import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'api_config.dart';
import 'api_client.dart';
import 'secure_token_storage.dart';

class AuthResult {
  final bool success;
  final String message;
  final String? token;
  final String? username;
  final String? fullName;
  final int? customerId;
  final int? statusCode;

  AuthResult({
    required this.success,
    required this.message,
    this.token,
    this.username,
    this.fullName,
    this.customerId,
    this.statusCode,
  });
}

class AuthService {
  static final ApiClient _api = ApiClient();

  /// Authenticates user credentials strictly against the database via API Gateway.
  /// Returns HTTP 401 with generic "Invalid username or password" on bad credentials or non-existent user.
  static Future<AuthResult> login({
    required String username,
    required String password,
  }) async {
    final cleanUsername = username.trim();
    final cleanPassword = password.trim();

    if (cleanUsername.isEmpty || cleanPassword.isEmpty) {
      return AuthResult(
        success: false,
        message: 'Invalid username or password.',
        statusCode: 400,
      );
    }

    try {
      // 1. Primary endpoint: POST /api/v1/auth/login via API Gateway
      final response = await _api.post(
        ApiConfig.loginPath,
        body: {
          'username': cleanUsername,
          'password': cleanPassword,
        },
        requiresAuth: false,
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        final data = jsonDecode(response.body);
        final token = data['token'] as String?;
        if (token == null || token.isEmpty) {
          return AuthResult(
            success: false,
            message: 'Invalid authentication response from server.',
            statusCode: 500,
          );
        }

        final fullName = data['fullName'] as String? ?? cleanUsername;
        final customerId = data['customerId'] is int
            ? data['customerId'] as int
            : int.tryParse(data['customerId']?.toString() ?? '1') ?? 1;

        // Persist token in secure device storage (KeyStore / Keychain)
        await SecureTokenStorage.saveToken(token);
        await SecureTokenStorage.saveUserSession(
          username: cleanUsername,
          fullName: fullName,
          customerId: customerId,
        );

        ApiClient.resetUnauthorized();

        return AuthResult(
          success: true,
          message: 'Welcome back, $fullName!',
          token: token,
          username: cleanUsername,
          fullName: fullName,
          customerId: customerId,
          statusCode: 200,
        );
      } else if (response.statusCode == 401) {
        // Strict specification requirement: generic message
        return AuthResult(
          success: false,
          message: 'Invalid username or password.',
          statusCode: 401,
        );
      } else if (response.statusCode == 429) {
        return AuthResult(
          success: false,
          message: 'Too many login attempts. Please wait a moment and try again.',
          statusCode: 429,
        );
      } else {
        // Try fallback banking path /api/v1/auth/banking/login
        try {
          final fallbackResp = await _api.post(
            '/auth/banking/login',
            body: {
              'username': cleanUsername,
              'password': cleanPassword,
            },
            requiresAuth: false,
          );

          if (fallbackResp.statusCode == 200 || fallbackResp.statusCode == 201) {
            final data = jsonDecode(fallbackResp.body);
            final token = data['token'] as String?;
            if (token != null && token.isNotEmpty) {
              final fullName = data['fullName'] as String? ?? cleanUsername;
              final customerId = data['customerId'] is int
                  ? data['customerId'] as int
                  : int.tryParse(data['customerId']?.toString() ?? '1') ?? 1;

              await SecureTokenStorage.saveToken(token);
              await SecureTokenStorage.saveUserSession(
                username: cleanUsername,
                fullName: fullName,
                customerId: customerId,
              );
              ApiClient.resetUnauthorized();

              return AuthResult(
                success: true,
                message: 'Welcome back, $fullName!',
                token: token,
                username: cleanUsername,
                fullName: fullName,
                customerId: customerId,
                statusCode: 200,
              );
            }
          } else if (fallbackResp.statusCode == 401) {
            return AuthResult(
              success: false,
              message: 'Invalid username or password.',
              statusCode: 401,
            );
          }
        } catch (_) {}

        final msg = _extractErrorMessage(response.body);
        return AuthResult(
          success: false,
          message: msg.isNotEmpty ? msg : 'Invalid username or password.',
          statusCode: response.statusCode,
        );
      }
    } catch (e) {
      debugPrint('[AuthService] Login error: $e');
      return AuthResult(
        success: false,
        message: 'Unable to connect to the banking server. Please check your network.',
        statusCode: 503,
      );
    }
  }

  /// Registers a new user via API Gateway
  static Future<AuthResult> register({
    required String username,
    required String password,
    required String email,
    required String fullName,
  }) async {
    final cleanUser = username.trim();
    final cleanPass = password.trim();
    final cleanEmail = email.trim();
    final cleanName = fullName.trim();

    if (cleanUser.isEmpty || cleanPass.isEmpty || cleanEmail.isEmpty) {
      return AuthResult(
        success: false,
        message: 'Please fill out all required registration fields.',
        statusCode: 400,
      );
    }

    try {
      final nameParts = cleanName.split(' ');
      final firstName = nameParts.isNotEmpty ? nameParts.first : cleanUser;
      final lastName = nameParts.length > 1 ? nameParts.sublist(1).join(' ') : 'User';

      final response = await _api.post(
        '/auth/banking/register',
        body: {
          'firstName': firstName,
          'lastName': lastName,
          'email': cleanEmail,
          'phone': '+639171234567',
          'username': cleanUser,
          'password': cleanPass,
        },
        requiresAuth: false,
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        final data = jsonDecode(response.body);
        final token = data['token'] as String? ?? 'jwt_';
        final customerId = data['customerId'] is int ? data['customerId'] as int : 1;

        await SecureTokenStorage.saveToken(token);
        await SecureTokenStorage.saveUserSession(
          username: cleanUser,
          fullName: cleanName,
          customerId: customerId,
        );

        return AuthResult(
          success: true,
          message: 'Account registered successfully!',
          token: token,
          username: cleanUser,
          fullName: cleanName,
          customerId: customerId,
          statusCode: 201,
        );
      } else {
        final msg = _extractErrorMessage(response.body);
        return AuthResult(
          success: false,
          message: msg.isNotEmpty ? msg : 'Registration failed (HTTP ).',
          statusCode: response.statusCode,
        );
      }
    } catch (e) {
      return AuthResult(
        success: false,
        message: 'Unable to connect to banking gateway.',
        statusCode: 503,
      );
    }
  }

  /// Clears user authentication session and tokens from secure device storage
  static Future<void> logout() async {
    await SecureTokenStorage.clearVault();
    ApiClient.resetUnauthorized();
  }

  /// Checks if device has a valid persistent JWT token
  static Future<bool> hasActiveSession() async {
    final token = await SecureTokenStorage.getToken();
    return token != null && token.isNotEmpty;
  }

  static String _extractErrorMessage(String responseBody) {
    try {
      final data = jsonDecode(responseBody);
      return data['message'] ?? data['error'] ?? '';
    } catch (_) {
      return '';
    }
  }
}

