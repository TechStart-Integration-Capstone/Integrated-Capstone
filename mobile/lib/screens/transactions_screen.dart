import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/bottom_sheets.dart';

class TransactionItem {
  final String id;
  final String title;
  final String date;
  final String account;
  final double amount;
  final bool isCredit;
  final String ofscore;
  final String status; // 'COMPLETED', 'REVERSED', 'FAILED_DLQ'

  TransactionItem({
    required this.id,
    required this.title,
    required this.date,
    required this.account,
    required this.amount,
    required this.isCredit,
    required this.ofscore,
    this.status = 'COMPLETED',
  });
}

class TransactionsScreen extends StatefulWidget {
  final List<TransactionItem> transactions;

  const TransactionsScreen({super.key, required this.transactions});

  @override
  State<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends State<TransactionsScreen> {
  String _searchQuery = '';
  String _filter = 'all'; // 'all', 'credit', 'debit', 'reversal'

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final brandWine = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;

    var filtered = widget.transactions.where((tx) {
      if (_filter == 'credit' && !tx.isCredit) return false;
      if (_filter == 'debit' && tx.isCredit) return false;
      if (_filter == 'reversal' && tx.status != 'REVERSED' && tx.status != 'FAILED_DLQ') return false;

      if (_searchQuery.trim().isNotEmpty) {
        final q = _searchQuery.toLowerCase();
        return tx.title.toLowerCase().contains(q) ||
            tx.id.toLowerCase().contains(q) ||
            tx.account.toLowerCase().contains(q);
      }
      return true;
    }).toList();

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Every little detail.',
            style: PayPinkTheme.display(
              fontSize: 26,
              fontWeight: FontWeight.w800,
              color: textInk,
              letterSpacing: -0.8,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Your latest 200 transactions, with a clearer view of where your money goes.',
            style: PayPinkTheme.body(fontSize: 12.5, color: textMuted),
          ),
          const SizedBox(height: 18),

          // Search and Filter Card
          GlassCard(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                TextField(
                  onChanged: (v) => setState(() => _searchQuery = v),
                  style: PayPinkTheme.body(fontSize: 13, color: textInk),
                  decoration: InputDecoration(
                    hintText: 'Search transactions or reference...',
                    hintStyle: PayPinkTheme.body(fontSize: 12, color: textMuted),
                    prefixIcon: Icon(Icons.search_rounded, size: 20, color: textMuted),
                    filled: true,
                    fillColor: isDark ? PayPinkTheme.darkCard : Colors.white,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(color: textLine),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(color: textLine),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(color: brandWine),
                    ),
                    contentPadding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
                  ),
                ),
                const SizedBox(height: 10),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _buildFilterChip('All', 'all', isDark),
                      const SizedBox(width: 8),
                      _buildFilterChip('Money in', 'credit', isDark),
                      const SizedBox(width: 8),
                      _buildFilterChip('Money out', 'debit', isDark),
                      const SizedBox(width: 8),
                      _buildFilterChip('Reversals', 'reversal', isDark),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // Transaction Ledger List
          if (filtered.isEmpty)
            Container(
              padding: const EdgeInsets.symmetric(vertical: 40),
              alignment: Alignment.center,
              child: Text(
                'No transactions match your search.',
                style: PayPinkTheme.body(fontSize: 13, color: textMuted),
              ),
            )
          else
            GlassCard(
              padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
              child: ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: filtered.length,
                separatorBuilder: (_, __) => Divider(color: textLine, height: 1),
                itemBuilder: (context, index) {
                  final tx = filtered[index];
                  final isReversed = tx.status == 'REVERSED';
                  final isDlq = tx.status == 'FAILED_DLQ';

                  return Material(
                    color: Colors.transparent,
                    child: ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: isReversed
                              ? (isDark ? const Color(0xFF382D16) : PayPinkTheme.amberBg)
                              : (isDlq
                                  ? (isDark ? const Color(0xFF3B1818) : PayPinkTheme.redBg)
                                  : (tx.isCredit
                                      ? (isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg)
                                      : (isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle))),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          isReversed
                              ? Icons.undo_rounded
                              : (isDlq
                                  ? Icons.sync_problem_rounded
                                  : (tx.isCredit ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded)),
                          color: isReversed
                              ? PayPinkTheme.amber
                              : (isDlq
                                  ? PayPinkTheme.red
                                  : (tx.isCredit
                                      ? (isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green)
                                      : (isDark ? const Color(0xFFF6A4C0) : PayPinkTheme.wine))),
                          size: 16,
                        ),
                      ),
                      title: Text(
                        tx.title,
                        style: PayPinkTheme.display(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: textInk,
                        ).copyWith(decoration: isReversed ? TextDecoration.lineThrough : null),
                      ),
                      subtitle: Text(
                        '${tx.account} · ${tx.date}',
                        style: PayPinkTheme.body(fontSize: 10.5, color: textMuted),
                      ),
                      trailing: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            '${tx.isCredit ? '+' : '-'}₱${tx.amount.toStringAsFixed(2)}',
                            style: PayPinkTheme.display(
                              fontSize: 13.5,
                              fontWeight: FontWeight.w700,
                              color: isReversed
                                  ? textMuted
                                  : (tx.isCredit
                                      ? (isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green)
                                      : textInk),
                            ),
                          ),
                          Text(
                            isReversed
                                ? '• Reversed'
                                : (isDlq ? '• DLQ Retrying' : 'Completed'),
                            style: PayPinkTheme.body(
                              fontSize: 9.5,
                              color: isReversed
                                  ? PayPinkTheme.amber
                                  : (isDlq ? PayPinkTheme.red : textMuted),
                              fontWeight: (isReversed || isDlq) ? FontWeight.w700 : FontWeight.normal,
                            ),
                          ),
                        ],
                      ),
                      onTap: () => PayPinkBottomSheets.showTransactionDetails(
                        context,
                        name: tx.title,
                        refId: tx.id,
                        date: tx.date,
                        amount: tx.amount,
                        isCredit: tx.isCredit,
                        ofscore: tx.ofscore,
                        auditHash: 'pg-audit-sha256-${tx.id.toLowerCase()}',
                      ),
                    ),
                  );
                },
              ),
            ),
          const SizedBox(height: 90),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, String value, bool isDark) {
    final isSelected = _filter == value;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return GestureDetector(
      onTap: () => setState(() => _filter = value),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected
              ? (isDark ? PayPinkTheme.wineLight : PayPinkTheme.wine)
              : (isDark ? PayPinkTheme.darkCard : Colors.white),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isSelected
                ? (isDark ? PayPinkTheme.pink : PayPinkTheme.wine)
                : textLine,
          ),
        ),
        child: Text(
          label,
          style: PayPinkTheme.body(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: isSelected ? Colors.white : textMuted,
          ),
        ),
      ),
    );
  }
}
