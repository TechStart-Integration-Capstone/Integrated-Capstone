import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import '../theme/paypink_theme.dart';

import '../screens/transactions_screen.dart';

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
  final List<TransactionItem> transactions;

  const PayPinkSpendingChart({
    super.key,
    required this.isDark,
    this.transactions = const [],
  });

  @override
  State<PayPinkSpendingChart> createState() => _PayPinkSpendingChartState();
}

class _PayPinkSpendingChartState extends State<PayPinkSpendingChart> {
  int _touchedIndex = -1;

  List<SpendingCategoryData> get _categories {
    final Map<String, double> totals = {};
    final Map<String, Color> colors = {
      'Transfers': const Color(0xFF6B1A3D),
      'Bills & Utilities': const Color(0xFF9E2C5E),
      'Shopping': const Color(0xFFF6A4C0),
      'Loan Payment': const Color(0xFF381022),
      'Everyday Checking': const Color(0xFF8B264E),
    };
    final Map<String, IconData> icons = {
      'Transfers': Icons.swap_horiz_rounded,
      'Bills & Utilities': Icons.receipt_long_rounded,
      'Shopping': Icons.shopping_bag_outlined,
      'Loan Payment': Icons.credit_score_rounded,
      'Everyday Checking': Icons.account_balance_wallet_rounded,
    };

    for (final tx in widget.transactions) {
      if (tx.isCredit) continue; // Outflow spending only
      final type = tx.transactionType.toUpperCase();
      final title = tx.title.toLowerCase();

      String category = 'Transfers';
      if (type.contains('LOAN') || title.contains('loan')) {
        category = 'Loan Payment';
      } else if (title.contains('bill') || title.contains('utility') || title.contains('electric') || title.contains('water')) {
        category = 'Bills & Utilities';
      } else if (title.contains('shop') || title.contains('store') || title.contains('mall')) {
        category = 'Shopping';
      } else if (type.contains('TRANSFER') || type.contains('REMITTANCE') || title.contains('transfer') || title.contains('sent')) {
        category = 'Transfers';
      } else {
        category = 'Everyday Checking';
      }

      totals[category] = (totals[category] ?? 0.0) + tx.amount.abs();
    }

    final list = <SpendingCategoryData>[];
    totals.forEach((cat, amt) {
      if (amt > 0) {
        list.add(SpendingCategoryData(
          label: cat,
          amount: amt,
          color: colors[cat] ?? const Color(0xFF6B1A3D),
          icon: icons[cat] ?? Icons.shopping_bag_outlined,
        ));
      }
    });

    list.sort((a, b) => b.amount.compareTo(a.amount));
    return list;
  }

  double get _totalSpend => _categories.fold<double>(0.0, (sum, c) => sum + c.amount);

  @override
  Widget build(BuildContext context) {
    final isDark = widget.isDark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    if (_categories.isEmpty || _totalSpend <= 0) {
      return Container(
        padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
        alignment: Alignment.center,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.pie_chart_outline_rounded, size: 36, color: textMuted.withValues(alpha: 0.5)),
            const SizedBox(height: 8),
            Text(
              'No Outflow Spending Yet',
              style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700, color: textInk),
            ),
            const SizedBox(height: 4),
            Text(
              'Your outgoing funds transfers and payments will automatically appear here.',
              textAlign: TextAlign.center,
              style: PayPinkTheme.body(fontSize: 11, color: textMuted),
            ),
          ],
        ),
      );
    }

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
                      fontSize: 10,
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
