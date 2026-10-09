import '../entities/remittance_entity.dart';

abstract class RemittanceRepository {
  Future<RemittanceEntity> executeRemittance({
    required String sourceAccount,
    required String recipientAccount,
    required double amount,
    required String channel,
  });
}
