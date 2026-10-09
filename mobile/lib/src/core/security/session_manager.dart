import 'dart:async';
import 'package:flutter/widgets.dart';

/// Manages enterprise session lifecycle, user activity tracking, and auto-lock on inactivity/backgrounding.
class SessionManager with WidgetsBindingObserver {
  final Duration inactivityTimeout;
  final Function()? onSessionLocked;

  bool _isLocked = false;
  DateTime _lastActivityTime = DateTime.now();
  DateTime? _pausedTime;
  Timer? _inactivityTimer;

  final ValueNotifier<bool> lockStateNotifier = ValueNotifier<bool>(false);

  SessionManager({
    this.inactivityTimeout = const Duration(minutes: 3),
    this.onSessionLocked,
  }) {
    _startInactivityTimer();
  }

  bool get isLocked => _isLocked;

  void startListening() {
    WidgetsBinding.instance.addObserver(this);
    recordUserActivity();
  }

  void stopListening() {
    WidgetsBinding.instance.removeObserver(this);
    _inactivityTimer?.cancel();
  }

  void recordUserActivity() {
    _lastActivityTime = DateTime.now();
    _resetInactivityTimer();
  }

  void _resetInactivityTimer() {
    _inactivityTimer?.cancel();
    if (_isLocked) return;

    _inactivityTimer = Timer(inactivityTimeout, () {
      lockSession();
    });
  }

  void _startInactivityTimer() {
    _resetInactivityTimer();
  }

  void lockSession() {
    if (_isLocked) return;
    _isLocked = true;
    lockStateNotifier.value = true;
    onSessionLocked?.call();
  }

  void unlockSession() {
    _isLocked = false;
    lockStateNotifier.value = false;
    recordUserActivity();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);

    if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive) {
      _pausedTime = DateTime.now();
      _inactivityTimer?.cancel();
    } else if (state == AppLifecycleState.resumed) {
      if (_pausedTime != null) {
        final elapsed = DateTime.now().difference(_pausedTime!);
        if (elapsed >= inactivityTimeout) {
          lockSession();
        } else {
          recordUserActivity();
        }
        _pausedTime = null;
      } else {
        recordUserActivity();
      }
    }
  }

  void dispose() {
    stopListening();
    lockStateNotifier.dispose();
  }
}
