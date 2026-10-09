import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';

/// PayPink Custom Numeric Keypad & Animated PIN Indicator
class PayPinkKeypad extends StatelessWidget {
  final ValueChanged<String> onKeyPressed;
  final VoidCallback onBackspace;
  final VoidCallback? onClear;
  final Widget? leftAction;
  final bool isDark;

  const PayPinkKeypad({
    super.key,
    required this.onKeyPressed,
    required this.onBackspace,
    this.onClear,
    this.leftAction,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(maxWidth: 340),
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildRow(['1', '2', '3'], context),
          const SizedBox(height: 14),
          _buildRow(['4', '5', '6'], context),
          const SizedBox(height: 14),
          _buildRow(['7', '8', '9'], context),
          const SizedBox(height: 14),
          _buildBottomRow(context),
        ],
      ),
    );
  }

  Widget _buildRow(List<String> keys, BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: keys.map((key) => _buildKey(key, context)).toList(),
    );
  }

  Widget _buildBottomRow(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        SizedBox(
          width: 72,
          height: 72,
          child: leftAction ?? const SizedBox(),
        ),
        _buildKey('0', context),
        SizedBox(
          width: 72,
          height: 72,
          child: Material(
            color: Colors.transparent,
            shape: const CircleBorder(),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () {
                HapticFeedback.lightImpact();
                onBackspace();
              },
              onLongPress: onClear != null
                  ? () {
                      HapticFeedback.mediumImpact();
                      onClear!();
                    }
                  : null,
              child: Center(
                child: Icon(
                  Icons.backspace_outlined,
                  size: 24,
                  color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildKey(String value, BuildContext context) {
    final subText = switch (value) {
      '2' => 'ABC',
      '3' => 'DEF',
      '4' => 'GHI',
      '5' => 'JKL',
      '6' => 'MNO',
      '7' => 'PQRS',
      '8' => 'TUV',
      '9' => 'WXYZ',
      _ => '',
    };

    final keyBg = isDark ? PayPinkTheme.darkCard : Colors.white;
    final borderCol = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final textCol = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final subCol = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return Container(
      width: 72,
      height: 72,
      decoration: BoxDecoration(
        color: keyBg,
        shape: BoxShape.circle,
        border: Border.all(color: borderCol, width: 1.2),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.25 : 0.04),
            blurRadius: 8,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        shape: const CircleBorder(),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          splashColor: PayPinkTheme.pink.withValues(alpha: 0.25),
          highlightColor: PayPinkTheme.wine.withValues(alpha: 0.15),
          onTap: () {
            HapticFeedback.lightImpact();
            onKeyPressed(value);
          },
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                value,
                style: PayPinkTheme.display(
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                  color: textCol,
                ),
              ),
              if (subText.isNotEmpty)
                Text(
                  subText,
                  style: PayPinkTheme.mono(
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    color: subCol,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Animated 6-digit PIN Indicator Dots
class PinIndicatorDots extends StatelessWidget {
  final int length;
  final int totalDigits;
  final bool isError;
  final bool isDark;

  const PinIndicatorDots({
    super.key,
    required this.length,
    this.totalDigits = 6,
    this.isError = false,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(totalDigits, (index) {
        final isFilled = index < length;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOutBack,
          margin: const EdgeInsets.symmetric(horizontal: 8),
          width: isFilled ? 16 : 14,
          height: isFilled ? 16 : 14,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: isError
                ? PayPinkTheme.red
                : isFilled
                    ? (isDark ? PayPinkTheme.pink : PayPinkTheme.wine)
                    : Colors.transparent,
            border: Border.all(
              color: isError
                  ? PayPinkTheme.red
                  : isFilled
                      ? (isDark ? PayPinkTheme.pink : PayPinkTheme.wine)
                      : (isDark ? PayPinkTheme.darkMuted.withValues(alpha: 0.5) : PayPinkTheme.muted.withValues(alpha: 0.5)),
              width: 2,
            ),
            boxShadow: isFilled
                ? [
                    BoxShadow(
                      color: (isError ? PayPinkTheme.red : (isDark ? PayPinkTheme.pink : PayPinkTheme.wine))
                          .withValues(alpha: 0.4),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ]
                : null,
          ),
        );
      }),
    );
  }
}
