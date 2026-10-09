/// Pure Business Entity for Bank Accounts
class BankAccountEntity {
  final int accountId;
  final String accountNumber;
  final String displayName;
  final double currentBalance;
  final String accountType;
  final String status;

  const BankAccountEntity({
    required this.accountId,
    required this.accountNumber,
    required this.displayName,
    required this.currentBalance,
    required this.accountType,
    required this.status,
  });

  String get maskedNumber => accountNumber.length >= 4
      ? '•••• •••• ${accountNumber.substring(accountNumber.length - 4)}'
      : accountNumber;
}
