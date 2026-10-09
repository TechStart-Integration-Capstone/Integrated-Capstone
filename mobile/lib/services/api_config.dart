import 'package:flutter/foundation.dart';

/// Configuration constants for API Gateway and backend microservice connectivity.
/// All requests are routed through the API Gateway at port 8080.
class ApiConfig {
  /// Base API Gateway URL:
  /// - Android Emulator: http://10.0.2.2:8080/api/v1
  /// - iOS / Web / Desktop: http://localhost:8080/api/v1
  /// - Production domain: https://api.yourbank.com/v1
  /// Canonical Cloud API Gateway URL for Azure West US 2
  static const String defaultCloudGateway =
      'http://paypink-levi-westus2.westus2.cloudapp.azure.com:8080/api/v1';

  /// Base API Gateway URL:
  /// - Priority 1: Explicit build-time override via --dart-define=API_BASE_URL=...
  /// - Priority 2: When running as Web hosted on Azure VM, auto-bind to the host's port 8080
  /// - Priority 3: Default to the live Azure Cloud backend so Mobile and Web are ALWAYS
  ///   synchronized with Azure SQL across all team branches and fresh merges!
  static String get baseUrl {
    const envUrl = String.fromEnvironment('API_BASE_URL');
    if (envUrl.isNotEmpty) {
      return envUrl;
    }
    if (kIsWeb) {
      final host = Uri.base.host;
      if (host.isNotEmpty && host != 'localhost' && host != '127.0.0.1') {
        return 'http://$host:8080/api/v1';
      }
    }
    return defaultCloudGateway;
  }

  // Gateway Endpoint paths
  static const String loginPath = '/auth/login';
  static const String accountsPath = '/accounts';
  static const String transfersPath = '/transfers';
  static const String loanPayPath = '/loans/pay';
  static const String transactionsPath = '/transactions';

  static const Duration requestTimeout = Duration(seconds: 10);
}
