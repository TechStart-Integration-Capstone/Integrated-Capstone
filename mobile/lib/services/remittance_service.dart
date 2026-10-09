import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'api_config.dart';
import 'api_client.dart';
import 'secure_token_storage.dart';
import 'circuit_breaker_client.dart';

class RemittanceResult {
  final bool success;
  final String message;
  final String? referenceId;
  /// 'COMPLETED', 'PROCESSING' (core banking still posting), 'PENDING' (PESONet batch), or 'FAILED'
  final String status;
  /// Bank transfers only: who and which bank the server resolved the account to.
  final String? recipientName;
  final String? bank;

  RemittanceResult({
    required this.success,
    required this.message,
    this.referenceId,
    this.status = 'COMPLETED',
    this.recipientName,
    this.bank,
  });
}

/// An account in the partner-bank directory (InstaPay/PESONet recipients).
class ExternalRecipient {
  final String bank;
  final String name;
  final String number;
  const ExternalRecipient({required this.bank, required this.name, required this.number});
}

class RemittanceService {
  static const double riskThreshold = 0.85;

  /// Executes funds transfer via API Gateway to Transfer/Remittance service.
  /// Requires X-Idempotency-Key header to prevent duplicate charges caused by network retries.
  static Future<RemittanceResult> submitRemittance({
    required String sourceAccountId,
    required String destinationAccountNumber,
    required double amount,
    bool forceFraud = false,
  }) async {
    // Restriction: Loan accounts cannot be a funding source for external transfers
    if (sourceAccountId.toUpperCase().contains('LOAN') ||
        sourceAccountId.toUpperCase().contains('LN-')) {
      return RemittanceResult(
        success: false,
        message:
            'Strict Policy: Loan accounts cannot be selected as a funding source for transfers.',
      );
    }

    final circuitBreaker = CircuitBreakerClient();

    // If Circuit Breaker is OPEN, fail-fast with a clear error — no fake receipts.
    if (circuitBreaker.isOpen) {
      return RemittanceResult(
        success: false,
        message:
            'Banking service is temporarily unavailable. Please try again in a moment.',
      );
    }

    final debitAcct = sourceAccountId.trim();
    final creditAcct = destinationAccountNumber.replaceAll(' ', '').trim();
    final idempotencyKey = ApiClient.generateIdempotencyKey();
    final correlationId = 'CORR-${DateTime.now().millisecondsSinceEpoch}';

    try {
      final token = await SecureTokenStorage.getToken() ?? '';

      // Primary Gateway Endpoint: POST /api/v1/transfers
      final transferUrl =
          Uri.parse('${ApiConfig.baseUrl}${ApiConfig.transfersPath}');
      final response = await http
          .post(
            transferUrl,
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
              'Authorization': 'Bearer $token',
              'X-Idempotency-Key': idempotencyKey,
              'Idempotency-Key': idempotencyKey,
              'X-Correlation-ID': correlationId,
            },
            body: jsonEncode({
              'sourceAccountId': debitAcct,
              'targetAccountId': creditAcct,
              'amount': amount,
              'currency': 'PHP',
            }),
          )
          .timeout(ApiConfig.requestTimeout);

      if (response.statusCode == 200 ||
          response.statusCode == 201 ||
          response.statusCode == 202) {
        circuitBreaker.recordSuccess();
        final body = jsonDecode(response.body);
        final refId = body['referenceNo'] ??
            body['reference'] ??
            body['transactionId'] ??
            body['referenceId'] ??
            'TRF-${DateTime.now().millisecondsSinceEpoch}';
        final isProcessing = response.statusCode == 202;

        return RemittanceResult(
          success: true,
          message: isProcessing
              ? 'Core banking is still posting this transfer.'
              : 'Transfer completed atomically via API Gateway.',
          referenceId: refId.toString(),
          status: isProcessing ? 'PROCESSING' : 'COMPLETED',
        );
      }

      // If primary endpoint returned a definitive business error (e.g. 400, 403, 409, 422, 500), return error directly instead of sending duplicate requests
      if (response.statusCode != 404) {
        circuitBreaker.recordFailure();
        final errorMsg = _extractErrorMessage(response.body);
        return RemittanceResult(
          success: false,
          message: errorMsg.isNotEmpty
              ? errorMsg
              : 'Transfer failed with status ${response.statusCode}',
        );
      }

      // Secondary Gateway Endpoint: POST /api/v1/remittance/transfer (only if primary route 404s)
      final remUrl =
          Uri.parse('${ApiConfig.baseUrl}/remittance/transfer');
      final remResponse = await http
          .post(
            remUrl,
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $token',
              'X-Idempotency-Key': idempotencyKey,
              'Idempotency-Key': idempotencyKey,
              'X-Correlation-ID': correlationId,
            },
            body: jsonEncode({
              'sourceAccountId': debitAcct,
              'targetAccountId': creditAcct,
              'amount': amount,
              'currency': 'PHP',
            }),
          )
          .timeout(ApiConfig.requestTimeout);

      if (remResponse.statusCode == 200 ||
          remResponse.statusCode == 201 ||
          remResponse.statusCode == 202) {
        circuitBreaker.recordSuccess();
        final body = jsonDecode(remResponse.body);
        final refId = body['referenceNo'] ??
            body['transactionId'] ??
            body['referenceId'] ??
            'TRF-${DateTime.now().millisecondsSinceEpoch}';
        final isProcessing = remResponse.statusCode == 202;

        return RemittanceResult(
          success: true,
          message: isProcessing
              ? 'Core banking is still posting this transfer.'
              : 'Transfer completed atomically via API Gateway.',
          referenceId: refId.toString(),
          status: isProcessing ? 'PROCESSING' : 'COMPLETED',
        );
      } else {
        circuitBreaker.recordFailure();
        final errorMsg = _extractErrorMessage(
            remResponse.body.isNotEmpty ? remResponse.body : response.body);
        return RemittanceResult(
          success: false,
          message: errorMsg.isNotEmpty
              ? errorMsg
              : 'Transfer failed with status ${response.statusCode}',
        );
      }
    } catch (e) {
      debugPrint('[RemittanceService] Error during transfer: $e');
      circuitBreaker.recordFailure();
      return RemittanceResult(
        success: false,
        message:
            'Unable to process transfer. Please check your internet connection and try again.',
      );
    }
  }

  static String _extractErrorMessage(String responseBody) {
    try {
      final data = jsonDecode(responseBody);
      return data['detail'] ?? data['message'] ?? data['error'] ?? responseBody;
    } catch (_) {
      return responseBody;
    }
  }

  /// Sends money to another bank over InstaPay (instant, up to ₱50,000) or PESONet
  /// (batch). Same request as the web app (frontend/bank/external.js):
  /// POST /api/v1/auth/banking/external/transfers.
  /// [sourceAccountId] is the numeric account ID, not the account number.
  static Future<RemittanceResult> submitExternalTransfer({
    required int sourceAccountId,
    required String destinationAccountNumber,
    required double amount,
    required String rail,
  }) async {
    final normalizedRail = rail.toUpperCase();
    if (normalizedRail == 'INSTAPAY' && amount > 50000) {
      return RemittanceResult(
        success: false,
        message: 'InstaPay allows up to ₱50,000 per transfer. Choose PESONet for a larger amount.',
      );
    }
    final idempotencyKey = ApiClient.generateIdempotencyKey();
    try {
      final token = await SecureTokenStorage.getToken() ?? '';
      final response = await http
          .post(
            Uri.parse('${ApiConfig.baseUrl}/auth/banking/external/transfers'),
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
              'Authorization': 'Bearer $token',
            },
            body: jsonEncode({
              'sourceAccountId': sourceAccountId,
              'destinationAccountNumber': destinationAccountNumber.replaceAll(RegExp(r'\s'), ''),
              'amount': double.parse(amount.toStringAsFixed(2)),
              'rail': normalizedRail,
              'idempotencyKey': 'PAY-$idempotencyKey'.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), ''),
            }),
          )
          .timeout(ApiConfig.requestTimeout);

      if (response.statusCode == 200 || response.statusCode == 201) {
        final body = jsonDecode(response.body);
        final status = (body['status'] ?? 'COMPLETED').toString().toUpperCase();
        final pending = status == 'PENDING';
        return RemittanceResult(
          success: status != 'FAILED',
          message: pending
              ? 'PESONet transfer submitted. It will be credited in the next clearing batch.'
              : 'Sent via ${normalizedRail == 'PESONET' ? 'PESONet' : 'InstaPay'}.',
          referenceId: body['reference']?.toString(),
          status: pending ? 'PENDING' : status,
          recipientName: body['recipientName']?.toString(),
          bank: body['bank']?.toString(),
        );
      }
      final errorMsg = _extractErrorMessage(response.body);
      return RemittanceResult(
        success: false,
        message: errorMsg.isNotEmpty ? errorMsg : 'Transfer failed with status ${response.statusCode}',
        status: 'FAILED',
      );
    } catch (e) {
      debugPrint('[RemittanceService] external transfer error: $e');
      return RemittanceResult(
        success: false,
        message: 'Unable to process transfer. Please check your internet connection and try again.',
        status: 'FAILED',
      );
    }
  }

  /// Partner-bank recipient directory used by InstaPay/PESONet transfers.
  /// GET /api/v1/auth/banking/external/recipients
  static Future<List<ExternalRecipient>> fetchExternalRecipients() async {
    try {
      final token = await SecureTokenStorage.getToken() ?? '';
      final response = await http.get(
        Uri.parse('${ApiConfig.baseUrl}/auth/banking/external/recipients'),
        headers: {'Accept': 'application/json', 'Authorization': 'Bearer $token'},
      ).timeout(ApiConfig.requestTimeout);
      if (response.statusCode == 200) {
        final list = jsonDecode(response.body);
        if (list is List) {
          return list.whereType<Map>().map((r) => ExternalRecipient(
                bank: r['bank']?.toString() ?? '',
                name: r['name']?.toString() ?? '',
                number: r['number']?.toString() ?? '',
              )).toList();
        }
      }
    } catch (e) {
      debugPrint('[RemittanceService] external recipients error: $e');
    }
    return const [];
  }

  /// Polls the transfer status. Used by the periodic timer on the pending UI.
  /// GET /api/v1/remittance/{referenceNo}/status
  static Future<String> getStatus(String referenceNo) async {
    try {
      final token = await SecureTokenStorage.getToken() ?? '';
      final url = Uri.parse('${ApiConfig.baseUrl}/remittance/$referenceNo/status');
      final response = await http.get(
        url,
        headers: {
          'Accept': 'application/json',
          'Authorization': 'Bearer $token',
        },
      ).timeout(ApiConfig.requestTimeout);

      if (response.statusCode == 200) {
        final body = jsonDecode(response.body);
        return (body['status'] ?? 'PROCESSING').toString().toUpperCase();
      }
    } catch (e) {
      debugPrint('[RemittanceService] getStatus error: $e');
    }
    return 'PROCESSING'; // keep polling on transient errors
  }
}
