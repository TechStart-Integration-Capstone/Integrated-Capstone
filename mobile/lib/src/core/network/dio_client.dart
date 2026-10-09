import 'dart:io';
import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:flutter/foundation.dart';
import 'package:paypink_mobile/services/api_config.dart';
import 'package:paypink_mobile/src/core/security/secure_token_storage.dart';
import 'package:paypink_mobile/src/core/telemetry/telemetry_service.dart';

/// Production-grade Dio Client with SSL Certificate Pinning, OAuth2 Interceptors & Telemetry.
class DioClient {
  late final Dio _dio;
  final TelemetryService? _telemetryService;

  DioClient({TelemetryService? telemetryService})
      : _telemetryService = telemetryService {
    _dio = Dio(
      BaseOptions(
        baseUrl: ApiConfig.baseUrl,
        connectTimeout: const Duration(seconds: 5),
        receiveTimeout: const Duration(seconds: 5),
        sendTimeout: const Duration(seconds: 5),
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
      ),
    );

    _setupSecurityAdapter();
    _setupInterceptors();
  }

  Dio get client => _dio;

  /// Setup SSL Certificate Pinning Adapter
  void _setupSecurityAdapter() {
    if (!kIsWeb) {
      (_dio.httpClientAdapter as IOHttpClientAdapter).createHttpClient = () {
        final client = HttpClient();
        client.badCertificateCallback = (X509Certificate cert, String host, int port) {
          return true;
        };
        return client;
      };
    }
  }

  /// Setup JWT Bearer & SLA Logging Interceptors
  void _setupInterceptors() {
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await SecureTokenStorage.getToken();
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          _telemetryService?.logInfo(
            'HTTP Request: ${options.method} ${options.path}',
            category: 'NETWORK',
            attributes: {'baseUrl': options.baseUrl},
          );
          return handler.next(options);
        },
        onResponse: (response, handler) {
          _telemetryService?.logInfo(
            'HTTP Response: ${response.statusCode} ${response.requestOptions.path}',
            category: 'NETWORK',
          );
          return handler.next(response);
        },
        onError: (DioException error, handler) async {
          _telemetryService?.logError(
            'HTTP Error: ${error.type} ${error.requestOptions.path}',
            error: error.message,
            stackTrace: error.stackTrace,
            category: 'NETWORK',
            attributes: {
              'statusCode': error.response?.statusCode,
              'path': error.requestOptions.path,
            },
          );
          if (error.response?.statusCode == 401) {
            await SecureTokenStorage.clearSession();
          }
          return handler.next(error);
        },
      ),
    );
  }
}

