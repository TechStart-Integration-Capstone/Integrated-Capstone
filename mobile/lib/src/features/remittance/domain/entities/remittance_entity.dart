/// Business Entity for Philippine Remittance Transfers
class RemittanceEntity {
  final String referenceId;
  final double amount;
  final String sourceAccount;
  final String recipientAccount;
  final String status;
  final String auditHash;

  const RemittanceEntity({
    required this.referenceId,
    required this.amount,
    required this.sourceAccount,
    required this.recipientAccount,
    required this.status,
    required this.auditHash,
  });
}
