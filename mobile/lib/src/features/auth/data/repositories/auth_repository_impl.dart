import 'package:paypink_mobile/services/auth_service.dart';
import 'package:paypink_mobile/src/core/network/dio_client.dart';
import 'package:paypink_mobile/src/core/security/secure_token_storage.dart';
import 'package:paypink_mobile/src/features/auth/domain/entities/user_entity.dart';
import 'package:paypink_mobile/src/features/auth/domain/repositories/auth_repository.dart';

/// Concrete Auth Repository Implementation
class AuthRepositoryImpl implements AuthRepository {
  final DioClient dioClient;

  AuthRepositoryImpl({required this.dioClient});

  @override
  Future<UserEntity> login(String username, String password) async {
    final res = await AuthService.login(username: username, password: password);
    if (res.success) {
      await SecureTokenStorage.saveUser(username);
      return UserEntity(username: username, email: '$username@paypink.com');
    } else {
      throw Exception(res.message);
    }
  }

  @override
  Future<void> logout() async {
    await AuthService.logout();
    await SecureTokenStorage.clearSession();
  }
}
