import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Capstone 2 Specification: Credential Hardening
/// User authentication payloads and JWT session keys are stored safely inside
/// native device storage chains using secure OS-level encryption layers
/// (iOS Keychain / Android KeyStore EncryptedSharedPreferences).
class SecureTokenStorage {
  static const FlutterSecureStorage _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
      resetOnError: true,
    ),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock,
    ),
  );

  static const String _tokenKey = 'paypink_jwt_token';
  static const String _cachedBalanceKey = 'paypink_cached_balance';

  static Future<void> saveToken(String token) async {
    await _storage.write(key: _tokenKey, value: token);
  }

  static Future<String?> getToken() async {
    return await _storage.read(key: _tokenKey);
  }

  static Future<void> cacheBalance(double balance) async {
    await _storage.write(key: _cachedBalanceKey, value: balance.toString());
  }

  static Future<double> getCachedBalance() async {
    final str = await _storage.read(key: _cachedBalanceKey);
    return double.tryParse(str ?? '50.00') ?? 50.00;
  }

  static Future<void> clearVault() async {
    await _storage.deleteAll();
  }
}
