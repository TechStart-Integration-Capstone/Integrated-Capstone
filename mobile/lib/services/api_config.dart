import 'package:flutter/foundation.dart';

/// Configuration constants for API Gateway and backend microservice connectivity.
class ApiConfig {
  /// Base API Gateway URL:
  /// - Android Emulator default: http://10.0.2.2:8080/api/v1
  /// - iOS / Web / Desktop default: http://localhost:8080/api/v1
  static String get baseUrl {
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

  static const Duration requestTimeout = Duration(seconds: 5);
}
