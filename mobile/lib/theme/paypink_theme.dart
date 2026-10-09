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

  // Web form tokens (frontend/bank/bank.css)
  static const Color inputBorder = Color(0xFFE1DADD);
  static const Color placeholder = Color(0xFFB0A6AC);
  static const Color errorBg = Color(0xFFFCF0EE);
  static const Color errorBorder = Color(0xFFF0D5D0);
  static const Color errorText = Color(0xFF9F3C3C);
  static const Color focusRing = Color(0xFFBD688C);

  // Card surface tokens. Flat white with a hairline border, matching the web cards.
  static const Color glassWhite = Color(0xFFFFFFFF);
  static const Color glassCardBg = Color(0xFFFFFFFF);
  static const Color glassBorder = line;
  static const Color glassBorderSubtle = line;

  // Dark mode: a wine-tinted near-black so the brand survives in the dark.
  static const Color darkBg = Color(0xFF161013);
  static const Color darkPaper = Color(0xFF1D1519);
  static const Color darkCard = Color(0xFF241A1F);
  static const Color darkInk = Color(0xFFF7F1F3);
  static const Color darkMuted = Color(0xFFA99AA2);
  static const Color darkLine = Color(0xFF35272E);
  static const Color darkGlassBorder = Color(0xFF35272E);
  static const Color darkGlassCardBg = Color(0xFF1D1519);

  // Corner radii, matching the web: inputs/buttons 9, account cards 11–12, panels 14, dialogs 17.
  static const double radiusSm = 9;
  static const double radiusMd = 12;
  static const double radiusLg = 14;
  static const double radiusXl = 17;

  // Page backgrounds are flat paper, like the web. Kept as gradients so existing callers still work.
  static const LinearGradient lightBgGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [paper, paper],
  );

  static const LinearGradient darkBgGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [darkBg, darkBg],
  );

  static const LinearGradient cardPinkGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF7A2A4E), wine],
  );

  // Account card colors follow the web's account-symbol palette (wine, rose, sage, sand)
  // with a gentle two-stop tone shift instead of the old fade-to-black.
  static const LinearGradient cardCheckingGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF7A2A4E), wine],
  );

  static const LinearGradient cardSavingsGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF7E8A6A), Color(0xFF5C6649)],
  );

  static const LinearGradient cardPinkishBeigeGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFFAE9270), Color(0xFF85694C)],
  );

  static const LinearGradient cardFuchsiaGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF9A5373), Color(0xFF6E3550)],
  );

  static const LinearGradient cardLoanGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [wineDark, Color(0xFF29242A)],
  );

  static String get fontFamily => GoogleFonts.dmSans().fontFamily ?? 'DM Sans';

  static ThemeData get lightTheme => ThemeData(
        textTheme: GoogleFonts.dmSansTextTheme(),
        brightness: Brightness.light,
        scaffoldBackgroundColor: paper,
        colorScheme: ColorScheme.fromSeed(
          seedColor: wine,
          primary: wine,
          brightness: Brightness.light,
          surface: paper,
        ),
        useMaterial3: true,
      );

  static ThemeData get darkTheme => ThemeData(
        textTheme: GoogleFonts.dmSansTextTheme(ThemeData.dark().textTheme),
        brightness: Brightness.dark,
        scaffoldBackgroundColor: darkBg,
        colorScheme: ColorScheme.fromSeed(
          seedColor: wine,
          brightness: Brightness.dark,
          surface: darkBg,
        ),
        useMaterial3: true,
      );

  // Bundled weights: Manrope 400–800, DM Sans 400–700 (assets/fonts). Clamp so we never
  // ask google_fonts for a weight that isn't on disk.
  static FontWeight _clamp(FontWeight w, FontWeight min, FontWeight max) {
    if (w.value < min.value) return min;
    if (w.value > max.value) return max;
    return w;
  }

  static const List<FontFeature> tabularFigures = [FontFeature.tabularFigures()];

  // Headings and amounts: Manrope, like the web's --display.
  static TextStyle display({
    double fontSize = 16,
    FontWeight fontWeight = FontWeight.w700,
    Color color = ink,
    double letterSpacing = -0.5,
    double? height,
    FontStyle? fontStyle,
  }) {
    return GoogleFonts.manrope(
      fontSize: fontSize,
      fontWeight: _clamp(fontWeight, FontWeight.w400, FontWeight.w800),
      color: color,
      letterSpacing: letterSpacing,
      height: height,
      fontFeatures: tabularFigures,
    );
  }

  // Body copy: DM Sans, like the web's --font.
  static TextStyle body({
    double fontSize = 14,
    FontWeight fontWeight = FontWeight.w400,
    Color color = ink,
    double? height,
    TextDecoration? decoration,
  }) {
    return GoogleFonts.dmSans(
      fontSize: fontSize,
      fontWeight: _clamp(fontWeight, FontWeight.w400, FontWeight.w700),
      color: color,
      height: height,
      decoration: decoration,
    );
  }

  // Small uppercase label, the web's `.eyebrow`. Use instead of mono for labels.
  static TextStyle eyebrow({
    double fontSize = 10,
    FontWeight fontWeight = FontWeight.w700,
    Color color = muted,
    double? letterSpacing,
  }) {
    return GoogleFonts.dmSans(
      fontSize: fontSize,
      fontWeight: _clamp(fontWeight, FontWeight.w400, FontWeight.w700),
      color: color,
      letterSpacing: letterSpacing ?? 1.6,
    );
  }

  // Monospace is reserved for account numbers and reference IDs.
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

  // The web's card: white, 1px hairline, barely-there shadow.
  static BoxDecoration glassCardDecoration({
    Color bg = glassCardBg,
    double radius = radiusLg,
    Color? borderColor,
  }) {
    return BoxDecoration(
      color: bg,
      borderRadius: BorderRadius.circular(radius),
      border: Border.all(color: borderColor ?? glassBorder),
      boxShadow: const [
        BoxShadow(color: Color(0x074C1937), blurRadius: 20, offset: Offset(0, 6)),
      ],
    );
  }

  // The web's `.balance-card`: flat wine, no heavy drop shadow.
  static BoxDecoration wineHeroDecoration({double radius = radiusLg}) {
    return BoxDecoration(
      color: wine,
      borderRadius: BorderRadius.circular(radius),
    );
  }
}

/// Faint concentric rings on the balance card, matching the web's `.balance-card:after`.
class ConcentricRingsPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withValues(alpha: 0.08)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;

    final center = Offset(size.width + 10, -10);
    for (final r in [145.0, 185.0, 226.0]) {
      canvas.drawCircle(center, r, paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
