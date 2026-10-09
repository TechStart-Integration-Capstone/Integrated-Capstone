import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';

/// Official vector painter for the PayPink stylized 'P' lettermark
/// Matches the exact SVG brand mark in the PayPink web ecosystem:
/// M 13 29 V 11 h 8 a 6 6 0 0 1 0 12 h -8
class PayPinkLogoMarkPainter extends CustomPainter {
  final Color strokeColor;

  const PayPinkLogoMarkPainter({this.strokeColor = Colors.white});

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 40.0;
    final strokeWidth = 4.2 * scale;

    final paint = Paint()
      ..color = strokeColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final path = Path();
    // Start at bottom of stem: (13, 29)
    path.moveTo(13 * scale, 29 * scale);
    // Vertical stem up to: (13, 11)
    path.lineTo(13 * scale, 11 * scale);
    // Top bar right to: (21, 11)
    path.lineTo(21 * scale, 11 * scale);
    // Arc radius 6 down to: (21, 23)
    path.arcToPoint(
      Offset(21 * scale, 23 * scale),
      radius: Radius.circular(6 * scale),
      clockwise: true,
    );
    // Bottom bar of loop left to: (13, 23)
    path.lineTo(13 * scale, 23 * scale);

    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant PayPinkLogoMarkPainter oldDelegate) =>
      oldDelegate.strokeColor != strokeColor;
}

/// Standard, consistent PayPink Brand Logo for the mobile application.
/// Strictly enforces brand identity across Login, PIN auth, Dashboard, and Statements.
class PayPinkLogo extends StatelessWidget {
  final double size;
  final bool showWordmark;
  final bool isDark;
  final bool showShadow;
  final Axis layout;
  final Color? wordmarkColor;
  final String? subtitle;

  const PayPinkLogo({
    super.key,
    this.size = 34,
    this.showWordmark = true,
    this.isDark = false,
    this.showShadow = true,
    this.layout = Axis.horizontal,
    this.wordmarkColor,
    this.subtitle,
  });

  /// Compact logo for app bar headers
  factory PayPinkLogo.header({bool isDark = false, String? subtitle}) => PayPinkLogo(
        size: 32,
        showWordmark: true,
        isDark: isDark,
        subtitle: subtitle,
      );

  /// Prominent badge logo for auth and onboarding screens
  factory PayPinkLogo.authHero({bool isDark = false}) => PayPinkLogo(
        size: 64,
        showWordmark: true,
        isDark: isDark,
        layout: Axis.vertical,
        subtitle: 'Personal Mobile Banking',
      );

  /// Small emblem mark only
  factory PayPinkLogo.markOnly({double size = 28, bool isDark = false}) => PayPinkLogo(
        size: size,
        showWordmark: false,
        isDark: isDark,
      );

  @override
  Widget build(BuildContext context) {
    final effectiveWordmarkColor = wordmarkColor ??
        (isDark ? Colors.white : PayPinkTheme.wine);

    final mark = Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: PayPinkTheme.wine,
        borderRadius: BorderRadius.circular(size * 0.28),
        boxShadow: showShadow
            ? [
                BoxShadow(
                  color: PayPinkTheme.wine.withValues(alpha: 0.30),
                  blurRadius: size * 0.35,
                  offset: Offset(0, size * 0.12),
                ),
              ]
            : null,
      ),
      child: Center(
        child: SizedBox(
          width: size,
          height: size,
          child: const CustomPaint(
            painter: PayPinkLogoMarkPainter(strokeColor: Colors.white),
          ),
        ),
      ),
    );

    if (!showWordmark) {
      return mark;
    }

    final wordmarkFontSize = size * 0.58;
    final regFontSize = wordmarkFontSize * 0.44;

    final wordmark = Row(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.baseline,
      textBaseline: TextBaseline.alphabetic,
      children: [
        Text(
          'PayPink',
          style: PayPinkTheme.display(
            fontSize: wordmarkFontSize,
            fontWeight: FontWeight.w800,
            color: effectiveWordmarkColor,
            letterSpacing: -0.8,
          ),
        ),
        Transform.translate(
          offset: Offset(1, -wordmarkFontSize * 0.35),
          child: Text(
            '®',
            style: PayPinkTheme.display(
              fontSize: regFontSize,
              fontWeight: FontWeight.w700,
              color: effectiveWordmarkColor,
            ),
          ),
        ),
      ],
    );

    if (layout == Axis.vertical) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          mark,
          SizedBox(height: size * 0.24),
          wordmark,
          if (subtitle != null) ...[
            const SizedBox(height: 4),
            Text(
              subtitle!,
              style: PayPinkTheme.body(
                fontSize: 12,
                color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ],
      );
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        mark,
        SizedBox(width: size * 0.28),
        if (subtitle != null)
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              wordmark,
              Text(
                subtitle!,
                style: PayPinkTheme.body(
                  fontSize: 9.5,
                  color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          )
        else
          wordmark,
      ],
    );
  }
}
