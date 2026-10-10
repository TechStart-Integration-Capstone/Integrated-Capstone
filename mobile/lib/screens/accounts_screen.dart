import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/bottom_sheets.dart';
import '../widgets/dynamic_card_deck.dart';
import '../widgets/loan_payment_sheet.dart';
import '../services/account_service.dart';
import '../widgets/paypink_shimmer.dart';

class AccountsScreen extends StatefulWidget {
  final bool hideBalances;
  final VoidCallback onToggleHideBalances;
  final Function(int)? onNavigateTab;
  /// Null while the first load is in progress.
  final List<BankAccount>? accounts;
  final String? userName;
  /// Set when accounts could not be loaded; the screen shows it with a Retry button.
  final String? loadError;
  final VoidCallback? onRetry;
  final Function(double amount, String fromAccount, String loanAccount)? onLoanPaymentSuccess;

  const AccountsScreen({
    super.key,
    required this.hideBalances,
    required this.onToggleHideBalances,
    this.onNavigateTab,
    this.accounts,
    this.userName,
    this.loadError,
    this.onRetry,
    this.onLoanPaymentSuccess,
  });

  @override
  State<AccountsScreen> createState() => _AccountsScreenState();
}

class _AccountsScreenState extends State<AccountsScreen> {
  final Set<String> _unmaskedAccountNumbers = {};

  void _openLoanPayment(BankAccount loan) {
    LoanPaymentSheet.show(
      context,
      loanAccount: loan,
      accounts: widget.accounts ?? [],
      onPaymentSuccess: (amount, fundingAccount, _) {
        widget.onLoanPaymentSuccess?.call(amount, fundingAccount.accountNumber, loan.accountNumber);
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final accounts = widget.accounts ?? const <BankAccount>[];
    final deposits = accounts.where((a) => !a.isLoan).toList();
    final loans = accounts.where((a) => a.isLoan).toList();
    final hasLiveAccounts = accounts.isNotEmpty;
    final totalLinked = accounts.length;

    return RefreshIndicator(
      color: PayPinkTheme.wine,
      onRefresh: () async {
        HapticFeedback.mediumImpact();
        widget.onRetry?.call();
        await Future<void>.delayed(const Duration(milliseconds: 600));
      },
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'A home for your money.',
            style: PayPinkTheme.display(
              fontSize: 26,
              fontWeight: FontWeight.w800,
              color: textInk,
              letterSpacing: -0.8,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Your accounts, together. Select an account to see its details.',
            style: PayPinkTheme.body(fontSize: 13, color: textMuted),
          ),
          const SizedBox(height: 16),

          // 3D Physical Cards Deck with Specular Sheen & Depth Tilt (real deposit accounts only)
          if (deposits.isNotEmpty) ...[
            DynamicCardDeck(
              accounts: deposits,
              cardHolder: widget.userName?.isNotEmpty == true ? widget.userName! : 'PayPink Client',
              hideBalances: widget.hideBalances,
              onToggleHideBalances: widget.onToggleHideBalances,
              onOpenTransfer: () => widget.onNavigateTab?.call(2),
              onOpenDetails: () {
                final acct = deposits.first;
                final holder = widget.userName?.isNotEmpty == true ? widget.userName! : acct.displayName;
                PayPinkBottomSheets.showAccountDetails(
                  context,
                  name: holder,
                  fullNumber: acct.formattedNumber,
                  balance: acct.currentBalance,
                  type: acct.accountType,
                  status: acct.status,
                  account: acct,
                );
              },
              isDark: isDark,
            ),
            const SizedBox(height: 24),
          ],

          // Header with Hide balances toggle
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Text(
                    'My accounts',
                    style: PayPinkTheme.display(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: textInk,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                      color: isDark ? PayPinkTheme.darkCard : PayPinkTheme.pinkSubtle,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: isDark ? PayPinkTheme.darkGlassBorder : PayPinkTheme.pink),
                    ),
                    child: Text(
                      '$totalLinked linked',
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
                onTap: widget.onToggleHideBalances,
                child: Row(
                  children: [
                    Icon(
                      widget.hideBalances
                          ? Icons.visibility_off_rounded
                          : Icons.visibility_rounded,
                      size: 14,
                      color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                    ),
                    const SizedBox(width: 5),
                    Text(
                      widget.hideBalances ? 'Show balances' : 'Hide balances',
                      style: PayPinkTheme.body(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Live accounts only; never placeholder balances.
          if (!hasLiveAccounts)
            _buildStatusCard(context)
          else ...[
            ...deposits.map((account) {
              final isMasked = !_unmaskedAccountNumbers.contains(account.accountNumber);
              final isSavings = account.isSavings;
              final isChecking = account.accountType.contains('CHECKING');
              final isEveryday = account.accountType.contains('EVERYDAY');

              final IconData icon = isSavings
                  ? Icons.savings_rounded
                  : (isChecking
                      ? Icons.business_center_rounded
                      : (isEveryday ? Icons.account_balance_wallet_rounded : Icons.credit_card_rounded));

              final Color iconColor = isSavings
                  ? PayPinkTheme.wine
                  : (isChecking
                      ? const Color(0xFFE11D48)
                      : (isEveryday ? PayPinkTheme.green : PayPinkTheme.amber));

              final Color iconBg = isSavings
                  ? PayPinkTheme.pinkSubtle
                  : (isChecking
                      ? const Color(0xFFFFF1F2)
                      : (isEveryday ? PayPinkTheme.greenBg : PayPinkTheme.amberBg));

              return Padding(
                padding: const EdgeInsets.only(bottom: 14.0),
                child: _buildAccountFullCard(
                  context,
                  account: account,
                  name: account.displayName,
                  maskedNumber: isMasked ? account.maskedNumber : account.formattedNumber,
                  fullNumber: account.formattedNumber,
                  isMasked: isMasked,
                  onToggleMask: () => setState(() {
                    if (!_unmaskedAccountNumbers.remove(account.accountNumber)) {
                      _unmaskedAccountNumbers.add(account.accountNumber);
                    }
                  }),
                  icon: icon,
                  iconColor: iconColor,
                  iconBg: iconBg,
                ),
              );
            }),
            ...loans.map((loan) {
              final isMasked = !_unmaskedAccountNumbers.contains(loan.accountNumber);
              return Padding(
                padding: const EdgeInsets.only(bottom: 14.0),
                child: _buildLoanCard(
                  context,
                  loan: loan,
                  isMasked: isMasked,
                  onToggleMask: () => setState(() {
                    if (!_unmaskedAccountNumbers.remove(loan.accountNumber)) {
                      _unmaskedAccountNumbers.add(loan.accountNumber);
                    }
                  }),
                ),
              );
            }),
          ],
          const SizedBox(height: 20),

          // Discretion note card matching mockup
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
                        'A little discretion, built in.',
                        style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700, color: textInk),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        'Your account numbers are masked by default. Tap the eye icon to reveal them, or copy the number.',
                        style: PayPinkTheme.body(
                          fontSize: 11,
                          color: textMuted,
                          height: 1.4,
                        ),
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
    ),
    );
  }

  /// Loading, load-failure, or no-accounts card shown instead of placeholder accounts.
  Widget _buildStatusCard(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final brandWine = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;

    if (widget.accounts == null && widget.loadError == null) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          children: [
            PayPinkShimmer.accountCardSkeleton(isDark: isDark),
            const SizedBox(height: 12),
            PayPinkShimmer.transactionSkeletonList(isDark: isDark, count: 2),
          ],
        ),
      );
    }

    final failed = widget.loadError != null;
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(failed ? Icons.cloud_off_rounded : Icons.account_balance_outlined, color: brandWine, size: 22),
          const SizedBox(height: 10),
          Text(
            failed ? 'We couldn’t load your accounts' : 'No accounts yet',
            style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700, color: textInk),
          ),
          const SizedBox(height: 4),
          Text(
            failed ? widget.loadError! : 'No accounts are linked to this profile yet.',
            style: PayPinkTheme.body(fontSize: 12, color: textMuted, height: 1.4),
          ),
          if (widget.onRetry != null) ...[
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: widget.onRetry,
              icon: Icon(Icons.refresh_rounded, size: 16, color: brandWine),
              label: Text('Try again', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w700, color: brandWine)),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildAccountFullCard(
    BuildContext context, {
    required BankAccount account,
    required String name,
    required String maskedNumber,
    required String fullNumber,
    required bool isMasked,
    required VoidCallback onToggleMask,
    required IconData icon,
    required Color iconColor,
    required Color iconBg,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final brandWine = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;
    final balance = account.currentBalance;
    final interestRate = account.annualInterestRatePercent;
    final status = account.status.toLowerCase() == 'active' ? 'Active' : account.status;

    return GlassCard(
      onTap: () {
        final holder = widget.userName?.isNotEmpty == true ? widget.userName! : name;
        PayPinkBottomSheets.showAccountDetails(
          context,
          name: holder,
          fullNumber: fullNumber,
          balance: balance,
          type: account.accountType,
          status: status,
          account: account,
        );
      },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 38,
                    height: 38,
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1E2638) : iconBg,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(icon, color: isDark ? PayPinkTheme.pink : iconColor, size: 18),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        name,
                        style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700, color: textInk),
                      ),
                      const SizedBox(height: 2),
                      GestureDetector(
                        onTap: onToggleMask,
                        child: Row(
                          children: [
                            Text(
                              maskedNumber,
                              style: PayPinkTheme.mono(fontSize: 10, color: textMuted),
                            ),
                            const SizedBox(width: 4),
                            Icon(
                              isMasked ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                              size: 12,
                              color: textMuted,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '• $status',
                  style: PayPinkTheme.body(
                    fontSize: 10,
                    color: isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            widget.hideBalances ? '••••••' : formatPeso(balance),
            style: PayPinkTheme.display(
              fontSize: 28,
              fontWeight: FontWeight.w800,
              color: textInk,
              letterSpacing: -0.6,
            ),
          ),
          const SizedBox(height: 10),
          // Interest pill: real savings tier, or "No interest" for checking accounts
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              interestRate != null ? '${interestRate.toStringAsFixed(2)}% p.a. interest' : 'No interest',
              style: PayPinkTheme.body(fontSize: 10, fontWeight: FontWeight.w700, color: brandWine),
            ),
          ),
          const SizedBox(height: 14),
          Divider(color: textLine, height: 1),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Available balance',
                style: PayPinkTheme.body(fontSize: 11, color: textMuted),
              ),
              Row(
                children: [
                  GestureDetector(
                    onTap: () {
                      Clipboard.setData(ClipboardData(text: fullNumber.replaceAll(' ', '')));
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          backgroundColor: PayPinkTheme.wine,
                          content: Text('Copied $name number to clipboard'),
                          duration: const Duration(seconds: 2),
                        ),
                      );
                    },
                    child: Row(
                      children: [
                        Icon(Icons.copy_rounded, size: 13, color: brandWine),
                        const SizedBox(width: 4),
                        Text(
                          'Copy',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: brandWine,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  GestureDetector(
                    onTap: () {
                      if (widget.onNavigateTab != null) {
                        widget.onNavigateTab!(2); // Transfer tab
                      }
                    },
                    child: Row(
                      children: [
                        Icon(Icons.swap_horiz_rounded, size: 14, color: brandWine),
                        const SizedBox(width: 4),
                        Text(
                          'Transfer',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: brandWine,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildLoanCard(
    BuildContext context, {
    required BankAccount loan,
    required bool isMasked,
    required VoidCallback onToggleMask,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final brandWine = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;
    const name = 'Personal Loan';
    final maskedNumber = isMasked ? loan.maskedNumber : loan.accountNumber;
    final remainingBalance = loan.outstandingDebt ?? loan.currentBalance;
    final overdue = loan.status.toUpperCase() == 'OVERDUE' || (loan.penaltyDue ?? 0) > 0;
    final status = overdue ? 'Overdue' : 'Current';
    final rateTerm = [
      if (loan.interestRate != null) '${loan.interestRate!.toStringAsFixed(2)}% p.a.',
      if (loan.termMonths != null) '${loan.termMonths} Mo',
    ].join(' · ');
    void openDetails() => PayPinkBottomSheets.showLoanDetails(
          context,
          loan: loan,
          onPay: () => _openLoanPayment(loan),
        );

    return GlassCard(
      onTap: openDetails,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 38,
                    height: 38,
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(Icons.real_estate_agent_rounded, color: isDark ? const Color(0xFFFB7185) : PayPinkTheme.wine, size: 18),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        name,
                        style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700, color: textInk),
                      ),
                      const SizedBox(height: 2),
                      GestureDetector(
                        onTap: onToggleMask,
                        child: Row(
                          children: [
                            Text(
                              maskedNumber,
                              style: PayPinkTheme.mono(fontSize: 10, color: textMuted),
                            ),
                            const SizedBox(width: 4),
                            Icon(
                              isMasked ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                              size: 12,
                              color: textMuted,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF143322) : PayPinkTheme.greenBg,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '• $status',
                  style: PayPinkTheme.body(
                    fontSize: 10,
                    color: isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.hideBalances ? '••••••' : formatPeso(remainingBalance),
                    style: PayPinkTheme.display(
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                      color: textInk,
                      letterSpacing: -0.6,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text('Remaining loan balance', style: PayPinkTheme.body(fontSize: 11, color: textMuted)),
                  if (loan.paymentsLeftLabel != null) ...[
                    const SizedBox(height: 4),
                    Text(loan.paymentsLeftLabel!, style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: brandWine)),
                  ],
                ],
              ),
              if (loan.dueDate != null || loan.minimumPayment != null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      if (loan.dueDate != null)
                        Text('Due: ${loan.dueDate}', style: PayPinkTheme.body(fontSize: 10, fontWeight: FontWeight.w700, color: brandWine)),
                      if (loan.minimumPayment != null)
                        Text(formatPeso(loan.minimumPayment!), style: PayPinkTheme.mono(fontSize: 11, fontWeight: FontWeight.w800, color: textInk)),
                    ],
                  ),
                ),
            ],
          ),
          const SizedBox(height: 14),
          Divider(color: textLine, height: 1),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                rateTerm,
                style: PayPinkTheme.body(fontSize: 11, color: textMuted),
              ),
              Row(
                children: [
                  GestureDetector(
                    onTap: () => PayPinkBottomSheets.showLoanSchedule(context, loan: loan),
                    child: Row(
                      children: [
                        Icon(Icons.calendar_month_outlined, size: 13, color: brandWine),
                        const SizedBox(width: 4),
                        Text(
                          'Schedule',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: brandWine,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  GestureDetector(
                    onTap: () => _openLoanPayment(loan),
                    child: Row(
                      children: [
                        Icon(Icons.payment_rounded, size: 14, color: brandWine),
                        const SizedBox(width: 4),
                        Text(
                          'Pay Loan',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: brandWine,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}
