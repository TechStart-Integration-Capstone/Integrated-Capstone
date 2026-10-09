/// Pure Business Entity for Transaction History Ledger
class TransactionEntity {
  final String id;
  final String title;
  final String date;
  final String account;
  final double amount;
  final bool isCredit;
  final String ofscore;
  final String status;

  const TransactionEntity({
    required this.id,
    required this.title,
    required this.date,
    required this.account,
    required this.amount,
    required this.isCredit,
    required this.ofscore,
    this.status = 'COMPLETED',
  });
}
