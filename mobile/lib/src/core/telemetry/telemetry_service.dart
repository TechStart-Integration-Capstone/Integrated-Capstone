import 'dart:convert';
import 'package:flutter/foundation.dart';

enum LogLevel { debug, info, warning, error, fatal }

class LogEntry {
  final DateTime timestamp;
  final LogLevel level;
  final String message;
  final String? category;
  final String? stackTrace;
  final Map<String, dynamic>? attributes;

  LogEntry({
    required this.timestamp,
    required this.level,
    required this.message,
    this.category,
    this.stackTrace,
    this.attributes,
  });

  Map<String, dynamic> toJson() => {
        'timestamp': timestamp.toIso8601String(),
        'level': level.name.toUpperCase(),
        'category': category ?? 'APP',
        'message': message,
        if (attributes != null) 'attributes': attributes,
        if (stackTrace != null) 'stackTrace': stackTrace,
      };

  @override
  String toString() {
    final cat = category != null ? '[$category] ' : '';
    final attrs = attributes != null ? ' | attrs: ${jsonEncode(attributes)}' : '';
    return '[${timestamp.toIso8601String()}] [${level.name.toUpperCase()}] $cat$message$attrs';
  }
}

/// Enterprise Telemetry & Structured Logging Service
class TelemetryService {
  final int maxLogHistory;
  final List<LogEntry> _logs = [];

  TelemetryService({this.maxLogHistory = 100});

  List<LogEntry> get logs => List.unmodifiable(_logs);

  void _addLog(LogEntry entry) {
    if (_logs.length >= maxLogHistory) {
      _logs.removeAt(0);
    }
    _logs.add(entry);

    if (kDebugMode) {
      debugPrint(entry.toString());
    }
  }

  void logDebug(String message, {String? category, Map<String, dynamic>? attributes}) {
    _addLog(LogEntry(
      timestamp: DateTime.now().toUtc(),
      level: LogLevel.debug,
      message: message,
      category: category,
      attributes: attributes,
    ));
  }

  void logInfo(String message, {String? category, Map<String, dynamic>? attributes}) {
    _addLog(LogEntry(
      timestamp: DateTime.now().toUtc(),
      level: LogLevel.info,
      message: message,
      category: category,
      attributes: attributes,
    ));
  }

  void logWarning(String message, {String? category, Map<String, dynamic>? attributes}) {
    _addLog(LogEntry(
      timestamp: DateTime.now().toUtc(),
      level: LogLevel.warning,
      message: message,
      category: category,
      attributes: attributes,
    ));
  }

  void logError(
    String message, {
    Object? error,
    StackTrace? stackTrace,
    String? category,
    Map<String, dynamic>? attributes,
  }) {
    final mergedAttrs = Map<String, dynamic>.from(attributes ?? {});
    if (error != null) {
      mergedAttrs['errorDetail'] = error.toString();
    }

    _addLog(LogEntry(
      timestamp: DateTime.now().toUtc(),
      level: LogLevel.error,
      message: message,
      category: category,
      stackTrace: stackTrace?.toString(),
      attributes: mergedAttrs,
    ));
  }

  void logFatal(
    String message, {
    Object? error,
    StackTrace? stackTrace,
    String? category,
    Map<String, dynamic>? attributes,
  }) {
    final mergedAttrs = Map<String, dynamic>.from(attributes ?? {});
    if (error != null) {
      mergedAttrs['fatalDetail'] = error.toString();
    }

    _addLog(LogEntry(
      timestamp: DateTime.now().toUtc(),
      level: LogLevel.fatal,
      message: message,
      category: category,
      stackTrace: stackTrace?.toString(),
      attributes: mergedAttrs,
    ));
  }

  void trackEvent(String eventName, {Map<String, dynamic>? properties}) {
    logInfo('Event: $eventName', category: 'ANALYTICS', attributes: properties);
  }

  void clearLogs() {
    _logs.clear();
  }

  String exportLogsAsJson() {
    return jsonEncode(_logs.map((l) => l.toJson()).toList());
  }
}
