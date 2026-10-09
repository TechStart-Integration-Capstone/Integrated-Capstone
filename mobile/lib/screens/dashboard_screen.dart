import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/bottom_sheets.dart';
import '../widgets/loan_payment_sheet.dart';
import '../widgets/loan_application_sheet.dart';
import 'transactions_screen.dart';
import '../services/account_service.dart';
import '../widgets/spending_chart.dart';
import '../widgets/account_card_carousel.dart';
import '../widgets/promo_banner.dart';
import '../services/statement_service.dart';

class DashboardScreen extends StatefulWidget {
  final bool hideBalances;
  final VoidCallback onToggleHideBalances;
  final Function(int) onNavigateTab;
  final List<TransactionItem> transactions;
  final String userName;
  final List<BankAccount>? accounts;
  final double? totalBalance;
  final Function(double amount, String fromAccount, String loanAccount)? onLoanPaymentSuccess;
  final String? userFullName;
  final Function(TransactionItem tx)? onReverseTransaction;
  final VoidCallback? onRefreshData;

  const DashboardScreen({
    super.key,
    required this.hideBalances,
    required this.onToggleHideBalances,
    required this.onNavigateTab,
    required this.transactions,
    this.userName = 'Customer',
    this.userFullName,
    this.accounts,
    this.totalBalance,
    this.onLoanPaymentSuccess,
    this.onReverseTransaction,
    this.onRefreshData,
  });

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  String _formatCurrency(double amount) {
    final parts = amount.toStringAsFixed(2).split('.');
    final integerPart = parts[0];
    final decimalPart = parts[1];
    final reg = RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))');
    final formattedInt = integerPart.replaceAllMapped(reg, (Match m) => '${m[1]},');
    return '$formattedInt.$decimalPart';
  }

  double get _totalMoneyIn {
    double total = 0.0;
    for (final tx in widget.transactions) {
      if (tx.isCredit && tx.status.toUpperCase() != 'REVERSED') {
        total += tx.amount;
      }
    }
    return total;
  }

  double get _totalMoneyOut {
    double total = 0.0;
    for (final tx in widget.transactions) {
      if (!tx.isCredit && tx.status.toUpperCase() != 'REVERSED') {
        total += tx.amount;
      }
    }
    return total;
  }

  String get _currentMonthYear {
    final now = DateTime.now();
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return '${months[now.month - 1]} ${now.year}';
  }

  BankAccount? get _liveLoanAccount {
    if (widget.accounts == null || widget.accounts!.isEmpty) return null;
    try {
      return widget.accounts!.firstWhere(
        (a) => a.accountType.toUpperCase().contains('LOAN') || a.displayName.toLowerCase().contains('loan'),
      );
    } catch (_) {
      return null;
    }
  }

  /// Real accounts and loans only; a customer without a loan gets no loan card.
  List<BankAccount> get _carouselAccounts => List<BankAccount>.from(widget.accounts ?? const <BankAccount>[]);

  void _openLoanPaymentSheet() {
    final loan = _liveLoanAccount;
    if (loan == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: PayPinkTheme.wine,
          content: const Text('You don’t have an active loan. Would you like to apply?'),
          action: SnackBarAction(
            label: 'Apply Now',
            textColor: Colors.white,
            onPressed: _openLoanApplicationSheet,
          ),
        ),
      );
      return;
    }
    LoanPaymentSheet.show(
      context,
      loanAccount: loan,
      accounts: widget.accounts ?? [],
      onPaymentSuccess: (amount, fundingAccount, newLoanBal) {
        widget.onLoanPaymentSuccess?.call(amount, fundingAccount.accountNumber, loan.accountNumber);
        widget.onRefreshData?.call();
      },
    );
  }

  void _openLoanApplicationSheet() {
    LoanApplicationSheet.show(
      context,
      accounts: widget.accounts ?? [],
      onLoanAccepted: () {
        widget.onRefreshData?.call();
        widget.onNavigateTab(0); // Refresh Overview tab
      },
    );
  }




  void _exportStatement() {
    final activeAccount = (widget.accounts != null && widget.accounts!.isNotEmpty)
        ? widget.accounts!.first
        : BankAccount(
            accountId: 1,
            accountNumber: '001396394080',
            accountType: 'CHECKING',
            currency: 'PHP',
            currentBalance: widget.totalBalance ?? 74950.00,
            status: 'ACTIVE',
          );

    final holderName = widget.userFullName?.isNotEmpty == true
        ? widget.userFullName!
        : (widget.userName.isNotEmpty ? widget.userName : 'PayPink Client');

    StatementService.generateAndExportStatement(
      context: context,
      customerName: holderName,
      accountNumber: activeAccount.formattedNumber,
      accountType: activeAccount.displayName,
      currentBalance: activeAccount.currentBalance,
      transactions: widget.transactions,
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Greeting matching mockup
          Text(
            'Hello, ${widget.userName}.',
            style: PayPinkTheme.display(
              fontSize: 26,
              fontWeight: FontWeight.w800,
              color: textInk,
              letterSpacing: -0.8,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            "Your everyday, at a glance. It's good to have you here.",
            style: PayPinkTheme.body(fontSize: 13, color: textMuted),
          ),
          const SizedBox(height: 12),

          // Hero Wine Balance Card with Concentric Ripple Rings
          Container(
            width: double.infinity,
            decoration: PayPinkTheme.wineHeroDecoration(),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(PayPinkTheme.radiusLg),
              child: Stack(
                children: [
                  Positioned.fill(
                    child: CustomPaint(painter: ConcentricRingsPainter()),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(22),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Row(
                              children: [
                                Text(
                                  'Total available balance',
                                  style: PayPinkTheme.body(
                                    color: PayPinkTheme.pink,
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                GestureDetector(
                                  onTap: widget.onToggleHideBalances,
                                  child: Icon(
                                    widget.hideBalances
                                        ? Icons.visibility_off_rounded
                                        : Icons.visibility_rounded,
                                    color: PayPinkTheme.pink,
                                    size: 16,
                                  ),
                                ),
                              ],
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                color: Colors.white.withValues(alpha: 0.18),
                                borderRadius: BorderRadius.circular(6),
                                border: Border.all(
                                  color: Colors.white.withValues(alpha: 0.25),
                                ),
                              ),
                              child: Text(
                                'PHP',
                                style: PayPinkTheme.display(
                                  color: Colors.white,
                                  fontSize: 10,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Builder(
                          builder: (context) {
                            final double effectiveBalance = widget.totalBalance ??
                                (widget.accounts != null && widget.accounts!.isNotEmpty
                                    ? widget.accounts!.fold<double>(0.0, (double sum, a) => sum + a.currentBalance)
                                    : 50.00);
                            final accountCount = (widget.accounts != null && widget.accounts!.isNotEmpty)
                                ? widget.accounts!.length
                                : 2;

                            return Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  widget.hideBalances
                                      ? '\u2022\u2022\u2022\u2022\u2022\u2022'
                                      : '\u20B1${_formatCurrency(effectiveBalance)}',
                                  style: PayPinkTheme.display(
                                    fontSize: 38,
                                    fontWeight: FontWeight.w600,
                                    color: Colors.white,
                                    letterSpacing: -1.2,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  'Across $accountCount account${accountCount == 1 ? '' : 's'}. All yours.',
                                  style: PayPinkTheme.body(
                                    color: const Color(0xFFE2B4CB),
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            );
                          },
                        ),
                        const SizedBox(height: 18),
                        Divider(color: Colors.white.withValues(alpha: 0.15), height: 1),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Flexible(
                              child: Text(
                                'Your money, in view.',
                                style: PayPinkTheme.body(
                                  color: PayPinkTheme.pink,
                                  fontSize: 11,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            const SizedBox(width: 8),
                            GestureDetector(
                              onTap: () => widget.onNavigateTab(1), // Go to accounts
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    'View all',
                                    style: PayPinkTheme.body(
                                      color: Colors.white,
                                      fontSize: 12,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  const SizedBox(width: 4),
                                  const Icon(
                                    Icons.arrow_forward_rounded,
                                    color: Colors.white,
                                    size: 14,
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 18),

          // Quick Action Circles matching design inspiration
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildQuickAction(
                context,
                icon: Icons.north_east_rounded,
                label: 'Send',
                onTap: () => widget.onNavigateTab(2), // Transfer tab
              ),
              _buildQuickAction(
                context,
                icon: Icons.credit_score_rounded,
                label: 'Pay Loan',
                onTap: _openLoanPaymentSheet,
              ),
              _buildQuickAction(
                context,
                icon: Icons.account_balance_wallet_rounded,
                label: 'Apply Loan',
                onTap: _openLoanApplicationSheet,
              ),
              _buildQuickAction(
                context,
                icon: Icons.receipt_long_rounded,
                label: 'Report',
                onTap: _exportStatement,
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Monthly Flow Card: "This month, so far" (Dynamic from live ledger)
          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'This month, so far',
                      style: PayPinkTheme.display(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: textInk,
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: isDark ? PayPinkTheme.darkCard : Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(6),
                        border: isDark ? Border.all(color: PayPinkTheme.darkGlassBorder) : null,
                      ),
                      child: Text(
                        _currentMonthYear,
                        style: PayPinkTheme.body(
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: textMuted,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: Row(
                        children: [
                          Container(
                            width: 38,
                            height: 38,
                            decoration: BoxDecoration(
                              color: isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg,
                              shape: BoxShape.circle,
                            ),
                            child: Icon(
                              Icons.arrow_downward_rounded,
                              color: isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green,
                              size: 18,
                            ),
                          ),
                          const SizedBox(width: 10),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Money in',
                                style: PayPinkTheme.body(
                                  fontSize: 11,
                                  color: textMuted,
                                ),
                              ),
                              Text(
                                widget.hideBalances
                                    ? '\u2022\u2022\u2022\u2022\u2022\u2022'
                                    : '\u20B1${_formatCurrency(_totalMoneyIn)}',
                                style: PayPinkTheme.display(
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
                                  color: textInk,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Row(
                        children: [
                          Container(
                            width: 38,
                            height: 38,
                            decoration: BoxDecoration(
                              color: isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle,
                              shape: BoxShape.circle,
                            ),
                            child: Icon(
                              Icons.arrow_upward_rounded,
                              color: isDark ? const Color(0xFFF6A4C0) : PayPinkTheme.wine,
                              size: 18,
                            ),
                          ),
                          const SizedBox(width: 10),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Money out',
                                style: PayPinkTheme.body(
                                  fontSize: 11,
                                  color: textMuted,
                                ),
                              ),
                              Text(
                                widget.hideBalances
                                    ? '\u2022\u2022\u2022\u2022\u2022\u2022'
                                    : '\u20B1${_formatCurrency(_totalMoneyOut)}',
                                style: PayPinkTheme.display(
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
                                  color: textInk,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Divider(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line, height: 1),
                const SizedBox(height: 8),
                Text(
                  widget.transactions.isEmpty
                      ? 'Based on your live account ledger.'
                      : 'Based on your latest ${widget.transactions.length} recorded transaction${widget.transactions.length == 1 ? '' : 's'}.',
                  style: PayPinkTheme.body(fontSize: 10, color: textMuted),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Your Accounts Section Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'Your accounts',
                    style: PayPinkTheme.display(
                      fontSize: 15.5,
                      fontWeight: FontWeight.w700,
                      color: textInk,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: isDark ? PayPinkTheme.darkCard : PayPinkTheme.pinkSubtle,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: isDark ? PayPinkTheme.darkGlassBorder : PayPinkTheme.pink),
                    ),
                    child: Text(
                      '${widget.accounts != null && widget.accounts!.isNotEmpty ? widget.accounts!.length : 2} linked',
                      style: PayPinkTheme.body(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                      ),
                    ),
                  ),
                ],
              ),
              GestureDetector(
                onTap: () => widget.onNavigateTab(1),
                child: Text(
                  'Manage \u2192',
                  style: PayPinkTheme.body(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Dynamic Swipeable Account Cards Carousel (hidden until real accounts load)
          if (_carouselAccounts.isNotEmpty)
          AccountCardCarousel(
            accounts: _carouselAccounts,
            cardHolder: widget.userFullName?.isNotEmpty == true ? widget.userFullName! : widget.userName,
            hideBalances: widget.hideBalances,
            onToggleHideBalances: widget.onToggleHideBalances,
            onNavigateTab: widget.onNavigateTab,
            onOpenLoanPayment: _openLoanPaymentSheet,
            onSelectAccount: (acct) {
              if (acct.isLoan) {
                PayPinkBottomSheets.showLoanDetails(context, loan: acct, onPay: _openLoanPaymentSheet);
                return;
              }
              final holder = widget.userFullName?.isNotEmpty == true ? widget.userFullName! : widget.userName;
              PayPinkBottomSheets.showAccountDetails(
                context,
                name: holder.isNotEmpty ? holder : acct.displayName,
                fullNumber: acct.formattedNumber,
                balance: acct.currentBalance,
                type: acct.accountType,
                status: acct.status,
                account: acct,
              );
            },
            isDark: isDark,
          ),

          const SizedBox(height: 20),

          // Recent activity (after accounts, as on the web overview)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Recent activity',
                style: PayPinkTheme.display(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: textInk,
                ),
              ),
              GestureDetector(
                onTap: () => widget.onNavigateTab(3), // Activity tab
                child: Text(
                  'View all \u2192',
                  style: PayPinkTheme.body(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Recent Activity Panel
          if (widget.transactions.isEmpty)
            GlassCard(
              padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
              child: Center(
                child: Column(
                  children: [
                    Icon(
                      Icons.receipt_long_outlined,
                      size: 32,
                      color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'No recent transactions yet',
                      style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700, color: textInk),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Your transfers and payments will reflect here automatically.',
                      style: PayPinkTheme.body(fontSize: 11, color: textMuted),
                    ),
                  ],
                ),
              ),
            )
          else
            GlassCard(
              padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
              child: ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: widget.transactions.take(5).length,
                separatorBuilder: (_, __) => Divider(color: textLine, height: 1),
                itemBuilder: (context, index) {
                  final tx = widget.transactions[index];
                  final isReversed = tx.status == 'REVERSED';
                  final isDlq = tx.status == 'FAILED_DLQ';

                  return Material(
                    color: Colors.transparent,
                    child: ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Container(
                        width: 36,
                        height: 36,
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
                        style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700, color: textInk).copyWith(
                          decoration: tx.status == 'REVERSED' ? TextDecoration.lineThrough : null,
                        ),
                      ),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${tx.account} · ${tx.date}',
                            style: PayPinkTheme.body(fontSize: 10, color: textMuted),
                          ),
                          if (tx.counterparty != null && tx.counterparty!.isNotEmpty) ...[
                            const SizedBox(height: 1),
                            Text(
                              tx.isCredit ? 'From: ${tx.counterparty}' : 'Recipient: ${tx.counterparty}',
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
                            '${tx.isCredit ? '+' : '-'}\u20B1${tx.amount.toStringAsFixed(2)}',
                            style: PayPinkTheme.display(
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                              color: isReversed
                                  ? textMuted
                                  : (tx.isCredit ? (isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green) : textInk),
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            tx.status == 'REVERSED'
                                ? '• Refunded'
                                : (tx.status == 'FAILED_DLQ' ? '• Processing' : 'Completed'),
                            style: PayPinkTheme.body(
                              fontSize: 10,
                              color: tx.status == 'REVERSED' ? PayPinkTheme.amber : (tx.status == 'FAILED_DLQ' ? PayPinkTheme.wine : textMuted),
                              fontWeight: tx.status == 'REVERSED' ? FontWeight.w700 : FontWeight.normal,
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
                        ofscore: tx.id,
                        auditHash: 'SEC-${tx.id}',
                        account: tx.account,
                        counterparty: tx.counterparty ?? tx.recipientAccount,
                        status: tx.status,
                        canReverse: false,
                        reversalMinutesRemaining: 0,
                        onReverse: null,
                      ),
                    ),
                  );
                },
              ),
            ),
          const SizedBox(height: 20),

          // Spending pattern card
          GlassCard(
            onTap: () => PayPinkBottomSheets.showHardwareVault(
              context,
              customerName: widget.userFullName ?? widget.userName,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Row(
                        children: [
                          const Icon(Icons.donut_large_rounded, color: PayPinkTheme.wine, size: 18),
                          const SizedBox(width: 8),
                          Flexible(
                            child: Text(
                              'Spending pattern',
                              style: PayPinkTheme.display(
                                fontSize: 14,
                                fontWeight: FontWeight.w700,
                                color: textInk,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                PayPinkSpendingChart(
                  isDark: isDark,
                  transactions: widget.transactions,
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Promotional banner: below the customer's own money and activity
          PromoBanner(
            isDark: isDark,
            onAction: () => widget.onNavigateTab(2),
          ),
          const SizedBox(height: 12),

          // Privacy Note Glass Card
          GlassCard(
            backgroundColor: isDark ? PayPinkTheme.darkPaper : PayPinkTheme.paper,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.shield_outlined, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine, size: 20),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'A little privacy goes a long way.',
                        style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700, color: textInk),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        'Keep your account details and password just for you. Masked by default for mobile safety.',
                        style: PayPinkTheme.body(fontSize: 11, color: textMuted, height: 1.4),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 90),
        ],
      ),
    );
  }

  Widget _buildQuickAction(
    BuildContext context, {
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final labelColor = isDark ? const Color(0xFFD1D5DB) : PayPinkTheme.ink;

    return GestureDetector(
      onTap: onTap,
      child: Column(
        children: [
          Container(
            width: 58,
            height: 58,
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1B2030) : PayPinkTheme.pinkSubtle,
              shape: BoxShape.circle,
              border: Border.all(
                color: isDark ? const Color(0xFF4A2338) : PayPinkTheme.pink,
                width: 1.2,
              ),
              boxShadow: [
                BoxShadow(
                  color: (isDark ? Colors.black : PayPinkTheme.wine).withValues(alpha: isDark ? 0.4 : 0.08),
                  blurRadius: 14,
                  offset: const Offset(0, 5),
                ),
              ],
            ),
            child: Icon(
              icon,
              color: isDark ? const Color(0xFFF6A4C0) : PayPinkTheme.wine,
              size: 22,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: PayPinkTheme.body(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: labelColor,
            ),
          ),
        ],
      ),
    );
  }
}


