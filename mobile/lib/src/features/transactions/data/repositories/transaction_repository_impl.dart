import 'package:paypink_mobile/services/account_service.dart';
import 'package:paypink_mobile/src/core/network/dio_client.dart';
import 'package:paypink_mobile/src/features/transactions/domain/entities/transaction_entity.dart';
import 'package:paypink_mobile/src/features/transactions/domain/repositories/transaction_repository.dart';

class TransactionRepositoryImpl implements TransactionRepository {
  final DioClient dioClient;

  TransactionRepositoryImpl({required this.dioClient});

  @override
  Future<List<TransactionEntity>> fetchTransactions() async {
    final list = await AccountService.fetchTransactions();
    return list
        .map((t) => TransactionEntity(
              id: t.id,
              title: t.title,
              date: t.date,
              account: t.account,
              amount: t.amount,
              isCredit: t.isCredit,
              ofscore: t.ofscore,
              status: t.status,
            ))
        .toList();
  }
}
