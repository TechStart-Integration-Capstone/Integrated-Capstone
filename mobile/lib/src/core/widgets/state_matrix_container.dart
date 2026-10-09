import 'package:flutter/material.dart';
import 'package:paypink_mobile/theme/paypink_theme.dart';
import 'package:paypink_mobile/src/core/widgets/shimmer_skeleton.dart';

enum ViewStateStatus { loading, empty, populated, error }

/// Universal 4-State UI Container Matrix (Loading, Empty, Populated, Error)
class StateMatrixContainer extends StatelessWidget {
  final ViewStateStatus status;
  final Widget child;
  final Widget? loadingWidget;
  final String emptyMessage;
  final String errorMessage;
  final VoidCallback? onRetry;

  const StateMatrixContainer({
    super.key,
    required this.status,
    required this.child,
    this.loadingWidget,
    this.emptyMessage = 'No data available at this moment.',
    this.errorMessage = 'Network error occurred. Please check your connection.',
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    switch (status) {
      case ViewStateStatus.loading:
        return loadingWidget ??
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Column(
                children: List.generate(
                  3,
                  (_) => ShimmerSkeleton.transactionItemSkeleton(context),
                ),
              ),
            );

      case ViewStateStatus.empty:
        return Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 20),
          alignment: Alignment.center,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.inbox_outlined, size: 48, color: textMuted),
              const SizedBox(height: 12),
              Text(
                emptyMessage,
                textAlign: TextAlign.center,
                style: PayPinkTheme.body(fontSize: 13, color: textMuted),
              ),
            ],
          ),
        );

      case ViewStateStatus.error:
        return Container(
          width: double.infinity,
          margin: const EdgeInsets.symmetric(vertical: 12),
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF3B1818) : PayPinkTheme.redBg,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: PayPinkTheme.red.withValues(alpha: 0.3)),
          ),
          child: Column(
            children: [
              Row(
                children: [
                  const Icon(Icons.error_outline_rounded, color: PayPinkTheme.red, size: 22),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      errorMessage,
                      style: PayPinkTheme.body(
                        fontSize: 12,
                        color: isDark ? Colors.white : PayPinkTheme.red,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
              if (onRetry != null) ...[
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  height: 38,
                  child: ElevatedButton.icon(
                    onPressed: onRetry,
                    icon: const Icon(Icons.refresh_rounded, size: 16),
                    label: const Text('Try Again'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: PayPinkTheme.wine,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                      textStyle: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
              ],
            ],
          ),
        );

      case ViewStateStatus.populated:
        return child;
    }
  }
}
