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
    Widget content = ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
        child: Container(
          padding: padding,
          decoration: PayPinkTheme.glassCardDecoration(
            bg: backgroundColor ?? PayPinkTheme.glassCardBg,
            radius: radius,
            borderColor: borderColor,
          ),
          child: child,
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
