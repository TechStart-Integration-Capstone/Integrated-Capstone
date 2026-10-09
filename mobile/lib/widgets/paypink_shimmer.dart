import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';

/// Luxury Shimmer Skeleton Loaders tailored to PayPink brand colors.
class PayPinkShimmer extends StatelessWidget {
  final Widget child;
  final bool isDark;

  const PayPinkShimmer({
    super.key,
    required this.child,
    this.isDark = false,
  });

  @override
  Widget build(BuildContext context) {
    return Shimmer.fromColors(
      baseColor: isDark ? const Color(0xFF280F1E) : const Color(0xFFF3E7EC),
      highlightColor: isDark ? const Color(0xFF481830) : const Color(0xFFFDF2F6),
      period: const Duration(milliseconds: 1500),
      child: child,
    );
  }

  /// Skeleton placeholder for the primary account / balance card
  static Widget accountCardSkeleton({required bool isDark}) {
    final blockColor = isDark ? Colors.white.withValues(alpha: 0.08) : Colors.white;

    return PayPinkShimmer(
      isDark: isDark,
      child: Container(
        height: 180,
        margin: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: blockColor,
          borderRadius: BorderRadius.circular(24),
        ),
      ),
    );
  }

  /// Skeleton placeholder for transaction history rows
  static Widget transactionSkeletonList({required bool isDark, int count = 4}) {
    final blockColor = isDark ? Colors.white.withValues(alpha: 0.08) : Colors.white;

    return PayPinkShimmer(
      isDark: isDark,
      child: Column(
        children: List.generate(count, (index) {
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: blockColor,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 140,
                        height: 12,
                        decoration: BoxDecoration(
                          color: blockColor,
                          borderRadius: BorderRadius.circular(6),
                        ),
                      ),
                      const SizedBox(height: 6),
                      Container(
                        width: 80,
                        height: 10,
                        decoration: BoxDecoration(
                          color: blockColor,
                          borderRadius: BorderRadius.circular(5),
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  width: 70,
                  height: 14,
                  decoration: BoxDecoration(
                    color: blockColor,
                    borderRadius: BorderRadius.circular(6),
                  ),
                ),
              ],
            ),
          );
        }),
      ),
    );
  }
}
