import '../entities/bank_account_entity.dart';

abstract class AccountRepository {
  Future<List<BankAccountEntity>> fetchAccounts();
}
