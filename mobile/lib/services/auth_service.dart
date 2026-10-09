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
  /// Uses POST /api/v1/auth/banking/login (verifies customer profile).
  /// Returns HTTP 401 with generic "Invalid username or password" on bad credentials or non-existent user.
  static Future<AuthResult> login({
    required String username,
    required String password,
  }) async {
    final cleanUsername = username.trim().toLowerCase();
    final cleanPassword = password; // Do not trim password

    if (cleanUsername.isEmpty || cleanPassword.isEmpty) {
      return AuthResult(
        success: false,
        message: 'Invalid username or password.',
        statusCode: 400,
      );
    }

    try {
      // 1. Primary endpoint: POST /api/v1/auth/banking/login via API Gateway
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

        final rawCustId = data['customerId'];
        final customerId = rawCustId is int
            ? rawCustId
            : int.tryParse(rawCustId?.toString() ?? '');

        if (customerId == null) {
          return AuthResult(
            success: false,
            message: 'Server did not return a valid customer profile.',
            statusCode: 500,
          );
        }

        final fullName = data['fullName'] as String? ?? cleanUsername;

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
      } else if (response.statusCode == 502 || response.statusCode == 503 || response.statusCode == 504) {
        return AuthResult(
          success: false,
          message: 'PayPink is starting up. Please wait a moment and try again.',
          statusCode: response.statusCode,
        );
      } else {
        // Fallback to /auth/login if /auth/banking/login returns 404 or unexpected code
        try {
          final fallbackResp = await _api.post(
            '/auth/login',
            body: {
              'username': cleanUsername,
              'password': cleanPassword,
            },
            requiresAuth: false,
          );

          if (fallbackResp.statusCode == 200 || fallbackResp.statusCode == 201) {
            final data = jsonDecode(fallbackResp.body);
            final token = data['token'] as String?;
            final rawCustId = data['customerId'];
            final customerId = rawCustId is int
                ? rawCustId
                : int.tryParse(rawCustId?.toString() ?? '');

            if (token != null && token.isNotEmpty && customerId != null) {
              final fullName = data['fullName'] as String? ?? cleanUsername;

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

        final msg = ApiClient.extractErrorMessage(response.statusCode, response.body);
        return AuthResult(
          success: false,
          message: msg.isNotEmpty ? msg : 'Invalid username or password.',
          statusCode: response.statusCode,
        );
      }
    } catch (e) {
      debugPrint('[AuthService] Login network failure: $e');
      return AuthResult(
        success: false,
        message: 'Unable to connect to PayPink Gateway at ${ApiConfig.baseUrl}. Please check your connection.',
        statusCode: 503,
      );
    }
  }

  /// Registers a new user via API Gateway
  static Future<AuthResult> register({
    required String firstName,
    required String lastName,
    required String email,
    required String phone,
    required String username,
    required String password,
  }) async {
    final cleanFirst = firstName.trim();
    final cleanLast = lastName.trim();
    final cleanEmail = email.trim();
    final cleanPhone = phone.trim();
    final cleanUser = username.trim().toLowerCase();
    final cleanPass = password; // Do not trim password

    if (cleanFirst.isEmpty || cleanLast.isEmpty || cleanEmail.isEmpty || cleanPhone.isEmpty || cleanUser.isEmpty || cleanPass.isEmpty) {
      return AuthResult(
        success: false,
        message: 'Please fill out all required registration fields.',
        statusCode: 400,
      );
    }

    if (cleanPass.length < 8) {
      return AuthResult(
        success: false,
        message: 'Password must be at least 8 characters long.',
        statusCode: 400,
      );
    }

    final userRegex = RegExp(r'^[a-zA-Z0-9_]{3,50}$');
    if (!userRegex.hasMatch(cleanUser)) {
      return AuthResult(
        success: false,
        message: 'Username must be 3–50 characters (letters, numbers, or underscores).',
        statusCode: 400,
      );
    }

    try {
      final response = await _api.post(
        '/auth/banking/register',
        body: {
          'firstName': cleanFirst,
          'lastName': cleanLast,
          'email': cleanEmail,
          'phone': cleanPhone,
          'username': cleanUser,
          'password': cleanPass,
        },
        requiresAuth: false,
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        final data = jsonDecode(response.body);
        final token = data['token'] as String?;
        if (token == null || token.isEmpty) {
          return AuthResult(
            success: false,
            message: 'Invalid registration response from server.',
            statusCode: 500,
          );
        }

        final rawCustId = data['customerId'];
        final customerId = rawCustId is int
            ? rawCustId
            : int.tryParse(rawCustId?.toString() ?? '');

        if (customerId == null) {
          return AuthResult(
            success: false,
            message: 'Server did not return a valid customer profile.',
            statusCode: 500,
          );
        }

        final fullName = data['fullName'] as String? ?? '$cleanFirst $cleanLast';

        await SecureTokenStorage.saveToken(token);
        await SecureTokenStorage.saveUserSession(
          username: cleanUser,
          fullName: fullName,
          customerId: customerId,
        );

        return AuthResult(
          success: true,
          message: 'Account registered successfully!',
          token: token,
          username: cleanUser,
          fullName: fullName,
          customerId: customerId,
          statusCode: 201,
        );
      } else if (response.statusCode == 502 || response.statusCode == 503 || response.statusCode == 504) {
        return AuthResult(
          success: false,
          message: 'PayPink is starting up. Please wait a moment and try again.',
          statusCode: response.statusCode,
        );
      } else {
        final msg = ApiClient.extractErrorMessage(response.statusCode, response.body);
        return AuthResult(
          success: false,
          message: msg.isNotEmpty ? msg : 'Registration failed (HTTP ${response.statusCode}).',
          statusCode: response.statusCode,
        );
      }
    } catch (e) {
      return AuthResult(
        success: false,
        message: 'PayPink is starting up. Please wait a moment and try again.',
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
}

