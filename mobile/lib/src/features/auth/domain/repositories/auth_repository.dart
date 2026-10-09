import '../entities/user_entity.dart';

/// Abstract Auth Repository Contract
abstract class AuthRepository {
  Future<UserEntity> login(String username, String password);
  Future<void> logout();
}
