import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import '../theme/paypink_theme.dart';

class SpendingCategoryData {
  final String label;
  final double amount;
  final Color color;
  final IconData icon;

  SpendingCategoryData({
    required this.label,
    required this.amount,
    required this.color,
    required this.icon,
  });
}

class PayPinkSpendingChart extends StatefulWidget {
  final bool isDark;

  const PayPinkSpendingChart({
    super.key,
    required this.isDark,
  });

  @override
  State<PayPinkSpendingChart> createState() => _PayPinkSpendingChartState();
}

class _PayPinkSpendingChartState extends State<PayPinkSpendingChart> {
  int _touchedIndex = -1;

  final List<SpendingCategoryData> _categories = [
    SpendingCategoryData(
      label: 'Transfers',
      amount: 15400.00,
      color: const Color(0xFF6B1A3D),
      icon: Icons.swap_horiz_rounded,
    ),
    SpendingCategoryData(
      label: 'Bills & Utilities',
      amount: 6250.00,
      color: const Color(0xFF9E2C5E),
      icon: Icons.receipt_long_rounded,
    ),
    SpendingCategoryData(
      label: 'Shopping',
      amount: 4120.50,
      color: const Color(0xFFF6A4C0),
      icon: Icons.shopping_bag_outlined,
    ),
    SpendingCategoryData(
      label: 'Loan Payment',
      amount: 2150.00,
      color: const Color(0xFF381022),
      icon: Icons.credit_score_rounded,
    ),
  ];

  double get _totalSpend => _categories.fold<double>(0.0, (sum, c) => sum + c.amount);

  @override
  Widget build(BuildContext context) {
    final isDark = widget.isDark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return Column(
      children: [
        SizedBox(
          height: 160,
          child: Stack(
            alignment: Alignment.center,
            children: [
              PieChart(
                PieChartData(
                  pieTouchData: PieTouchData(
                    touchCallback: (FlTouchEvent event, pieTouchResponse) {
                      setState(() {
                        if (!event.isInterestedForInteractions ||
                            pieTouchResponse == null ||
                            pieTouchResponse.touchedSection == null) {
                          _touchedIndex = -1;
                          return;
                        }
                        _touchedIndex = pieTouchResponse.touchedSection!.touchedSectionIndex;
                      });
                    },
                  ),
                  borderData: FlBorderData(show: false),
                  sectionsSpace: 3,
                  centerSpaceRadius: 46,
                  sections: _categories.asMap().entries.map((entry) {
                    final idx = entry.key;
                    final item = entry.value;
                    final isTouched = idx == _touchedIndex;
                    final radius = isTouched ? 28.0 : 22.0;

                    return PieChartSectionData(
                      color: item.color,
                      value: item.amount,
                      title: '',
                      radius: radius,
                      badgeWidget: null,
                    );
                  }).toList(),
                ),
                duration: const Duration(milliseconds: 300),
                curve: Curves.easeInOut,
              ),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    _touchedIndex >= 0 && _touchedIndex < _categories.length
                        ? _categories[_touchedIndex].label
                        : 'Total Outflow',
                    style: PayPinkTheme.body(
                      fontSize: 9.5,
                      fontWeight: FontWeight.w600,
                      color: textMuted,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    _touchedIndex >= 0 && _touchedIndex < _categories.length
                        ? '\u20B1${_categories[_touchedIndex].amount.toStringAsFixed(0)}'
                        : '\u20B1${_totalSpend.toStringAsFixed(0)}',
                    style: PayPinkTheme.display(
                      fontSize: 14,
                      fontWeight: FontWeight.w800,
                      color: textInk,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 8,
          alignment: WrapAlignment.center,
          children: _categories.map((cat) {
            final percentage = ((cat.amount / _totalSpend) * 100).toStringAsFixed(0);
            return Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: cat.color,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 5),
                Text(
                  '${cat.label} ($percentage%)',
                  style: PayPinkTheme.body(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: textMuted,
                  ),
                ),
              ],
            );
          }).toList(),
        ),
      ],
    );
  }
}
