import 'package:paypink_mobile/services/remittance_service.dart';
import 'package:paypink_mobile/src/core/network/crypto_interceptor.dart';
import 'package:paypink_mobile/src/core/network/dio_client.dart';
import 'package:paypink_mobile/src/features/remittance/domain/entities/remittance_entity.dart';
import 'package:paypink_mobile/src/features/remittance/domain/repositories/remittance_repository.dart';

class RemittanceRepositoryImpl implements RemittanceRepository {
  final DioClient dioClient;

  RemittanceRepositoryImpl({required this.dioClient});

  @override
  Future<RemittanceEntity> executeRemittance({
    required String sourceAccount,
    required String recipientAccount,
    required double amount,
    required String channel,
  }) async {
    final res = await RemittanceService.submitRemittance(
      sourceAccountId: sourceAccount,
      destinationAccountNumber: recipientAccount,
      amount: amount,
    );

    if (res.success) {
      final refId = res.referenceId ?? 'TRX-${DateTime.now().millisecondsSinceEpoch}';
      final auditHash = CryptoInterceptor.generateAuditHash('$refId-$amount-$sourceAccount');

      return RemittanceEntity(
        referenceId: refId,
        amount: amount,
        sourceAccount: sourceAccount,
        recipientAccount: recipientAccount,
        status: 'COMPLETED',
        auditHash: auditHash,
      );
    } else {
      throw Exception(res.message);
    }
  }
}
