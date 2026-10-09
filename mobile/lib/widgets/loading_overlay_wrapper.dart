import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import 'paypink_logo.dart';

/// Reusable enterprise loading overlay that absorbs all UI touches and displays
/// a glassmorphism loading barrier during in-flight async operations.
class LoadingOverlayWrapper extends StatelessWidget {
  final Widget child;
  final bool isLoading;
  final String? loadingText;
  final String? subText;
  final Color? barrierColor;

  const LoadingOverlayWrapper({
    super.key,
    required this.child,
    required this.isLoading,
    this.loadingText,
    this.subText,
    this.barrierColor,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final cardBg = isDark ? PayPinkTheme.darkCard.withOpacity(0.92) : Colors.white.withOpacity(0.95);
    final borderColor = isDark ? PayPinkTheme.darkGlassBorder : PayPinkTheme.pink.withOpacity(0.4);

    return Stack(
      children: [
        // Touch-absorbing wrapper over underlying UI
        AbsorbPointer(
          absorbing: isLoading,
          child: child,
        ),

        // Glassmorphism Loading Barrier Overlay
        if (isLoading)
          Positioned.fill(
            child: BackdropFilter(
              filter: ui.ImageFilter.blur(sigmaX: 4, sigmaY: 4),
              child: Container(
                color: barrierColor ?? (isDark ? Colors.black.withOpacity(0.65) : Colors.black.withOpacity(0.45)),
                child: Center(
                  child: Container(
                    margin: const EdgeInsets.symmetric(horizontal: 28),
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
                    decoration: BoxDecoration(
                      color: cardBg,
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(color: borderColor, width: 1.2),
                      boxShadow: [
                        BoxShadow(
                          color: PayPinkTheme.wine.withOpacity(0.18),
                          blurRadius: 24,
                          spreadRadius: 2,
                          offset: const Offset(0, 8),
                        ),
                      ],
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Stacked Brand Logo with Centered Loading Indicator
                        Stack(
                          alignment: Alignment.center,
                          children: [
                            SizedBox(
                              width: 64,
                              height: 64,
                              child: CircularProgressIndicator(
                                strokeWidth: 3.5,
                                valueColor: AlwaysStoppedAnimation<Color>(
                                  isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                                ),
                                backgroundColor: isDark
                                    ? PayPinkTheme.pink.withOpacity(0.15)
                                    : PayPinkTheme.pink.withOpacity(0.3),
                              ),
                            ),
                            const PayPinkLogo(
                              size: 26,
                              showWordmark: false,
                            ),
                          ],
                        ),
                        const SizedBox(height: 20),
                        Text(
                          loadingText ?? 'Processing Transaction...',
                          textAlign: TextAlign.center,
                          style: PayPinkTheme.display(
                            fontSize: 17,
                            fontWeight: FontWeight.w700,
                            color: textInk,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          subText ?? 'Please hold on while we secure your request with core banking.',
                          textAlign: TextAlign.center,
                          style: PayPinkTheme.body(
                            fontSize: 13,
                            color: textMuted,
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}
