import 'dart:ui';
import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';

class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;
  final Color? backgroundColor;
  final Color? borderColor;
  final VoidCallback? onTap;

  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = 20,
    this.backgroundColor,
    this.borderColor,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final effectiveBg = backgroundColor ?? (isDark ? PayPinkTheme.darkGlassCardBg : PayPinkTheme.glassCardBg);
    final effectiveBorder = borderColor ?? (isDark ? PayPinkTheme.darkGlassBorder : PayPinkTheme.glassBorder);

    Widget content = ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
        child: Container(
          padding: padding,
          decoration: BoxDecoration(
            color: effectiveBg,
            borderRadius: BorderRadius.circular(radius),
            border: Border.all(color: effectiveBorder, width: 1.2),
            boxShadow: [
              BoxShadow(
                color: (isDark ? Colors.black : PayPinkTheme.wine).withValues(alpha: isDark ? 0.35 : 0.08),
                blurRadius: 24,
                offset: const Offset(0, 10),
              ),
              BoxShadow(
                color: isDark ? const Color(0x1AFFFFFF) : const Color(0x55FFFFFF),
                blurRadius: 0,
                spreadRadius: 1,
              ),
            ],
          ),
          child: DefaultTextStyle.merge(
            style: TextStyle(
              color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink,
            ),
            child: child,
          ),
        ),
      ),
    );

    if (onTap != null) {
      return Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(radius),
          onTap: onTap,
          splashColor: PayPinkTheme.wine.withValues(alpha: 0.08),
          highlightColor: PayPinkTheme.wine.withValues(alpha: 0.04),
          child: content,
        ),
      );
    }
    return content;
  }
}
