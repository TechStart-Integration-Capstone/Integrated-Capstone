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

  TransactionItem({
    required this.id,
    required this.title,
    required this.date,
    required this.account,
    required this.amount,
    required this.isCredit,
    required this.ofscore,
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
  String _filter = 'all'; // 'all', 'credit', 'debit'

  @override
  Widget build(BuildContext context) {
    var filtered = widget.transactions.where((tx) {
      if (_filter == 'credit' && !tx.isCredit) return false;
      if (_filter == 'debit' && tx.isCredit) return false;

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
              color: PayPinkTheme.ink,
              letterSpacing: -0.8,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Your latest 200 transactions, with a clearer view of where your money goes.',
            style: PayPinkTheme.body(fontSize: 12.5, color: PayPinkTheme.muted),
          ),
          const SizedBox(height: 18),

          // Search and Filter Card
          GlassCard(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                TextField(
                  onChanged: (v) => setState(() => _searchQuery = v),
                  decoration: InputDecoration(
                    hintText: 'Search transactions or reference...',
                    hintStyle: PayPinkTheme.body(fontSize: 12, color: PayPinkTheme.muted),
                    prefixIcon: const Icon(Icons.search_rounded, size: 20, color: PayPinkTheme.muted),
                    filled: true,
                    fillColor: Colors.white,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(color: PayPinkTheme.line),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(color: PayPinkTheme.line),
                    ),
                    contentPadding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    _buildFilterChip('All', 'all'),
                    const SizedBox(width: 8),
                    _buildFilterChip('Money in', 'credit'),
                    const SizedBox(width: 8),
                    _buildFilterChip('Money out', 'debit'),
                  ],
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
                style: PayPinkTheme.body(fontSize: 13, color: PayPinkTheme.muted),
              ),
            )
          else
            GlassCard(
              padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
              child: ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: filtered.length,
                separatorBuilder: (_, __) => const Divider(color: PayPinkTheme.line, height: 1),
                itemBuilder: (context, index) {
                  final tx = filtered[index];
                  return Material(
                    color: Colors.transparent,
                    child: ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: tx.isCredit ? PayPinkTheme.greenBg : PayPinkTheme.pinkSubtle,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          tx.isCredit ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded,
                          color: tx.isCredit ? PayPinkTheme.green : PayPinkTheme.wine,
                          size: 16,
                        ),
                      ),
                      title: Text(
                        tx.title,
                        style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700),
                      ),
                      subtitle: Text(
                        '${tx.account} · ${tx.date}',
                        style: PayPinkTheme.body(fontSize: 10.5, color: PayPinkTheme.muted),
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
                              color: tx.isCredit ? PayPinkTheme.green : PayPinkTheme.ink,
                            ),
                          ),
                          Text(
                            'Completed',
                            style: PayPinkTheme.body(fontSize: 9.5, color: PayPinkTheme.muted),
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
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, String value) {
    final isSelected = _filter == value;
    return GestureDetector(
      onTap: () => setState(() => _filter = value),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected ? PayPinkTheme.wine : Colors.white,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isSelected ? PayPinkTheme.wine : PayPinkTheme.line,
          ),
        ),
        child: Text(
          label,
          style: PayPinkTheme.body(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: isSelected ? Colors.white : PayPinkTheme.muted,
          ),
        ),
      ),
    );
  }
}
