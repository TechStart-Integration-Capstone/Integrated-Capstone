import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Hardware-backed secure storage wrapper using Android KeyStore & iOS Keychain.
class SecureTokenStorage {
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock,
    ),
  );

  static const String _keyToken = 'paypink_auth_token';
  static const String _keyUser = 'paypink_current_username';
  static const String _keyCachedBalance = 'paypink_cached_balance';

  /// Save JWT Access Token
  static Future<void> saveToken(String token) async {
    await _storage.write(key: _keyToken, value: token);
  }

  /// Retrieve JWT Access Token
  static Future<String?> getToken() async {
    return await _storage.read(key: _keyToken);
  }

  /// Save current username
  static Future<void> saveUser(String username) async {
    await _storage.write(key: _keyUser, value: username);
  }

  /// Retrieve current username
  static Future<String?> getUser() async {
    return await _storage.read(key: _keyUser);
  }

  /// Save cached balance for offline/circuit breaker state
  static Future<void> saveCachedBalance(double balance) async {
    await _storage.write(key: _keyCachedBalance, value: balance.toString());
  }

  /// Retrieve cached balance
  static Future<double> getCachedBalance() async {
    final val = await _storage.read(key: _keyCachedBalance);
    if (val != null) {
      return double.tryParse(val) ?? 50.00;
    }
    return 50.00;
  }

  /// Delete session token on logout
  static Future<void> clearSession() async {
    await _storage.delete(key: _keyToken);
    await _storage.delete(key: _keyUser);
  }
}
