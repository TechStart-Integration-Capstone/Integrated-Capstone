import 'dart:convert';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'api_config.dart';
import 'secure_token_storage.dart';

/// HTTP Client Interceptor for PayPink Mobile
/// - Automatically injects "Authorization: Bearer <token>"
/// - Generates and attaches "X-Correlation-ID"
/// - Supports "X-Idempotency-Key" on mutating POST requests
/// - Automatically intercepts HTTP 401 Unauthorized to trigger user logout/redirect
class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;
  ApiClient._internal();

  final http.Client _client = http.Client();

  /// Global callback or notifier for 401 Unauthorized sessions
  static final ValueNotifier<bool> unauthorizedNotifier = ValueNotifier<bool>(false);

  static void triggerUnauthorized() {
    unauthorizedNotifier.value = true;
  }

  static void resetUnauthorized() {
    unauthorizedNotifier.value = false;
  }

  /// Generate a unique Idempotency key for mutations
  static String generateIdempotencyKey() {
    final rand = Random().nextInt(999999);
    return 'IDEMP-${DateTime.now().millisecondsSinceEpoch}-$rand';
  }

  /// Performs a GET request with automatic Bearer token injection
  Future<http.Response> get(
    String endpoint, {
    Map<String, String>? queryParams,
    Map<String, String>? headers,
    bool requiresAuth = true,
  }) async {
    final uri = _buildUri(endpoint, queryParams);
    final requestHeaders = await _buildHeaders(headers, requiresAuth: requiresAuth);

    try {
      final response = await _client.get(uri, headers: requestHeaders).timeout(ApiConfig.requestTimeout);
      _handleResponseStatus(response);
      return response;
    } catch (e) {
      rethrow;
    }
  }

  /// Performs a POST request with automatic Bearer token and optional Idempotency-Key
  Future<http.Response> post(
    String endpoint, {
    dynamic body,
    String? idempotencyKey,
    Map<String, String>? headers,
    bool requiresAuth = true,
  }) async {
    final uri = _buildUri(endpoint, null);
    final requestHeaders = await _buildHeaders(
      headers,
      requiresAuth: requiresAuth,
      idempotencyKey: idempotencyKey,
    );

    final encodedBody = body != null ? (body is String ? body : jsonEncode(body)) : null;

    try {
      final response = await _client.post(
        uri,
        headers: requestHeaders,
        body: encodedBody,
      ).timeout(ApiConfig.requestTimeout);

      _handleResponseStatus(response);
      return response;
    } catch (e) {
      rethrow;
    }
  }

  Uri _buildUri(String endpoint, Map<String, String>? queryParams) {
    final cleanEndpoint = endpoint.startsWith('/') ? endpoint : '/$endpoint';
    final fullUrl = '${ApiConfig.baseUrl}$cleanEndpoint';
    final baseUri = Uri.parse(fullUrl);
    if (queryParams != null && queryParams.isNotEmpty) {
      return baseUri.replace(queryParameters: {...baseUri.queryParameters, ...queryParams});
    }
    return baseUri;
  }

  Future<Map<String, String>> _buildHeaders(
    Map<String, String>? customHeaders, {
    bool requiresAuth = true,
    String? idempotencyKey,
  }) async {
    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-Correlation-ID': 'CORR-${DateTime.now().millisecondsSinceEpoch}-${Random().nextInt(9999)}',
    };

    if (idempotencyKey != null && idempotencyKey.isNotEmpty) {
      headers['X-Idempotency-Key'] = idempotencyKey;
      headers['Idempotency-Key'] = idempotencyKey; // Dual support
    }

    if (requiresAuth) {
      final token = await SecureTokenStorage.getToken();
      if (token != null && token.isNotEmpty) {
        headers['Authorization'] = 'Bearer $token';
      }
    }

    if (customHeaders != null) {
      headers.addAll(customHeaders);
    }

    return headers;
  }

  void _handleResponseStatus(http.Response response) {
    if (response.statusCode == 401) {
      debugPrint('[ApiClient] HTTP 401 Unauthorized encountered on ${response.request?.url}. Triggering session expiration.');
      SecureTokenStorage.clearSession();
      triggerUnauthorized();
    }
  }

  /// Extracts user-facing error message matching web SPA standards:
  /// - 502/503/504: "PayPink is starting up. Please wait a moment and try again."
  /// - 429: "Too many requests. Please wait a moment and try again."
  /// - RFC-7807 / Problem details: message, error, detail, or reason
  static String extractErrorMessage(int statusCode, String responseBody) {
    if (statusCode == 502 || statusCode == 503 || statusCode == 504) {
      return 'PayPink is starting up. Please wait a moment and try again.';
    }
    if (statusCode == 429) {
      return 'Too many requests. Please wait a moment and try again.';
    }
    try {
      final data = jsonDecode(responseBody);
      if (data is Map<String, dynamic>) {
        final msg = data['error'] ?? data['message'] ?? data['detail'] ?? data['reason'];
        if (msg != null && msg.toString().trim().isNotEmpty) {
          return msg.toString().trim();
        }
      }
    } catch (_) {}
    return '';
  }
}
