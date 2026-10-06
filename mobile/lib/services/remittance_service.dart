import 'dart:convert';
import 'dart:math';
import 'package:http/http.dart' as http;
import 'api_config.dart';
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
    if (forceFraud || amount > 1000) {
      return 0.94; // Fraudulent / High Risk (> 0.85)
    } else if (amount > 100) {
      return 0.42;
    }
    return 0.12; // Low Risk
  }

  /// Executes remittance pipeline over API Gateway to Azure SQL with Circuit Breaker fallback
  static Future<RemittanceResult> submitRemittance({
    required String sourceAccountId,
    required String destinationAccountNumber,
    required double amount,
    bool forceFraud = false,
  }) async {
    final riskScore = evaluateRisk(amount, forceFraud: forceFraud);
    final circuitBreaker = CircuitBreakerClient();

    // Drop immediately if score exceeds 0.85
    if (riskScore > riskThreshold) {
      return RemittanceResult(
        success: false,
        message: 'Remittance dropped: Risk evaluation ($riskScore) exceeds SLA security threshold (0.85).',
        riskScore: riskScore,
      );
    }

    final debitAcct = sourceAccountId.contains('5046') ? '5046' : '8504';
    final creditAcct = destinationAccountNumber.replaceAll(' ', '');
    final idempotencyKey = 'REQ-${DateTime.now().millisecondsSinceEpoch}-${Random().nextInt(1000)}';
    final correlationId = 'CORR-${DateTime.now().millisecondsSinceEpoch}';

    // If Circuit Breaker is OPEN, perform client-side fail-fast fallback
    if (circuitBreaker.isOpen) {
      return _executeFallbackSimulation(
        debitAcct: debitAcct,
        creditAcct: creditAcct,
        amount: amount,
        riskScore: riskScore,
        reason: 'Client Circuit Breaker is OPEN. Executing local offline transfer.',
      );
    }

    try {
      final token = await SecureTokenStorage.getToken() ?? 'mock_jwt_token';
      final url = Uri.parse('${ApiConfig.baseUrl}/remittance/transfer');

      final response = await http.post(
        url,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $token',
          'Idempotency-Key': idempotencyKey,
          'X-Correlation-ID': correlationId,
          'X-Auth-Customer-Id': '1', // Default caller customer ID
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
        final refId = body['transactionId'] ?? body['referenceId'] ?? 'TRX-AZSQL-${Random().nextInt(900) + 100}';
        final returnedRisk = (body['riskScore'] != null) ? (body['riskScore'] as num).toDouble() : riskScore;
        final returnedOfs = body['ofsString'] ?? generateOFSCoreString(debitAccount: debitAcct, creditAccount: creditAcct, amount: amount);

        return RemittanceResult(
          success: true,
          message: 'Remittance processed via API Gateway & committed to Azure SQL.',
          referenceId: refId.toString(),
          ofscore: returnedOfs,
          riskScore: returnedRisk,
          isOfflineFallback: false,
        );
      } else {
        // HTTP Error from Backend / Gateway
        circuitBreaker.recordFailure();
        final errorMsg = _extractErrorMessage(response.body);
        return RemittanceResult(
          success: false,
          message: 'Gateway Error (${response.statusCode}): $errorMsg',
          riskScore: riskScore,
        );
      }
    } catch (e) {
      // Network timeout / connection refused -> Trip breaker & execute graceful fallback simulation
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

    final ref = 'TRX-OFFLINE-${Random().nextInt(900) + 100}';

    return RemittanceResult(
      success: true,
      message: 'Remittance processed offline ($reason).',
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
