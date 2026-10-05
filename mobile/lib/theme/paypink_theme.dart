import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class PayPinkTheme {
  // Brand colors matching Webapp and Capstone 2 design tokens
  static const Color wine = Color(0xFF651C3E);
  static const Color wineDark = Color(0xFF47142C);
  static const Color wineLight = Color(0xFF8A2754);
  static const Color wineGlass = Color(0xD9651C3E);
  static const Color wineGlow = Color(0x59BE185D);

  static const Color pink = Color(0xFFF7D6E3);
  static const Color pinkSubtle = Color(0xFFFBF0F4);
  static const Color paper = Color(0xFFFAF9F6);
  static const Color ink = Color(0xFF29242A);
  static const Color muted = Color(0xFF807A80);
  static const Color line = Color(0xFFECE8E7);

  static const Color green = Color(0xFF25745C);
  static const Color greenBg = Color(0xFFEDF5F0);
  static const Color amber = Color(0xFFB45309);
  static const Color amberBg = Color(0xFFFEF3C7);
  static const Color red = Color(0xFFB91C1C);
  static const Color redBg = Color(0xFFFEE2E2);

  // Glassmorphism tokens
  static const Color glassWhite = Color(0xB8FFFFFF);
  static const Color glassCardBg = Color(0xC7FFFFFF);
  static const Color glassBorder = Color(0xD9FFFFFF);
  static const Color glassBorderSubtle = Color(0x66FFFFFF);

  // Typography with GoogleFonts fallback
  static TextStyle display({
    double fontSize = 16,
    FontWeight fontWeight = FontWeight.w700,
    Color color = ink,
    double letterSpacing = -0.5,
    double? height,
  }) {
    return GoogleFonts.manrope(
      fontSize: fontSize,
      fontWeight: fontWeight,
      color: color,
      letterSpacing: letterSpacing,
      height: height,
    );
  }

  static TextStyle body({
    double fontSize = 14,
    FontWeight fontWeight = FontWeight.w400,
    Color color = ink,
    double? height,
    TextDecoration? decoration,
  }) {
    return GoogleFonts.dmSans(
      fontSize: fontSize,
      fontWeight: fontWeight,
      color: color,
      height: height,
      decoration: decoration,
    );
  }

  static TextStyle mono({
    double fontSize = 11,
    FontWeight fontWeight = FontWeight.w500,
    Color color = ink,
  }) {
    return GoogleFonts.jetBrainsMono(
      fontSize: fontSize,
      fontWeight: fontWeight,
      color: color,
    );
  }

  static BoxDecoration glassCardDecoration({
    Color bg = glassCardBg,
    double radius = 20,
    Color? borderColor,
  }) {
    return BoxDecoration(
      color: bg,
      borderRadius: BorderRadius.circular(radius),
      border: Border.all(color: borderColor ?? glassBorder, width: 1.2),
      boxShadow: [
        BoxShadow(
          color: wine.withValues(alpha: 0.08),
          blurRadius: 24,
          offset: const Offset(0, 10),
        ),
        const BoxShadow(
          color: Color(0x55FFFFFF),
          blurRadius: 0,
          spreadRadius: 1,
        ),
      ],
    );
  }

  static BoxDecoration wineHeroDecoration({double radius = 24}) {
    return BoxDecoration(
      gradient: const LinearGradient(
        colors: [Color(0xFF7A204C), Color(0xFF551633), Color(0xFF380B20)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      ),
      borderRadius: BorderRadius.circular(radius),
      border: Border.all(color: Colors.white.withValues(alpha: 0.3), width: 1.2),
      boxShadow: [
        BoxShadow(
          color: wineDark.withValues(alpha: 0.45),
          blurRadius: 28,
          offset: const Offset(0, 14),
        ),
      ],
    );
  }
}

/// Custom painter for fintech concentric ripple rings overlay on Hero Card
class ConcentricRingsPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withValues(alpha: 0.05)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;

    final center = Offset(size.width * 0.85, size.height * 0.2);
    for (double r = 40; r <= 240; r += 35) {
      canvas.drawCircle(center, r, paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
