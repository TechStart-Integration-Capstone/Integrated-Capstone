import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/bottom_sheets.dart';
import '../widgets/transaction_report_sheet.dart';
import '../services/account_service.dart';

class TransactionItem {
  final String id;
  final String title;
  final String date;
  final String account;
  final double amount;
  final bool isCredit;
  final String transactionType; // 'TRANSFER_OUT', 'TRANSFER_IN', 'LOAN_PAYMENT', 'DEPOSIT'
  final String? counterparty;
  final String status; // 'Completed', 'Processing', 'Refunded', 'Failed', 'REVERSED'
  final String ofscore;
  final DateTime? timestamp;
  final String? sourceAccount;
  final String? recipientAccount;

  TransactionItem({
    required this.id,
    required this.title,
    required this.date,
    required this.account,
    required this.amount,
    required this.isCredit,
    this.transactionType = 'TRANSFER_OUT',
    this.counterparty,
    this.status = 'Completed',
    String? ofscore,
    this.timestamp,
    this.sourceAccount,
    this.recipientAccount,
  }) : ofscore = ofscore ?? 'TXN-REF-$id';

  TransactionItem copyWith({
    String? id,
    String? title,
    String? date,
    String? account,
    double? amount,
    bool? isCredit,
    String? transactionType,
    String? counterparty,
    String? status,
    String? ofscore,
    DateTime? timestamp,
    String? sourceAccount,
    String? recipientAccount,
  }) {
    return TransactionItem(
      id: id ?? this.id,
      title: title ?? this.title,
      date: date ?? this.date,
      account: account ?? this.account,
      amount: amount ?? this.amount,
      isCredit: isCredit ?? this.isCredit,
      transactionType: transactionType ?? this.transactionType,
      counterparty: counterparty ?? this.counterparty,
      status: status ?? this.status,
      ofscore: ofscore ?? this.ofscore,
      timestamp: timestamp ?? this.timestamp,
      sourceAccount: sourceAccount ?? this.sourceAccount,
      recipientAccount: recipientAccount ?? this.recipientAccount,
    );
  }

  String get displayType {
    if (transactionType == 'LOAN_PAYMENT' || title.toLowerCase().contains('loan payment')) {
      return 'Loan Payment';
    }
    if (transactionType == 'DEPOSIT' || title.toLowerCase().contains('deposit') || title.toLowerCase().contains('welcome')) {
      return 'Deposit';
    }
    if (isCredit) {
      return 'Transfer In';
    }
    return 'Transfer Out';
  }

  bool get isLoanRelated =>
      account.toLowerCase().contains('loan') ||
      title.toLowerCase().contains('loan') ||
      transactionType == 'LOAN_PAYMENT';

  bool get isCheckingRelated =>
      account.toLowerCase().contains('checking') ||
      account.toLowerCase().contains('everyday') ||
      account.toLowerCase().contains('5046') ||
      account.toLowerCase().contains('3963') ||
      account.toLowerCase().contains('3812') ||
      (sourceAccount?.toLowerCase().contains('check') ?? false) ||
      (recipientAccount?.toLowerCase().contains('check') ?? false) ||
      title.toLowerCase().contains('checking');

  bool get isSavingsRelated =>
      account.toLowerCase().contains('savings') ||
      account.toLowerCase().contains('5968') ||
      account.toLowerCase().contains('1963') ||
      account.toLowerCase().contains('8123') ||
      account.toLowerCase().contains('8504') ||
      account.toLowerCase().contains('3469') ||
      (sourceAccount?.toLowerCase().contains('sav') ?? false) ||
      (recipientAccount?.toLowerCase().contains('sav') ?? false) ||
      title.toLowerCase().contains('savings');

  bool get isPayPinkRelated =>
      account.toLowerCase().contains('paypink') ||
      title.toLowerCase().contains('paypink') ||
      (counterparty?.toLowerCase().contains('paypink') ?? false) ||
      (recipientAccount?.toLowerCase().contains('paypink') ?? false) ||
      (!isLoanRelated && !account.toLowerCase().contains('bank'));
}

class TransactionsScreen extends StatefulWidget {
  final List<TransactionItem> transactions;
  final VoidCallback? onRefresh;
  final String? customerName;
  final String? primaryAccountNumber;
  final List<BankAccount> accounts;

  const TransactionsScreen({
    super.key,
    required this.transactions,
    this.onRefresh,
    this.customerName,
    this.primaryAccountNumber,
    this.accounts = const [],
  });

  @override
  State<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends State<TransactionsScreen> {
  String _searchQuery = '';
  String _filter = 'all'; // 'all', 'credit', 'debit', 'checking', 'savings', 'paypink', 'loan'



  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final brandWine = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;

    final seen = <String>{};
    var filtered = widget.transactions.where((tx) {
      if (_filter == 'credit' && !tx.isCredit) return false;
      if (_filter == 'debit' && tx.isCredit) return false;
      if (_filter == 'checking' && !tx.isCheckingRelated) return false;
      if (_filter == 'savings' && !tx.isSavingsRelated) return false;
      if (_filter == 'paypink' && !tx.isPayPinkRelated) return false;
      if (_filter == 'loan' && !tx.isLoanRelated) return false;

      if (_searchQuery.trim().isNotEmpty) {
        final q = _searchQuery.toLowerCase();
        return tx.title.toLowerCase().contains(q) ||
            tx.id.toLowerCase().contains(q) ||
            tx.account.toLowerCase().contains(q) ||
            (tx.counterparty?.toLowerCase().contains(q) ?? false);
      }
      return true;
    }).where((tx) {
      final key = '${tx.id}_${tx.isCredit}_${tx.amount.toStringAsFixed(2)}';
      return seen.add(key);
    }).toList();

    // Deduplicate optimistic client-side transactions if authoritative server transactions are present
    final hasAuthoritative = filtered.where((tx) => tx.id.startsWith('PP-') || int.tryParse(tx.id) != null).toList();
    if (hasAuthoritative.isNotEmpty) {
      filtered = filtered.where((tx) {
        final isOptimistic = tx.id.startsWith('TXN-') || tx.id.startsWith('TRF-') || tx.id.startsWith('TX-PH-') || tx.id.startsWith('LOAN-PAY-');
        if (isOptimistic) {
          final matchesServer = hasAuthoritative.any((srv) {
            final amtMatch = (srv.amount - tx.amount).abs() < 0.001;
            final credMatch = srv.isCredit == tx.isCredit;
            final timeMatch = tx.timestamp == null || srv.timestamp == null || srv.timestamp!.difference(tx.timestamp!).inMinutes.abs() < 15;
            return amtMatch && credMatch && timeMatch;
          });
          if (matchesServer) return false;
        }
        return true;
      }).toList();
    }

    return RefreshIndicator(
      color: PayPinkTheme.wine,
      onRefresh: () async {
        HapticFeedback.mediumImpact();
        widget.onRefresh?.call();
        await Future<void>.delayed(const Duration(milliseconds: 600));
      },
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
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
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    icon: Icon(Icons.picture_as_pdf_rounded, color: brandWine, size: 21),
                    tooltip: 'Generate transaction report',
                    onPressed: () => TransactionReportSheet.show(
                      context,
                      accounts: widget.accounts.where((a) => !a.isLoan).toList(),
                    ),
                  ),
                  if (widget.onRefresh != null)
                    IconButton(
                      icon: Icon(Icons.refresh_rounded, color: brandWine, size: 22),
                      tooltip: 'Sync via Gateway',
                      onPressed: widget.onRefresh,
                    ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Your latest 200 transactions, with a clearer view of where your money goes.',
            style: PayPinkTheme.body(fontSize: 13, color: textMuted),
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
                    hintText: 'Search transactions, reference, counterparty...',
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
                  physics: const BouncingScrollPhysics(),
                  child: Row(
                    children: [
                      _buildFilterChip('All', 'all', isDark),
                      const SizedBox(width: 8),
                      _buildFilterChip('Money in', 'credit', isDark),
                      const SizedBox(width: 8),
                      _buildFilterChip('Money out', 'debit', isDark),
                      const SizedBox(width: 8),
                      const SizedBox(width: 8),
                      _buildFilterChip('Checking', 'checking', isDark),
                      const SizedBox(width: 8),
                      _buildFilterChip('Savings', 'savings', isDark),
                      const SizedBox(width: 8),
                      _buildFilterChip('PayPink', 'paypink', isDark),
                      const SizedBox(width: 8),
                      _buildFilterChip('Loans', 'loan', isDark),
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
                                  ? (isDark ? const Color(0xFF382D16) : PayPinkTheme.amberBg)
                                  : (tx.isCredit
                                      ? (isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg)
                                      : (isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle))),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          isReversed
                              ? Icons.undo_rounded
                              : (isDlq
                                  ? Icons.hourglass_top_rounded
                                  : (tx.isCredit ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded)),
                          color: isReversed
                              ? PayPinkTheme.amber
                              : (isDlq
                                  ? PayPinkTheme.amber
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
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${tx.account} · ${tx.date}',
                            style: PayPinkTheme.body(fontSize: 11, color: textMuted),
                          ),
                          if (tx.counterparty != null && tx.counterparty!.isNotEmpty) ...[
                            const SizedBox(height: 1),
                            Text(
                              'Recipient: ${tx.counterparty}',
                              style: PayPinkTheme.body(
                                fontSize: 10,
                                color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ],
                      ),
                      trailing: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            '${tx.isCredit ? '+' : '-'}₱${tx.amount.toStringAsFixed(2)}',
                            style: PayPinkTheme.display(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: isReversed
                                  ? textMuted
                                  : (tx.isCredit
                                      ? (isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green)
                                      : textInk),
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            isReversed
                                ? '• Refunded'
                                : (isDlq ? '• Processing' : 'Completed'),
                            style: PayPinkTheme.body(
                              fontSize: 10,
                              color: isReversed
                                  ? PayPinkTheme.amber
                                  : (isDlq ? PayPinkTheme.wine : textMuted),
                              fontWeight: (isReversed || isDlq) ? FontWeight.w700 : FontWeight.normal,
                            ),
                          ),
                        ],
                      ),
                      onTap: () {
                        final cleanRef = tx.id.startsWith('TXN-')
                            ? tx.id
                            : (tx.id.startsWith('TRX-')
                                ? tx.id.replaceFirst('TRX-', 'TXN-')
                                : 'TXN-2026-${tx.id.replaceAll(RegExp(r'[^0-9]'), '').padRight(5, '0').substring(0, 5)}');
                        PayPinkBottomSheets.showTransactionDetails(
                          context,
                          name: tx.title,
                          refId: cleanRef,
                          date: tx.date,
                          amount: tx.amount,
                          isCredit: tx.isCredit,
                          ofscore: cleanRef,
                          auditHash: 'SEC-$cleanRef',
                          account: tx.account,
                          counterparty: tx.counterparty ?? tx.recipientAccount,
                          status: tx.status,
                        );
                      },
                    ),
                  );
                },
              ),
            ),
          const SizedBox(height: 90),
        ],
      ),
    ),
    );
  }

  Widget _buildFilterChip(String label, String value, bool isDark) {
    final isSelected = _filter == value;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        setState(() => _filter = value);
      },
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
