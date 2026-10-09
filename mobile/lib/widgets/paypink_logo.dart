import 'dart:math' as math;
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
                  fontSize: 10,
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

/// Official PayPink 'P' Emblem Watermark Painter for Digital Cards (MOB-103)
/// Exact SVG path rendering of the spiral 'P' watermark:
/// viewBox: 0 0 400 480 with 60% opacity fill
class PayPinkCardWatermarkPainter extends CustomPainter {
  final double opacity;
  final Color color;

  const PayPinkCardWatermarkPainter({
    this.opacity = 0.60,
    this.color = Colors.white,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (size.width <= 0 || size.height <= 0) return;

    final scale = math.min(size.width / 400.0, size.height / 480.0);
    final dx = (size.width - 400.0 * scale) / 2.0;
    final dy = (size.height - 480.0 * scale) / 2.0;

    final paint = Paint()
      ..color = color.withValues(alpha: opacity)
      ..style = PaintingStyle.fill;

    // 1. Top-Left Stem Accent
    // M 40 40 H 105 C 105 40 85 95 40 152 V 40 Z
    final path1 = Path();
    path1.moveTo(dx + 40 * scale, dy + 40 * scale);
    path1.lineTo(dx + 105 * scale, dy + 40 * scale);
    path1.cubicTo(
      dx + 105 * scale, dy + 40 * scale,
      dx + 85 * scale, dy + 95 * scale,
      dx + 40 * scale, dy + 152 * scale,
    );
    path1.lineTo(dx + 40 * scale, dy + 40 * scale);
    path1.close();

    // 2. Main Sweeping Spiral "P" Body & Stem
    final path2 = Path()..fillType = PathFillType.evenOdd;
    path2.moveTo(dx + 40 * scale, dy + 174 * scale);
    path2.cubicTo(
      dx + 88 * scale, dy + 116 * scale,
      dx + 112 * scale, dy + 55 * scale,
      dx + 120 * scale, dy + 40 * scale,
    );
    path2.cubicTo(
      dx + 148 * scale, dy + 28 * scale,
      dx + 182 * scale, dy + 22 * scale,
      dx + 220 * scale, dy + 22 * scale,
    );
    path2.cubicTo(
      dx + 308 * scale, dy + 22 * scale,
      dx + 365 * scale, dy + 78 * scale,
      dx + 365 * scale, dy + 178 * scale,
    );
    path2.cubicTo(
      dx + 365 * scale, dy + 272 * scale,
      dx + 305 * scale, dy + 330 * scale,
      dx + 220 * scale, dy + 330 * scale,
    );
    path2.cubicTo(
      dx + 178 * scale, dy + 330 * scale,
      dx + 148 * scale, dy + 312 * scale,
      dx + 124 * scale, dy + 286 * scale,
    );
    path2.lineTo(dx + 138 * scale, dy + 238 * scale);
    path2.cubicTo(
      dx + 158 * scale, dy + 258 * scale,
      dx + 184 * scale, dy + 270 * scale,
      dx + 216 * scale, dy + 270 * scale,
    );
    path2.cubicTo(
      dx + 272 * scale, dy + 270 * scale,
      dx + 305 * scale, dy + 230 * scale,
      dx + 305 * scale, dy + 178 * scale,
    );
    path2.cubicTo(
      dx + 305 * scale, dy + 124 * scale,
      dx + 272 * scale, dy + 82 * scale,
      dx + 216 * scale, dy + 82 * scale,
    );
    path2.cubicTo(
      dx + 166 * scale, dy + 82 * scale,
      dx + 128 * scale, dy + 118 * scale,
      dx + 105 * scale, dy + 170 * scale,
    );
    path2.lineTo(dx + 105 * scale, dy + 430 * scale);
    path2.lineTo(dx + 40 * scale, dy + 430 * scale);
    path2.lineTo(dx + 40 * scale, dy + 174 * scale);
    path2.close();

    canvas.drawPath(path1, paint);
    canvas.drawPath(path2, paint);
  }

  @override
  bool shouldRepaint(covariant PayPinkCardWatermarkPainter oldDelegate) =>
      oldDelegate.opacity != opacity || oldDelegate.color != color;
}

/// Subtle, high-end PayPink brand watermark widget for account cards
class PayPinkCardWatermark extends StatelessWidget {
  final double? width;
  final double? height;
  final double opacity;
  final Color color;

  const PayPinkCardWatermark({
    super.key,
    this.width,
    this.height,
    this.opacity = 0.60,
    this.color = Colors.white,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      height: height,
      child: CustomPaint(
        painter: PayPinkCardWatermarkPainter(
          opacity: opacity,
          color: color,
        ),
      ),
    );
  }
}

