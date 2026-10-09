import 'package:paypink_mobile/services/account_service.dart';
import 'package:paypink_mobile/src/core/network/dio_client.dart';
import 'package:paypink_mobile/src/features/accounts/domain/entities/bank_account_entity.dart';
import 'package:paypink_mobile/src/features/accounts/domain/repositories/account_repository.dart';

class AccountRepositoryImpl implements AccountRepository {
  final DioClient dioClient;

  AccountRepositoryImpl({required this.dioClient});

  @override
  Future<List<BankAccountEntity>> fetchAccounts() async {
    final profile = await AccountService.fetchProfile();
    if (profile.accounts.isNotEmpty) {
      return profile.accounts
          .map((a) => BankAccountEntity(
                accountId: a.accountId,
                accountNumber: a.accountNumber,
                displayName: a.displayName,
                currentBalance: a.currentBalance,
                accountType: a.accountType,
                status: a.status,
              ))
          .toList();
    }
    return [];
  }
}
