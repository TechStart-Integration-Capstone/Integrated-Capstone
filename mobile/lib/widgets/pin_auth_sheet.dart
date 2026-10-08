import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';
import '../services/secure_token_storage.dart';
import 'paypink_keypad.dart';

/// Step-Up Transaction PIN Authorization Bottom Sheet
class PinAuthSheet extends StatefulWidget {
  final String title;
  final String description;
  final double? amount;

  const PinAuthSheet({
    super.key,
    this.title = 'Authorize Transaction',
    required this.description,
    this.amount,
  });

  static Future<bool> show(
    BuildContext context, {
    String title = 'Authorize Transaction',
    required String description,
    double? amount,
  }) async {
    // If user hasn't set an MPIN yet, initialize default demo PIN '123456'
    final hasPin = await SecureTokenStorage.hasPin();
    if (!hasPin) {
      await SecureTokenStorage.savePin('123456');
    }
    if (!context.mounted) return false;

    final result = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => PinAuthSheet(
        title: title,
        description: description,
        amount: amount,
      ),
    );

    return result ?? false;
  }

  @override
  State<PinAuthSheet> createState() => _PinAuthSheetState();
}

class _PinAuthSheetState extends State<PinAuthSheet> with SingleTickerProviderStateMixin {
  String _enteredPin = '';
  bool _isError = false;
  String _errorMessage = '';

  late AnimationController _shakeController;
  late Animation<double> _shakeAnimation;

  @override
  void initState() {
    super.initState();
    _shakeController = AnimationController(
      duration: const Duration(milliseconds: 350),
      vsync: this,
    );
    _shakeAnimation = Tween<double>(begin: 0.0, end: 10.0)
        .chain(CurveTween(curve: Curves.elasticIn))
        .animate(_shakeController);
  }

  @override
  void dispose() {
    _shakeController.dispose();
    super.dispose();
  }

  void _onKeyPress(String digit) {
    if (_enteredPin.length >= 6) return;

    setState(() {
      _enteredPin += digit;
      _isError = false;
      _errorMessage = '';
    });

    if (_enteredPin.length == 6) {
      _verifyPin(_enteredPin);
    }
  }

  void _onBackspace() {
    if (_enteredPin.isNotEmpty) {
      setState(() {
        _enteredPin = _enteredPin.substring(0, _enteredPin.length - 1);
        _isError = false;
        _errorMessage = '';
      });
    }
  }

  Future<void> _verifyPin(String pin) async {
    final valid = await SecureTokenStorage.verifyPin(pin);
    if (!mounted) return;

    if (valid) {
      HapticFeedback.mediumImpact();
      Navigator.pop(context, true);
    } else {
      HapticFeedback.heavyImpact();
      setState(() {
        _isError = true;
        _errorMessage = 'Incorrect MPIN. Try again.';
        _enteredPin = '';
      });
      _shakeController.forward(from: 0.0);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bgCol = isDark ? PayPinkTheme.darkCard : Colors.white;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return Container(
      padding: const EdgeInsets.only(top: 12, left: 20, right: 20, bottom: 24),
      decoration: BoxDecoration(
        color: bgCol,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x33000000),
            blurRadius: 30,
            offset: Offset(0, -8),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Container(
            width: 38,
            height: 4,
            decoration: BoxDecoration(
              color: isDark ? PayPinkTheme.darkLine : Colors.grey.shade300,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(height: 16),

          // Security Lock Icon
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: isDark ? PayPinkTheme.wineLight.withValues(alpha: 0.25) : PayPinkTheme.pinkSubtle,
              shape: BoxShape.circle,
            ),
            child: Icon(
              Icons.lock_outline_rounded,
              color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
              size: 24,
            ),
          ),
          const SizedBox(height: 12),

          Text(
            widget.title,
            style: PayPinkTheme.display(
              fontSize: 18,
              fontWeight: FontWeight.w800,
              color: textInk,
            ),
          ),
          const SizedBox(height: 4),

          if (widget.amount != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(
                '₱${widget.amount!.toStringAsFixed(2)}',
                style: PayPinkTheme.display(
                  fontSize: 26,
                  fontWeight: FontWeight.w900,
                  color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                ),
              ),
            ),

          Text(
            widget.description,
            textAlign: TextAlign.center,
            style: PayPinkTheme.body(
              fontSize: 12,
              color: textMuted,
            ),
          ),
          const SizedBox(height: 20),

          // Animated PIN Indicator
          AnimatedBuilder(
            animation: _shakeAnimation,
            builder: (context, child) {
              return Transform.translate(
                offset: Offset(_isError ? _shakeAnimation.value * (1 - _shakeController.value) : 0, 0),
                child: child,
              );
            },
            child: PinIndicatorDots(
              length: _enteredPin.length,
              totalDigits: 6,
              isError: _isError,
              isDark: isDark,
            ),
          ),
          const SizedBox(height: 8),

          SizedBox(
            height: 18,
            child: _errorMessage.isNotEmpty
                ? Text(
                    _errorMessage,
                    style: PayPinkTheme.body(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: PayPinkTheme.red,
                    ),
                  )
                : null,
          ),
          const SizedBox(height: 12),

          // Numeric Keypad
          PayPinkKeypad(
            onKeyPressed: _onKeyPress,
            onBackspace: _onBackspace,
            isDark: isDark,
          ),
          const SizedBox(height: 10),

          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(
              'Cancel',
              style: PayPinkTheme.body(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: textMuted,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
