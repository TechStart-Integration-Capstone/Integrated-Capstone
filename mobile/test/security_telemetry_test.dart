import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/src/core/security/session_manager.dart';
import 'package:paypink_mobile/src/core/telemetry/telemetry_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('TelemetryService unit tests', () {
    late TelemetryService telemetry;

    setUp(() {
      telemetry = TelemetryService(maxLogHistory: 5);
    });

    test('logInfo, logWarning, and logError append structured log entries', () {
      telemetry.logInfo('User initiated login', category: 'AUTH');
      telemetry.logWarning('Low memory warning', category: 'SYSTEM');
      telemetry.logError('HTTP Host Lookup Failed', category: 'NETWORK', attributes: {'statusCode': 503});

      expect(telemetry.logs.length, 3);
      expect(telemetry.logs[0].message, 'User initiated login');
      expect(telemetry.logs[0].category, 'AUTH');
      expect(telemetry.logs[0].level, LogLevel.info);

      expect(telemetry.logs[2].category, 'NETWORK');
      expect(telemetry.logs[2].attributes?['statusCode'], 503);
    });

    test('circular buffer caps log history to maxLogHistory limit', () {
      for (int i = 1; i <= 10; i++) {
        telemetry.logInfo('Log #$i');
      }

      expect(telemetry.logs.length, 5);
      expect(telemetry.logs.first.message, 'Log #6');
      expect(telemetry.logs.last.message, 'Log #10');
    });

    test('exportLogsAsJson formats logs into valid JSON array string', () {
      telemetry.logInfo('Test event', category: 'TEST');
      final json = telemetry.exportLogsAsJson();

      expect(json, contains('"message":"Test event"'));
      expect(json, contains('"category":"TEST"'));
    });
  });

  group('SessionManager auto-lock unit tests', () {
    test('lockSession sets isLocked to true and notifies listeners', () {
      bool eventFired = false;
      final sessionManager = SessionManager(
        inactivityTimeout: const Duration(seconds: 10),
        onSessionLocked: () {
          eventFired = true;
        },
      );

      expect(sessionManager.isLocked, isFalse);
      expect(sessionManager.lockStateNotifier.value, isFalse);

      sessionManager.lockSession();

      expect(sessionManager.isLocked, isTrue);
      expect(sessionManager.lockStateNotifier.value, isTrue);
      expect(eventFired, isTrue);

      sessionManager.unlockSession();
      expect(sessionManager.isLocked, isFalse);
      expect(sessionManager.lockStateNotifier.value, isFalse);
    });

    test('didChangeAppLifecycleState triggers lock when paused duration exceeds timeout', () {
      final sessionManager = SessionManager(
        inactivityTimeout: const Duration(milliseconds: 50),
      );

      sessionManager.didChangeAppLifecycleState(AppLifecycleState.paused);
      
      // Simulate 100ms pause in background
      Future.delayed(const Duration(milliseconds: 100), () {
        sessionManager.didChangeAppLifecycleState(AppLifecycleState.resumed);
        expect(sessionManager.isLocked, isTrue);
      });
    });
  });
}
