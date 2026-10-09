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
  static const Color indigo = Color(0xFF4F46E5);
  static const Color indigoBg = Color(0xFFEEF2FF);

  // Glassmorphism tokens
  static const Color glassWhite = Color(0xB8FFFFFF);
  static const Color glassCardBg = Color(0xC7FFFFFF);
  static const Color glassBorder = Color(0xD9FFFFFF);
  static const Color glassBorderSubtle = Color(0x66FFFFFF);

  // Dark Mode Tokens (Luminous Obsidian Navy & Soft Rose Palette - Matching Design Inspiration)
  static const Color darkBg = Color(0xFF0B0E17); // Deep Obsidian Midnight Navy
  static const Color darkPaper = Color(0xFF121622); // Deep Frosted Glass Card Paper
  static const Color darkCard = Color(0xFF151A29); // Floating Elevated Navy Card
  static const Color darkInk = Color(0xFFFFFFFF); // Pure Luminous White (Maximum Contrast & Readability)
  static const Color darkMuted = Color(0xFF9DA4B5); // High Legibility Soft Slate Gray
  static const Color darkLine = Color(0xFF222A3B); // Subtle Dark Hairline Divider
  static const Color darkGlassBorder = Color(0x383F4C68); // Translucent Dark Slate Hairline Border
  static const Color darkGlassCardBg = Color(0xF0121623); // Frosted Obsidian Glass Card Background

  // Ambient Background Gradients
  static const LinearGradient lightBgGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [
      Color(0xFFFFF0F5), // Lavender blush / soft rose petal hint
      Color(0xFFFFF7F9), // Subtle warm creamy rose
      Color(0xFFFBF2F6), // Delicate touch of luxury pink
    ],
    stops: [0.0, 0.45, 1.0],
  );

  static const LinearGradient darkBgGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [
      Color(0xFF0F1422), // Luminous obsidian slate
      Color(0xFF0B0E17), // Deep midnight obsidian navy
    ],
  );

  static const LinearGradient cardPinkGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [pink, wine],
  );

  // Distinct Account Card Color Gradients with Lower-Half Gradient Black (MOB-103)
  // Card 1: Checking Account (Default) — Signature PayPink Vibrant Rose to Gradient Black
  static const LinearGradient cardCheckingGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0xFFE11D48), Color(0xFFDB2777), Color(0xFF220E18), Color(0xFF09090B)],
    stops: [0.0, 0.38, 0.72, 1.0],
  );

  // Card 2: Savings Account — Soft Blush / Pastel Rose to Gradient Black
  static const LinearGradient cardSavingsGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0xFFFDA4AF), Color(0xFFFB7185), Color(0xFF221118), Color(0xFF09090B)],
    stops: [0.0, 0.38, 0.72, 1.0],
  );

  // Last Card: Pinkish Beige (Desert Rose / Champagne Blush Nude to Gradient Black)
  static const LinearGradient cardPinkishBeigeGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0xFFD8ABA0), Color(0xFFBA8677), Color(0xFF261414), Color(0xFF09090B)],
    stops: [0.0, 0.38, 0.72, 1.0],
  );

  // Card 3: Alternative / Third Card / Reserve — Electric Fuchsia & Magenta Pink to Gradient Black
  static const LinearGradient cardFuchsiaGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0xFFE879F9), Color(0xFFC026D3), Color(0xFF1F0A24), Color(0xFF09090B)],
    stops: [0.0, 0.38, 0.72, 1.0],
  );

  // Card 4: Loan Account / Credit — Deep Obsidian Wine to Gradient Black
  static const LinearGradient cardLoanGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0xFF881337), Color(0xFF4C0519), Color(0xFF18181B), Color(0xFF09090B)],
    stops: [0.0, 0.38, 0.72, 1.0],
  );

  static String get fontFamily =>
      GoogleFonts.plusJakartaSans().fontFamily ?? 'Plus Jakarta Sans';

  static ThemeData get lightTheme => ThemeData(
        textTheme: GoogleFonts.plusJakartaSansTextTheme(),
        brightness: Brightness.light,
        scaffoldBackgroundColor: const Color(0xFFFFF5F8),
        colorScheme: ColorScheme.fromSeed(
          seedColor: wine,
          brightness: Brightness.light,
          surface: const Color(0xFFFFF5F8),
        ),
        useMaterial3: true,
      );

  static ThemeData get darkTheme => ThemeData(
        textTheme: GoogleFonts.plusJakartaSansTextTheme(ThemeData.dark().textTheme),
        brightness: Brightness.dark,
        scaffoldBackgroundColor: darkBg,
        colorScheme: ColorScheme.fromSeed(
          seedColor: wine,
          brightness: Brightness.dark,
          surface: darkBg,
        ),
        useMaterial3: true,
      );

  // Typography with Google Fonts Plus Jakarta Sans
  static TextStyle display({
    double fontSize = 16,
    FontWeight fontWeight = FontWeight.w700,
    Color color = ink,
    double letterSpacing = -0.5,
    double? height,
    FontStyle? fontStyle,
  }) {
    return GoogleFonts.plusJakartaSans(
      fontSize: fontSize,
      fontWeight: fontWeight,
      color: color,
      letterSpacing: letterSpacing,
      height: height,
      fontStyle: fontStyle,
    );
  }

  static TextStyle body({
    double fontSize = 14,
    FontWeight fontWeight = FontWeight.w400,
    Color color = ink,
    double? height,
    TextDecoration? decoration,
  }) {
    return GoogleFonts.plusJakartaSans(
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
    double? letterSpacing,
  }) {
    return GoogleFonts.jetBrainsMono(
      fontSize: fontSize,
      fontWeight: fontWeight,
      color: color,
      letterSpacing: letterSpacing,
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
        colors: [Color(0xFF5B162F), Color(0xFF3D0E1F), Color(0xFF280814)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      ),
      borderRadius: BorderRadius.circular(radius),
      border: Border.all(color: const Color(0xFF7A2444).withValues(alpha: 0.5), width: 1.2),
      boxShadow: [
        BoxShadow(
          color: Colors.black.withValues(alpha: 0.45),
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
