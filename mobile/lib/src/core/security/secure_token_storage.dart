import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../services/web_storage_stub.dart' if (dart.library.html) '../../../services/web_storage_web.dart';

/// Hardware-backed secure storage wrapper using Android KeyStore & iOS Keychain, with web fallback.
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

  static final Map<String, String> _mem = {};

  /// Save JWT Access Token
  static Future<void> saveToken(String token) async {
    _mem[_keyToken] = token;
    if (kIsWeb) {
      saveWebLocal(_keyToken, token);
      return;
    }
    try {
      await _storage.write(key: _keyToken, value: token);
    } catch (_) {}
  }

  /// Retrieve JWT Access Token
  static Future<String?> getToken() async {
    if (kIsWeb) {
      final webVal = getWebLocal(_keyToken);
      if (webVal != null && webVal.isNotEmpty) return webVal;
      return _mem[_keyToken];
    }
    try {
      return await _storage.read(key: _keyToken);
    } catch (_) {
      return _mem[_keyToken];
    }
  }

  /// Save current username
  static Future<void> saveUser(String username) async {
    _mem[_keyUser] = username;
    if (kIsWeb) {
      saveWebLocal(_keyUser, username);
      return;
    }
    try {
      await _storage.write(key: _keyUser, value: username);
    } catch (_) {}
  }

  /// Retrieve current username
  static Future<String?> getUser() async {
    if (kIsWeb) {
      final webVal = getWebLocal(_keyUser);
      if (webVal != null && webVal.isNotEmpty) return webVal;
      return _mem[_keyUser];
    }
    try {
      return await _storage.read(key: _keyUser);
    } catch (_) {
      return _mem[_keyUser];
    }
  }

  /// Save cached balance for offline/circuit breaker state
  static Future<void> saveCachedBalance(double balance) async {
    _mem[_keyCachedBalance] = balance.toString();
    if (kIsWeb) {
      saveWebLocal(_keyCachedBalance, balance.toString());
      return;
    }
    try {
      await _storage.write(key: _keyCachedBalance, value: balance.toString());
    } catch (_) {}
  }

  /// Retrieve cached balance
  static Future<double> getCachedBalance() async {
    String? val;
    if (kIsWeb) {
      val = getWebLocal(_keyCachedBalance) ?? _mem[_keyCachedBalance];
    } else {
      try {
        val = await _storage.read(key: _keyCachedBalance);
      } catch (_) {
        val = _mem[_keyCachedBalance];
      }
    }
    if (val != null) {
      return double.tryParse(val) ?? 50.00;
    }
    return 50.00;
  }

  /// Delete session token on logout
  static Future<void> clearSession() async {
    _mem.clear();
    if (kIsWeb) {
      removeWebLocal(_keyToken);
      removeWebLocal(_keyUser);
      return;
    }
    try {
      await _storage.delete(key: _keyToken);
      await _storage.delete(key: _keyUser);
    } catch (_) {}
  }
}
