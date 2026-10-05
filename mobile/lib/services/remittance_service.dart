import 'dart:math';

class RemittanceResult {
  final bool success;
  final String message;
  final String? referenceId;
  final String? ofscore;
  final double riskScore;

  RemittanceResult({
    required this.success,
    required this.message,
    this.referenceId,
    this.ofscore,
    required this.riskScore,
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

  /// Executes remittance pipeline
  static Future<RemittanceResult> submitRemittance({
    required String sourceAccountId,
    required String destinationAccountNumber,
    required double amount,
    bool forceFraud = false,
  }) async {
    final riskScore = evaluateRisk(amount, forceFraud: forceFraud);

    // Drop immediately if score exceeds 0.85
    if (riskScore > riskThreshold) {
      return RemittanceResult(
        success: false,
        message: 'Remittance dropped: Risk evaluation ($riskScore) exceeds SLA security threshold (0.85).',
        riskScore: riskScore,
      );
    }

    final ofs = generateOFSCoreString(
      debitAccount: sourceAccountId.contains('5046') ? '5046' : '8504',
      creditAccount: destinationAccountNumber.replaceAll(' ', ''),
      amount: amount,
    );

    final ref = 'TRX-20261002-${Random().nextInt(900) + 100}';

    return RemittanceResult(
      success: true,
      message: 'Remittance cleared risk analytics and committed to Core Ledger.',
      referenceId: ref,
      ofscore: ofs,
      riskScore: riskScore,
    );
  }
}
