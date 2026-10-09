import 'dart:convert';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:encrypt/encrypt.dart' as encrypt_pkg;

/// AES-256 End-to-End Payload Encryption & Hashing Helper
class CryptoInterceptor {
  // 32-byte secret key seed for AES-256 payload encryption
  static const String _secretSeed = "PayPink_Mobile_Enterprise_Secret_Key_2026";

  static encrypt_pkg.Key _getKey() {
    final bytes = utf8.encode(_secretSeed);
    final digest = sha256.convert(bytes);
    return encrypt_pkg.Key(Uint8List.fromList(digest.bytes));
  }

  /// Encrypt sensitive Map payload to AES-256 Base64 string
  static String encryptPayload(Map<String, dynamic> payload) {
    final key = _getKey();
    final iv = encrypt_pkg.IV.fromLength(16);
    final encrypter = encrypt_pkg.Encrypter(encrypt_pkg.AES(key));

    final jsonString = jsonEncode(payload);
    final encrypted = encrypter.encrypt(jsonString, iv: iv);
    return "${iv.base64}:${encrypted.base64}";
  }

  /// Decrypt Base64 AES-256 encrypted payload back to Map
  static Map<String, dynamic> decryptPayload(String encryptedText) {
    try {
      final parts = encryptedText.split(':');
      if (parts.length != 2) return jsonDecode(encryptedText);

      final key = _getKey();
      final iv = encrypt_pkg.IV.fromBase64(parts[0]);
      final encrypter = encrypt_pkg.Encrypter(encrypt_pkg.AES(key));

      final decrypted = encrypter.decrypt64(parts[1], iv: iv);
      return jsonDecode(decrypted);
    } catch (_) {
      return {};
    }
  }

  /// Generate SHA-256 Audit Hash
  static String generateAuditHash(String payload) {
    final bytes = utf8.encode(payload);
    return sha256.convert(bytes).toString();
  }
}
