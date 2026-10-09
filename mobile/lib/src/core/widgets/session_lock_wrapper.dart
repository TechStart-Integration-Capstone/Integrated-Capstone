import 'package:flutter/material.dart';
import 'package:paypink_mobile/src/core/security/session_manager.dart';
import 'package:paypink_mobile/screens/pin_auth_screen.dart';

/// Wraps the application to monitor user interaction gestures and display
/// full-screen PIN re-authentication when session auto-lock is triggered.
class SessionLockWrapper extends StatefulWidget {
  final Widget child;
  final SessionManager sessionManager;
  final String? currentUsername;

  const SessionLockWrapper({
    super.key,
    required this.child,
    required this.sessionManager,
    this.currentUsername,
  });

  @override
  State<SessionLockWrapper> createState() => _SessionLockWrapperState();
}

class _SessionLockWrapperState extends State<SessionLockWrapper> {
  @override
  void initState() {
    super.initState();
    widget.sessionManager.startListening();
  }

  @override
  void dispose() {
    widget.sessionManager.stopListening();
    super.dispose();
  }

  void _handleUserInteraction(PointerEvent event) {
    if (!widget.sessionManager.isLocked) {
      widget.sessionManager.recordUserActivity();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      onPointerDown: _handleUserInteraction,
      onPointerMove: _handleUserInteraction,
      child: ValueListenableBuilder<bool>(
        valueListenable: widget.sessionManager.lockStateNotifier,
        builder: (context, isLocked, child) {
          return Stack(
            children: [
              widget.child,
              if (isLocked)
                Positioned.fill(
                  child: Material(
                    color: Colors.black,
                    child: PinAuthScreen(
                      mode: PinScreenMode.login,
                      username: widget.currentUsername ?? 'User',
                      fullName: 'Authorized Session Holder',
                      onAuthSuccess: () {
                        widget.sessionManager.unlockSession();
                      },
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

