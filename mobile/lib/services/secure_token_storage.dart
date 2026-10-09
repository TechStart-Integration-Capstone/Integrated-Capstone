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

  /// Small non-secret app preferences (e.g. notification read state), stored per user.
  static Future<String?> readValue(String key) => _read(key);
  static Future<void> writeValue(String key, String value) => _write(key, value);

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

  static const String _pinOwnerKey = 'paypink_mpin_owner';

  /// Hashes and securely persists user MPIN, remembering which user it belongs to.
  static Future<void> savePin(String pin, {String? owner}) async {
    final bytes = utf8.encode('paypink_salt_${pin.trim()}');
    final digest = sha256.convert(bytes);
    await _write(_pinHashKey, digest.toString());
    final given = owner?.trim() ?? '';
    final resolvedOwner = (given.isNotEmpty ? given : (await getUsername() ?? '')).trim().toLowerCase();
    if (resolvedOwner.isNotEmpty) {
      await _write(_pinOwnerKey, resolvedOwner);
    }
  }

  /// True when this device already holds an MPIN for [username].
  /// A different user signing in on the same device must create their own.
  static Future<bool> hasPinFor(String username) async {
    if (!await hasPin()) return false;
    final owner = await _read(_pinOwnerKey);
    final user = username.trim().toLowerCase();
    if (owner == null || owner.isEmpty) {
      // MPIN saved before owners were recorded: adopt it for this user.
      if (user.isNotEmpty) await _write(_pinOwnerKey, user);
      return true;
    }
    return owner == user;
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

  /// Ends the signed-in session (token, customer ID, cached balance) but keeps the
  /// MPIN and the remembered user, so the MPIN only has to be created once per device.
  static Future<void> clearSession() async {
    await _delete(_tokenKey);
    await _delete(_customerIdKey);
    await _delete(_cachedBalanceKey);
  }

  static const String _favoritesCacheKey = 'paypink_favorites_cache';

  /// Saves cached favorites list for instant display across app lifecycle.
  static Future<void> saveFavoritesCache(List<Map<String, String>> favorites) async {
    try {
      final user = (await getUsername()) ?? 'default';
      await writeValue('${_favoritesCacheKey}_$user', jsonEncode(favorites));
    } catch (_) {}
  }

  /// Retrieves cached favorites list.
  static Future<List<Map<String, String>>> getFavoritesCache() async {
    try {
      final user = (await getUsername()) ?? 'default';
      final raw = await readValue('${_favoritesCacheKey}_$user');
      if (raw != null && raw.isNotEmpty) {
        final decoded = jsonDecode(raw);
        if (decoded is List) {
          final List<Map<String, String>> list = [];
          for (final item in decoded) {
            if (item is Map) {
              list.add(Map<String, String>.from(
                item.map((k, v) => MapEntry(k.toString(), v.toString())),
              ));
            }
          }
          return list;
        }
      }
    } catch (_) {}
    return [];
  }

  /// Wipes everything on the device, including the MPIN.
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
