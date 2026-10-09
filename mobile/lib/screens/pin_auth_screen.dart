import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';
import '../services/secure_token_storage.dart';
import '../widgets/paypink_keypad.dart';
import '../widgets/paypink_logo.dart';

enum PinScreenMode {
  login,
  setup,
}

class PinAuthScreen extends StatefulWidget {
  final PinScreenMode mode;
  final String? username;
  final String? fullName;
  final VoidCallback? onAuthSuccess;
  final VoidCallback? onFallbackToPassword;
  final VoidCallback? onCancel;
  final bool isDarkMode;

  const PinAuthScreen({
    super.key,
    required this.mode,
    this.username,
    this.fullName,
    this.onAuthSuccess,
    this.onFallbackToPassword,
    this.onCancel,
    this.isDarkMode = false,
  });

  @override
  State<PinAuthScreen> createState() => _PinAuthScreenState();
}

class _PinAuthScreenState extends State<PinAuthScreen> with SingleTickerProviderStateMixin {
  String _currentPin = '';
  String _firstEnteredPin = '';
  bool _isConfirming = false;
  bool _isError = false;
  String _errorMessage = '';

  String _resolvedFullName = '';
  String _resolvedUsername = '';

  late AnimationController _shakeController;
  late Animation<double> _shakeAnimation;

  @override
  void initState() {
    super.initState();
    _resolvedFullName = widget.fullName?.trim() ?? '';
    _resolvedUsername = widget.username?.trim() ?? '';
    _loadUserSession();

    _shakeController = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );
    _shakeAnimation = Tween<double>(begin: 0.0, end: 12.0)
        .chain(CurveTween(curve: Curves.elasticIn))
        .animate(_shakeController);
  }

  Future<void> _loadUserSession() async {
    if (_resolvedFullName.isEmpty) {
      final storedName = await SecureTokenStorage.getFullName();
      if (storedName != null && storedName.trim().isNotEmpty && mounted) {
        setState(() => _resolvedFullName = storedName.trim());
      }
    }
    if (_resolvedUsername.isEmpty) {
      final storedUser = await SecureTokenStorage.getUsername();
      if (storedUser != null && storedUser.trim().isNotEmpty && mounted) {
        setState(() => _resolvedUsername = storedUser.trim());
      }
    }
  }

  String get _displayName {
    if (_resolvedFullName.isNotEmpty) return _resolvedFullName;
    if (_resolvedUsername.isNotEmpty) return _resolvedUsername;
    return 'Customer';
  }

  String get _initials {
    final name = _displayName;
    final parts = name.split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.length >= 2 && parts[0].isNotEmpty && parts[1].isNotEmpty) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    } else if (name.isNotEmpty) {
      return name[0].toUpperCase();
    }
    return 'P';
  }

  @override
  void dispose() {
    _shakeController.dispose();
    super.dispose();
  }

  void _onKeyPress(String digit) {
    if (_currentPin.length >= 6) return;

    setState(() {
      _currentPin += digit;
      _isError = false;
      _errorMessage = '';
    });

    if (_currentPin.length == 6) {
      _handleCompletePin(_currentPin);
    }
  }

  void _onBackspace() {
    if (_currentPin.isNotEmpty) {
      setState(() {
        _currentPin = _currentPin.substring(0, _currentPin.length - 1);
        _isError = false;
        _errorMessage = '';
      });
    }
  }

  void _onClear() {
    setState(() {
      _currentPin = '';
      _isError = false;
      _errorMessage = '';
    });
  }

  Future<void> _handleCompletePin(String pin) async {
    if (widget.mode == PinScreenMode.login) {
      final isValid = await SecureTokenStorage.verifyPin(pin);
      if (isValid) {
        HapticFeedback.mediumImpact();
        if (widget.onAuthSuccess != null) {
          widget.onAuthSuccess!();
        } else if (mounted) {
          Navigator.of(context).pop(true);
        }
      } else {
        _triggerError('Incorrect MPIN. Please try again.');
      }
    } else {
      // Setup Mode
      if (!_isConfirming) {
        // Step 1 done -> Go to Step 2 confirmation
        setState(() {
          _firstEnteredPin = pin;
          _currentPin = '';
          _isConfirming = true;
        });
      } else {
        // Step 2 confirmation
        if (pin == _firstEnteredPin) {
          await SecureTokenStorage.savePin(pin);
          HapticFeedback.mediumImpact();
          if (widget.onAuthSuccess != null) {
            widget.onAuthSuccess!();
          } else if (mounted) {
            Navigator.of(context).pop(true);
          }
        } else {
          _triggerError('PINs do not match. Please try again.');
          setState(() {
            _isConfirming = false;
            _firstEnteredPin = '';
          });
        }
      }
    }
  }

  void _triggerError(String message) {
    HapticFeedback.heavyImpact();
    setState(() {
      _isError = true;
      _errorMessage = message;
      _currentPin = '';
    });
    _shakeController.forward(from: 0.0);
  }

  @override
  Widget build(BuildContext context) {
    final isDark = widget.isDarkMode;
    final bgCol = isDark ? PayPinkTheme.darkBg : PayPinkTheme.paper;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    final displayName = _displayName;
    final initials = _initials;

    return Scaffold(
      backgroundColor: bgCol,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Top bar with optional Cancel / Back
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      if (widget.onCancel != null)
                        IconButton(
                          icon: Icon(Icons.arrow_back_rounded, color: textInk),
                          onPressed: widget.onCancel,
                        )
                      else
                        const SizedBox(width: 48),
                      // Standard PayPink Logo
                      PayPinkLogo(
                        size: 26,
                        showWordmark: true,
                        isDark: isDark,
                      ),
                      const SizedBox(width: 48),
                    ],
                  ),
                  const SizedBox(height: 18),

                  // User Avatar with dynamic initials (e.g. "LV" for Levi Viernes)
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: LinearGradient(
                        colors: isDark
                            ? [PayPinkTheme.wineLight, PayPinkTheme.wineDark]
                            : [PayPinkTheme.pink, PayPinkTheme.wine],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: PayPinkTheme.wine.withValues(alpha: 0.3),
                          blurRadius: 16,
                          offset: const Offset(0, 6),
                        ),
                      ],
                    ),
                    child: Center(
                      child: Text(
                        initials,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 0.5,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 18),

                  // Title & Description
                  Text(
                    widget.mode == PinScreenMode.login
                        ? 'Welcome back, $displayName'
                        : (_isConfirming ? 'Confirm your MPIN' : 'Create your 6-digit MPIN'),
                    textAlign: TextAlign.center,
                    style: PayPinkTheme.display(
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                      color: textInk,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    widget.mode == PinScreenMode.login
                        ? 'Enter your 6-digit MPIN for quick access'
                        : (_isConfirming
                            ? 'Re-enter your 6-digit MPIN to confirm'
                            : 'Set a secure PIN for login and payment authorization'),
                    textAlign: TextAlign.center,
                    style: PayPinkTheme.body(
                      fontSize: 13,
                      color: textMuted,
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Animated PIN Indicator Dots with Shake on error
                  AnimatedBuilder(
                    animation: _shakeAnimation,
                    builder: (context, child) {
                      return Transform.translate(
                        offset: Offset(_isError ? _shakeAnimation.value * (1 - _shakeController.value) : 0, 0),
                        child: child,
                      );
                    },
                    child: PinIndicatorDots(
                      length: _currentPin.length,
                      totalDigits: 6,
                      isError: _isError,
                      isDark: isDark,
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Error Message
                  SizedBox(
                    height: 20,
                    child: _errorMessage.isNotEmpty
                        ? Text(
                            _errorMessage,
                            style: PayPinkTheme.body(
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: PayPinkTheme.red,
                            ),
                          )
                        : null,
                  ),
                  const SizedBox(height: 16),

                  // Keypad
                  PayPinkKeypad(
                    onKeyPressed: _onKeyPress,
                    onBackspace: _onBackspace,
                    onClear: _onClear,
                    isDark: isDark,
                  ),
                  const SizedBox(height: 16),

                  // Fallback to password button
                  if (widget.mode == PinScreenMode.login && widget.onFallbackToPassword != null)
                    TextButton(
                      onPressed: widget.onFallbackToPassword,
                      style: TextButton.styleFrom(
                        foregroundColor: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                      ),
                      child: Text(
                        'Log in with password instead',
                        style: PayPinkTheme.body(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                        ),
                      ),
                    )
                  else
                    const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
