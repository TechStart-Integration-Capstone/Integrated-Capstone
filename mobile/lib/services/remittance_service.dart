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
  /// 'COMPLETED', 'RESERVED', or 'FAILED'
  final String status;

  RemittanceResult({
    required this.success,
    required this.message,
    this.referenceId,
    this.status = 'COMPLETED',
  });
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
        final isReserved = response.statusCode == 202;

        return RemittanceResult(
          success: true,
          message: isReserved
              ? 'Transfer reserved. You can cancel or send now within the grace window.'
              : 'Transfer completed atomically via API Gateway.',
          referenceId: refId.toString(),
          status: isReserved ? 'RESERVED' : 'COMPLETED',
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
              'skipClientWindow': true,
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
        final isReserved = remResponse.statusCode == 202;

        return RemittanceResult(
          success: true,
          message: isReserved
              ? 'Transfer reserved. You can cancel or send now within the grace window.'
              : 'Transfer completed atomically via API Gateway.',
          referenceId: refId.toString(),
          status: isReserved ? 'RESERVED' : 'COMPLETED',
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

  /// Cancels a RESERVED transfer within its grace window.
  /// POST /api/v1/remittance/{referenceNo}/cancel
  static Future<RemittanceResult> cancel(String referenceNo) async {
    try {
      final token = await SecureTokenStorage.getToken() ?? '';
      final url = Uri.parse('${ApiConfig.baseUrl}/remittance/$referenceNo/cancel');
      final response = await http.post(
        url,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $token',
        },
      ).timeout(ApiConfig.requestTimeout);

      if (response.statusCode == 200) {
        return RemittanceResult(
          success: true,
          message: 'Transfer cancelled. Held funds have been released.',
          referenceId: referenceNo,
          status: 'CANCELLED',
        );
      }
      final errorMsg = _extractErrorMessage(response.body);
      return RemittanceResult(
        success: false,
        message: errorMsg.isNotEmpty ? errorMsg : 'Could not cancel transfer.',
        referenceId: referenceNo,
        status: 'FAILED',
      );
    } catch (e) {
      debugPrint('[RemittanceService] cancel error: $e');
      return RemittanceResult(
        success: false,
        message: 'Unable to cancel transfer. Please check your connection.',
        referenceId: referenceNo,
        status: 'FAILED',
      );
    }
  }

  /// Skips the remaining grace window and settles immediately.
  /// POST /api/v1/remittance/{referenceNo}/send-now
  static Future<RemittanceResult> sendNow(String referenceNo) async {
    try {
      final token = await SecureTokenStorage.getToken() ?? '';
      final url = Uri.parse('${ApiConfig.baseUrl}/remittance/$referenceNo/send-now');
      final response = await http.post(
        url,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $token',
        },
      ).timeout(ApiConfig.requestTimeout);

      if (response.statusCode == 200) {
        return RemittanceResult(
          success: true,
          message: 'Transfer sent immediately.',
          referenceId: referenceNo,
          status: 'COMPLETED',
        );
      }
      final errorMsg = _extractErrorMessage(response.body);
      return RemittanceResult(
        success: false,
        message: errorMsg.isNotEmpty ? errorMsg : 'Could not send transfer immediately.',
        referenceId: referenceNo,
        status: 'FAILED',
      );
    } catch (e) {
      debugPrint('[RemittanceService] sendNow error: $e');
      return RemittanceResult(
        success: false,
        message: 'Unable to send transfer. Please check your connection.',
        referenceId: referenceNo,
        status: 'FAILED',
      );
    }
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
        return (body['status'] ?? 'RESERVED').toString().toUpperCase();
      }
    } catch (e) {
      debugPrint('[RemittanceService] getStatus error: $e');
    }
    return 'RESERVED'; // keep polling on transient errors
  }
}
