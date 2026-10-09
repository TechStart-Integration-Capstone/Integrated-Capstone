import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'web_storage_stub.dart' if (dart.library.html) 'web_storage_web.dart';

/// User authentication payloads and JWT session keys stored inside
/// native device storage (iOS Keychain / Android KeyStore) or browser localStorage on Web.
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
  static const String _usernameKey = 'paypink_username';
  static const String _fullNameKey = 'paypink_fullname';
  static const String _customerIdKey = 'paypink_customer_id';
  static const String _pinHashKey = 'paypink_mpin_hash';

  // In-memory fallback if both storage layers are unavailable
  static final Map<String, String> _memoryFallback = {};

  static Future<void> _write(String key, String value) async {
    _memoryFallback[key] = value;
    if (kIsWeb) {
      saveWebLocal(key, value);
      return;
    }
    try {
      await _storage.write(key: key, value: value);
    } catch (_) {}
  }

  static Future<String?> _read(String key) async {
    if (kIsWeb) {
      final webVal = getWebLocal(key);
      if (webVal != null && webVal.isNotEmpty) return webVal;
      return _memoryFallback[key];
    }
    try {
      final val = await _storage.read(key: key);
      return val ?? _memoryFallback[key];
    } catch (_) {
      return _memoryFallback[key];
    }
  }

  static Future<void> _delete(String key) async {
    _memoryFallback.remove(key);
    if (kIsWeb) {
      removeWebLocal(key);
      return;
    }
    try {
      await _storage.delete(key: key);
    } catch (_) {}
  }

  static Future<void> saveToken(String token) async {
    await _write(_tokenKey, token);
  }

  static Future<String?> getToken() async {
    return await _read(_tokenKey);
  }

  static Future<void> saveUserSession({
    required String username,
    required String fullName,
    required int customerId,
  }) async {
    await _write(_usernameKey, username);
    await _write(_fullNameKey, fullName);
    await _write(_customerIdKey, customerId.toString());
  }

  static Future<String?> getUsername() async {
    return await _read(_usernameKey);
  }

  static Future<String?> getFullName() async {
    return await _read(_fullNameKey);
  }

  static Future<int?> getCustomerId() async {
    final str = await _read(_customerIdKey);
    return str != null ? int.tryParse(str) : null;
  }

  static Future<void> cacheBalance(double balance) async {
    await _write(_cachedBalanceKey, balance.toString());
  }

  static Future<double> getCachedBalance() async {
    final str = await _read(_cachedBalanceKey);
    return double.tryParse(str ?? '0.00') ?? 0.00;
  }

  /// Hashes and securely persists user MPIN
  static Future<void> savePin(String pin) async {
    final bytes = utf8.encode('paypink_salt_${pin.trim()}');
    final digest = sha256.convert(bytes);
    await _write(_pinHashKey, digest.toString());
  }

  /// Verifies input PIN against securely persisted SHA-256 hash
  static Future<bool> verifyPin(String pin) async {
    final storedHash = await _read(_pinHashKey);
    if (storedHash == null) return false;
    final bytes = utf8.encode('paypink_salt_${pin.trim()}');
    final digest = sha256.convert(bytes);
    return storedHash == digest.toString();
  }

  /// Checks if the user has already configured an MPIN
  static Future<bool> hasPin() async {
    final storedHash = await _read(_pinHashKey);
    return storedHash != null && storedHash.isNotEmpty;
  }

  /// Clears user MPIN
  static Future<void> clearPin() async {
    await _delete(_pinHashKey);
  }

  static Future<void> clearVault() async {
    _memoryFallback.clear();
    if (kIsWeb) {
      clearWebLocal();
      return;
    }
    try {
      await _storage.deleteAll();
    } catch (_) {}
  }
}
