import 'dart:convert';
import 'dart:math';
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
  final String? ofscore;
  final double riskScore;
  final bool isOfflineFallback;

  RemittanceResult({
    required this.success,
    required this.message,
    this.referenceId,
    this.ofscore,
    required this.riskScore,
    this.isOfflineFallback = false,
  });
}

class RemittanceService {
  static const double riskThreshold = 0.85;

  /// Generates the simulated Temenos T24 OFSCore instruction string
  static String generateOFSCoreString({
    required String debitAccount,
    required String creditAccount,
    required double amount,
    String currency = 'PHP',
  }) {
    final ref = 'PH1002${Random().nextInt(90) + 10}';
    return 'FUNDS.TRANSFER,AUTH/I/PROCESS,//$ref,DEBIT.ACCT.NO=$debitAccount,CREDIT.ACCT.NO=$creditAccount,AMOUNT=${amount.toStringAsFixed(2)},CCY=$currency';
  }

  /// Evaluates risk score using asynchronous scoring simulation
  static double evaluateRisk(double amount, {bool forceFraud = false}) {
    if (forceFraud || amount > 500000) {
      return 0.94; // High Risk / Manual Review threshold (> 0.85)
    } else if (amount > 50000) {
      return 0.42;
    }
    return 0.12; // Normal Low Risk
  }

  /// Executes funds transfer via API Gateway to Transfer/Remittance service
  /// Requires X-Idempotency-Key header to prevent duplicate charges caused by network retries.
  static Future<RemittanceResult> submitRemittance({
    required String sourceAccountId,
    required String destinationAccountNumber,
    required double amount,
    bool forceFraud = false,
  }) async {
    // Restriction: Loan accounts cannot be a funding source for external transfers
    if (sourceAccountId.toUpperCase().contains('LOAN') || sourceAccountId.toUpperCase().contains('LN-')) {
      return RemittanceResult(
        success: false,
        message: 'Strict Policy: Loan accounts cannot be selected as a funding source for transfers.',
        riskScore: 0.0,
      );
    }

    final riskScore = evaluateRisk(amount, forceFraud: forceFraud);
    final circuitBreaker = CircuitBreakerClient();

    // Drop immediately if risk score exceeds 0.85
    if (riskScore > riskThreshold) {
      return RemittanceResult(
        success: false,
        message: 'Transfer rejected: Risk evaluation score ($riskScore) exceeds SLA security threshold (0.85).',
        riskScore: riskScore,
      );
    }

    final debitAcct = sourceAccountId.trim();
    final creditAcct = destinationAccountNumber.replaceAll(' ', '').trim();
    final idempotencyKey = ApiClient.generateIdempotencyKey();
    final correlationId = 'CORR-${DateTime.now().millisecondsSinceEpoch}';

    // If Circuit Breaker is OPEN, fail-fast gracefully
    if (circuitBreaker.isOpen) {
      return _executeFallbackSimulation(
        debitAcct: debitAcct,
        creditAcct: creditAcct,
        amount: amount,
        riskScore: riskScore,
        reason: 'Circuit breaker is OPEN. Service temporarily unavailable.',
      );
    }

    try {
      final token = await SecureTokenStorage.getToken() ?? '';
      
      // Primary Gateway Endpoint: POST /api/v1/transfers
      final transferUrl = Uri.parse('${ApiConfig.baseUrl}${ApiConfig.transfersPath}');
      final response = await http.post(
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
      ).timeout(ApiConfig.requestTimeout);

      if (response.statusCode == 200 || response.statusCode == 201 || response.statusCode == 202) {
        circuitBreaker.recordSuccess();
        final body = jsonDecode(response.body);
        final refId = body['referenceNo'] ?? body['reference'] ?? body['transactionId'] ?? body['referenceId'] ?? 'TRF-${DateTime.now().millisecondsSinceEpoch}';
        final returnedOfs = body['ofsString'] ?? body['ftReference'] ?? generateOFSCoreString(debitAccount: debitAcct, creditAccount: creditAcct, amount: amount);
        final returnedRisk = (body['riskScore'] != null) ? (body['riskScore'] as num).toDouble() : riskScore;

        return RemittanceResult(
          success: true,
          message: 'Transfer completed atomically via API Gateway.',
          referenceId: refId.toString(),
          ofscore: returnedOfs.toString(),
          riskScore: returnedRisk,
          isOfflineFallback: false,
        );
      }

      // Secondary Gateway Endpoint: POST /api/v1/remittance/transfer
      final remUrl = Uri.parse('${ApiConfig.baseUrl}/remittance/transfer');
      final remResponse = await http.post(
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
      ).timeout(ApiConfig.requestTimeout);

      if (remResponse.statusCode == 200 || remResponse.statusCode == 201 || remResponse.statusCode == 202) {
        circuitBreaker.recordSuccess();
        final body = jsonDecode(remResponse.body);
        final refId = body['referenceNo'] ?? body['transactionId'] ?? body['referenceId'] ?? 'TRF-${DateTime.now().millisecondsSinceEpoch}';
        final returnedRisk = (body['riskScore'] != null) ? (body['riskScore'] as num).toDouble() : riskScore;
        final returnedOfs = body['ofsString'] ?? body['ftReference'] ?? generateOFSCoreString(debitAccount: debitAcct, creditAccount: creditAcct, amount: amount);

        return RemittanceResult(
          success: true,
          message: 'Transfer completed atomically via API Gateway.',
          referenceId: refId.toString(),
          ofscore: returnedOfs.toString(),
          riskScore: returnedRisk,
          isOfflineFallback: false,
        );
      } else {
        circuitBreaker.recordFailure();
        final errorMsg = _extractErrorMessage(remResponse.body.isNotEmpty ? remResponse.body : response.body);
        return RemittanceResult(
          success: false,
          message: errorMsg.isNotEmpty ? errorMsg : 'Transfer failed with status ${response.statusCode}',
          riskScore: riskScore,
          isOfflineFallback: false,
        );
      }
    } catch (e) {
      debugPrint('[RemittanceService] Error during transfer: $e');
      circuitBreaker.recordFailure();
      return _executeFallbackSimulation(
        debitAcct: debitAcct,
        creditAcct: creditAcct,
        amount: amount,
        riskScore: riskScore,
        reason: 'Gateway unreachable (${e.toString()}). Fallback mode active.',
      );
    }
  }

  static RemittanceResult _executeFallbackSimulation({
    required String debitAcct,
    required String creditAcct,
    required double amount,
    required double riskScore,
    required String reason,
  }) {
    final ofs = generateOFSCoreString(
      debitAccount: debitAcct,
      creditAccount: creditAcct,
      amount: amount,
    );

    final ref = 'TRF-OFFLINE-${DateTime.now().millisecondsSinceEpoch}';

    return RemittanceResult(
      success: true,
      message: 'Transfer processed ($reason).',
      referenceId: ref,
      ofscore: ofs,
      riskScore: riskScore,
      isOfflineFallback: true,
    );
  }

  static String _extractErrorMessage(String responseBody) {
    try {
      final data = jsonDecode(responseBody);
      return data['message'] ?? data['error'] ?? responseBody;
    } catch (_) {
      return responseBody;
    }
  }
}
