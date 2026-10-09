import 'package:flutter/foundation.dart';

/// Configuration constants for API Gateway and backend microservice connectivity.
/// All requests are routed through the API Gateway at port 8080.
class ApiConfig {
  /// Base API Gateway URL:
  /// - Android Emulator: http://10.0.2.2:8080/api/v1
  /// - iOS / Web / Desktop: http://localhost:8080/api/v1
  /// - Production domain: https://api.yourbank.com/v1
  static String get baseUrl {
    const envUrl = String.fromEnvironment('API_BASE_URL');
    if (envUrl.isNotEmpty) {
      return envUrl;
    }
    if (kIsWeb) {
      return 'http://localhost:8080/api/v1';
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return 'http://10.0.2.2:8080/api/v1';
      case TargetPlatform.iOS:
      case TargetPlatform.macOS:
      case TargetPlatform.windows:
      case TargetPlatform.linux:
      default:
        return 'http://localhost:8080/api/v1';
    }
  }

  // Gateway Endpoint paths
  static const String loginPath = '/auth/login';
  static const String accountsPath = '/accounts';
  static const String transfersPath = '/transfers';
  static const String loanPayPath = '/loans/pay';
  static const String transactionsPath = '/transactions';

  static const Duration requestTimeout = Duration(seconds: 10);
}
