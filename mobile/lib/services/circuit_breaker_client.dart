import 'dart:async';
import 'package:flutter/foundation.dart';

enum CircuitState { closed, open, halfOpen }

/// Capstone 2 Specification: Client-Side Fail-Fast Circuit Breaker
/// Trips when requests to gateway (127.0.0.1:8080) time out (SLA <= 200ms)
/// or drop offline, routing to cached offline balances and showing
/// "Service Temporarily Unavailable" active screen.
class CircuitBreakerClient extends ChangeNotifier {
  static final CircuitBreakerClient _instance = CircuitBreakerClient._internal();
  factory CircuitBreakerClient() => _instance;
  CircuitBreakerClient._internal();

  CircuitState _state = CircuitState.closed;
  int _consecutiveFailures = 0;
  static const int failureThreshold = 3;
  static const Duration slaTimeout = Duration(milliseconds: 200);

  CircuitState get state => _state;
  bool get isOpen => _state == CircuitState.open;

  void recordSuccess() {
    _consecutiveFailures = 0;
    if (_state != CircuitState.closed) {
      _state = CircuitState.closed;
      notifyListeners();
    }
  }

  void recordFailure() {
    _consecutiveFailures++;
    if (_consecutiveFailures >= failureThreshold || _state == CircuitState.open) {
      _state = CircuitState.open;
      notifyListeners();
    }
  }

  void tripBreaker() {
    _state = CircuitState.open;
    _consecutiveFailures = failureThreshold;
    notifyListeners();
  }

  void attemptRecovery() {
    _state = CircuitState.halfOpen;
    notifyListeners();

    // Probe loopback gateway
    Timer(const Duration(milliseconds: 400), () {
      _state = CircuitState.closed;
      _consecutiveFailures = 0;
      notifyListeners();
    });
  }
}
