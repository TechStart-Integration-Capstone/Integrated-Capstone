import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// User authentication payloads and JWT session keys stored inside
/// native device storage (iOS Keychain / Android KeyStore).
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

  static Future<void> saveToken(String token) async {
    await _storage.write(key: _tokenKey, value: token);
  }

  static Future<String?> getToken() async {
    return await _storage.read(key: _tokenKey);
  }

  static Future<void> saveUserSession({
    required String username,
    required String fullName,
    required int customerId,
  }) async {
    await _storage.write(key: _usernameKey, value: username);
    await _storage.write(key: _fullNameKey, value: fullName);
    await _storage.write(key: _customerIdKey, value: customerId.toString());
  }

  static Future<String?> getUsername() async {
    return await _storage.read(key: _usernameKey);
  }

  static Future<String?> getFullName() async {
    return await _storage.read(key: _fullNameKey);
  }

  static Future<int?> getCustomerId() async {
    final str = await _storage.read(key: _customerIdKey);
    return str != null ? int.tryParse(str) : null;
  }

  static Future<void> cacheBalance(double balance) async {
    await _storage.write(key: _cachedBalanceKey, value: balance.toString());
  }

  static Future<double> getCachedBalance() async {
    final str = await _storage.read(key: _cachedBalanceKey);
    return double.tryParse(str ?? '0.00') ?? 0.00;
  }

  static const String _pinHashKey = 'paypink_mpin_hash';
  static const String _pinOwnerKey = 'paypink_mpin_owner';

  /// Hashes and securely persists user MPIN, remembering which user it belongs to.
  static Future<void> savePin(String pin, {String? owner}) async {
    final bytes = utf8.encode('paypink_salt_${pin.trim()}');
    final digest = sha256.convert(bytes);
    await _storage.write(key: _pinHashKey, value: digest.toString());
    final given = owner?.trim() ?? '';
    final resolvedOwner = (given.isNotEmpty ? given : (await getUsername() ?? '')).trim().toLowerCase();
    if (resolvedOwner.isNotEmpty) {
      await _storage.write(key: _pinOwnerKey, value: resolvedOwner);
    }
  }

  /// True when this device already holds an MPIN for [username].
  /// A different user signing in on the same device must create their own.
  static Future<bool> hasPinFor(String username) async {
    if (!await hasPin()) return false;
    final owner = await _storage.read(key: _pinOwnerKey);
    final user = username.trim().toLowerCase();
    if (owner == null || owner.isEmpty) {
      // MPIN saved before owners were recorded: adopt it for this user.
      if (user.isNotEmpty) await _storage.write(key: _pinOwnerKey, value: user);
      return true;
    }
    return owner == user;
  }

  /// Verifies input PIN against securely persisted SHA-256 hash
  static Future<bool> verifyPin(String pin) async {
    final storedHash = await _storage.read(key: _pinHashKey);
    if (storedHash == null) return false;
    final bytes = utf8.encode('paypink_salt_${pin.trim()}');
    final digest = sha256.convert(bytes);
    return storedHash == digest.toString();
  }

  /// Checks if the user has already configured an MPIN
  static Future<bool> hasPin() async {
    final storedHash = await _storage.read(key: _pinHashKey);
    return storedHash != null && storedHash.isNotEmpty;
  }

  /// Clears user MPIN
  static Future<void> clearPin() async {
    await _storage.delete(key: _pinHashKey);
  }

  /// Ends the signed-in session (token, customer ID, cached balance) but keeps the
  /// MPIN and the remembered user, so the MPIN only has to be created once per device.
  static Future<void> clearSession() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _customerIdKey);
    await _storage.delete(key: _cachedBalanceKey);
  }

  /// Wipes everything on the device, including the MPIN.
  static Future<void> clearVault() async {
    await _storage.deleteAll();
  }
}

