import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';

/// Reusable Shimmer Loading Skeletons for Enterprise Mobile Apps.
class ShimmerSkeleton extends StatelessWidget {
  final double width;
  final double height;
  final double borderRadius;

  const ShimmerSkeleton({
    super.key,
    required this.width,
    required this.height,
    this.borderRadius = 12.0,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final baseColor = isDark ? const Color(0xFF2E1C2B) : const Color(0xFFE8DFE3);
    final highlightColor = isDark ? const Color(0xFF4A2A44) : const Color(0xFFF7F2F5);

    return Shimmer.fromColors(
      baseColor: baseColor,
      highlightColor: highlightColor,
      child: Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: baseColor,
          borderRadius: BorderRadius.circular(borderRadius),
        ),
      ),
    );
  }

  /// Shimmer skeleton for Account Cards
  static Widget accountCardSkeleton(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.only(bottom: 14.0),
      child: ShimmerSkeleton(
        width: double.infinity,
        height: 140,
        borderRadius: 20,
      ),
    );
  }

  /// Shimmer skeleton for Transaction List Tiles
  static Widget transactionItemSkeleton(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 10.0, horizontal: 14.0),
      child: Row(
        children: [
          ShimmerSkeleton(width: 40, height: 40, borderRadius: 20),
          SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ShimmerSkeleton(width: 140, height: 14),
                SizedBox(height: 6),
                ShimmerSkeleton(width: 90, height: 10),
              ],
            ),
          ),
          ShimmerSkeleton(width: 70, height: 16),
        ],
      ),
    );
  }
}
