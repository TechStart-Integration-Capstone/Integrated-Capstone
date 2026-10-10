import 'package:flutter/material.dart';
import '../services/account_service.dart';
import '../theme/paypink_theme.dart';
import '../widgets/bottom_sheets.dart';
import '../widgets/loan_application_sheet.dart';
import '../widgets/loan_payment_sheet.dart';
import '../widgets/paypink_shimmer.dart';

/// One place for everything loans: your loans (payments left, next payment, missed auto-debit),
/// Pay, Details & schedule, and Apply. Mirrors the web Loans page (frontend/bank/loans.js).
class LoansScreen extends StatefulWidget {
  final List<BankAccount> initialAccounts;

  /// Called after a payment or a new loan so the rest of the app refreshes balances.
  final VoidCallback? onChanged;
  final void Function(double amount, String fundingAccount, String loanAccount)? onLoanPaymentSuccess;

  const LoansScreen({
    super.key,
    required this.initialAccounts,
    this.onChanged,
    this.onLoanPaymentSuccess,
  });

  static Future<void> open(
    BuildContext context, {
    required List<BankAccount> accounts,
    VoidCallback? onChanged,
    void Function(double amount, String fundingAccount, String loanAccount)? onLoanPaymentSuccess,
  }) {
    return Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => LoansScreen(
        initialAccounts: accounts,
        onChanged: onChanged,
        onLoanPaymentSuccess: onLoanPaymentSuccess,
      ),
    ));
  }

  @override
  State<LoansScreen> createState() => _LoansScreenState();
}

class _LoansScreenState extends State<LoansScreen> {
  late List<BankAccount> _accounts = widget.initialAccounts;
  LoanEligibility? _eligibility;
  bool _loading = true;
  String? _error;

  List<BankAccount> get _loans => _accounts.where((a) => a.isLoan).toList();

  @override
  void initState() {
    super.initState();
    _reload();
  }

  Future<void> _reload() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    // Only real server data: on failure keep what is on screen and say so (never placeholder accounts).
    final results = await Future.wait([
      AccountService.fetchLoansOrNull(),
      AccountService.fetchLoanEligibility(),
    ]);
    if (!mounted) return;
    final loans = results[0] as List<BankAccount>?;
    setState(() {
      if (loans != null) {
        _accounts = [..._accounts.where((a) => !a.isLoan), ...loans];
      } else {
        _error = 'We couldn’t load your latest loan details. Pull down to try again.';
      }
      _eligibility = results[1] as LoanEligibility? ?? _eligibility;
      _loading = false;
    });
  }

  void _changed() {
    widget.onChanged?.call();
    _reload();
  }

  void _pay(BankAccount loan) {
    LoanPaymentSheet.show(
      context,
      loanAccount: loan,
      accounts: _accounts,
      onPaymentSuccess: (amount, funding, _) {
        widget.onLoanPaymentSuccess?.call(amount, funding.accountNumber, loan.accountNumber);
        _changed();
      },
    );
  }

  void _details(BankAccount loan) => PayPinkBottomSheets.showLoanDetails(context, loan: loan, onPay: () => _pay(loan));

  void _apply() => LoanApplicationSheet.show(context, accounts: _accounts, onLoanAccepted: _changed);

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final ink = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final muted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final loans = _loans;

    final bg = isDark ? PayPinkTheme.darkBg : PayPinkTheme.paper;

    return Container(
      color: bg,
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Scaffold(
            backgroundColor: Colors.transparent,
            appBar: AppBar(
              backgroundColor: Colors.transparent,
              surfaceTintColor: Colors.transparent,
              foregroundColor: ink,
              title: Text('Loans', style: PayPinkTheme.display(fontSize: 20, fontWeight: FontWeight.w700, color: ink)),
            ),
            body: SafeArea(
              top: false,
              child: RefreshIndicator(
                color: PayPinkTheme.wine,
                onRefresh: _reload,
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(18, 8, 18, 32),
                children: [
                  Text('Pay, track and apply for loans in one place.', style: PayPinkTheme.body(fontSize: 13, color: muted)),
                  const SizedBox(height: 18),
                  if (_error != null) ...[
                    _notice(_error!, isDark),
                    const SizedBox(height: 14),
                  ],
                  _sectionTitle('Your loans', loans.isEmpty ? null : '${loans.length} active', ink, muted),
                  const SizedBox(height: 10),
                  if (_loading && loans.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: PayPinkShimmer.accountCardSkeleton(isDark: isDark),
                    )
                  else if (loans.isEmpty)
                    _emptyLoans(isDark, ink, muted)
                  else
                    ...loans.map((l) => Padding(padding: const EdgeInsets.only(bottom: 12), child: _loanCard(l, isDark, ink, muted))),
                  const SizedBox(height: 14),
                  _sectionTitle('Borrow', null, ink, muted),
                  const SizedBox(height: 10),
                  _borrowCard(isDark, ink, muted),
                  const SizedBox(height: 16),
                  Text(
                    'Installments are collected automatically on their due date from the account the loan was paid into. '
                    'Payments settle any late fee first, then your oldest installment. Paying early has no fee.',
                    style: PayPinkTheme.body(fontSize: 11, color: muted, height: 1.6),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

  Widget _sectionTitle(String title, String? trailing, Color ink, Color muted) => Row(
        children: [
          Text(title, style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w700, color: ink)),
          const Spacer(),
          if (trailing != null) Text(trailing, style: PayPinkTheme.body(fontSize: 12, color: muted)),
        ],
      );

  BoxDecoration _card(bool isDark) => PayPinkTheme.glassCardDecoration(
        bg: isDark ? PayPinkTheme.darkCard : Colors.white,
        borderColor: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line,
      );

  Widget _notice(String text, bool isDark) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF3A1E22) : PayPinkTheme.errorBg,
          border: Border.all(color: isDark ? const Color(0xFF5A2D33) : PayPinkTheme.errorBorder),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(text, style: PayPinkTheme.body(fontSize: 12, color: isDark ? const Color(0xFFF2B8B5) : PayPinkTheme.errorText)),
      );

  Widget _loanCard(BankAccount loan, bool isDark, Color ink, Color muted) {
    final overdue = loan.status.toUpperCase() == 'OVERDUE';
    final left = loan.paymentsRemaining, total = loan.paymentsTotal;
    final paidShare = (left != null && total != null && total > 0) ? (total - left) / total : null;
    final balance = loan.outstandingDebt ?? loan.currentBalance;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: _card(isDark),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text('Personal loan · ${loan.accountNumber}',
                    style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: muted), overflow: TextOverflow.ellipsis),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isDark
                      ? (overdue ? const Color(0xFF451A1D) : const Color(0xFF143322))
                      : (overdue ? const Color(0xFFFBEFED) : PayPinkTheme.greenBg),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  overdue ? 'Overdue' : 'Active',
                  style: PayPinkTheme.body(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: isDark
                        ? (overdue ? const Color(0xFFF87171) : const Color(0xFF4ADE80))
                        : (overdue ? const Color(0xFFA33D39) : PayPinkTheme.green),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(formatPeso(balance), style: PayPinkTheme.display(fontSize: 26, fontWeight: FontWeight.w700, color: ink, letterSpacing: -0.6)),
          Text('Remaining principal', style: PayPinkTheme.body(fontSize: 11, color: muted)),
          if (paidShare != null) ...[
            const SizedBox(height: 12),
            Semantics(
              label: loan.paymentsLeftLabel,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: paidShare,
                  minHeight: 6,
                  backgroundColor: isDark ? PayPinkTheme.darkLine : const Color(0xFFF4EEF1),
                  color: PayPinkTheme.wine,
                ),
              ),
            ),
            const SizedBox(height: 6),
            Text(loan.paymentsLeftLabel!, style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: ink)),
          ],
          if (loan.hasMissedAutoDebit) ...[
            const SizedBox(height: 12),
            _notice(
              'We couldn’t collect your ${formatPeso(loan.missedAutoDebitAmount!)} payment'
              '${loan.missedAutoDebitDate != null ? ' on ${loan.missedAutoDebitDate}' : ''}. '
              'Top up and we’ll try again tonight, or pay now to avoid ${overdue ? 'more charges' : 'a 2% late fee'}.',
              isDark,
            ),
          ],
          const SizedBox(height: 12),
          Divider(height: 1, color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
          const SizedBox(height: 10),
          if (loan.minimumPayment != null) _row('Next payment', '${formatPeso(loan.minimumPayment!)}${loan.dueDate != null ? ' · ${loan.dueDate}' : ''}', ink, muted),
          if ((loan.penaltyDue ?? 0) > 0) _row('Late fee', formatPeso(loan.penaltyDue!), ink, muted),
          if (loan.payoffAmount != null) _row('Total to pay off', formatPeso(loan.payoffAmount!), ink, muted),
          if (loan.repaymentAccountNumber != null)
            _row('Auto-debit from', '•••• ${loan.repaymentAccountNumber!.substring(loan.repaymentAccountNumber!.length > 4 ? loan.repaymentAccountNumber!.length - 4 : 0)}', ink, muted),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => _details(loan),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size.fromHeight(46),
                    foregroundColor: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                    side: BorderSide(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.inputBorder),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm)),
                  ),
                  child: Text('Details & schedule', style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w600, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine)),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: FilledButton(
                  onPressed: () => _pay(loan),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(46),
                    backgroundColor: PayPinkTheme.wine,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm)),
                  ),
                  child: Text('Pay', style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _row(String label, String value, Color ink, Color muted) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(
          children: [
            Text(label, style: PayPinkTheme.body(fontSize: 12, color: muted)),
            const Spacer(),
            Flexible(
              child: Text(value,
                  textAlign: TextAlign.right,
                  style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: ink).copyWith(fontFeatures: PayPinkTheme.tabularFigures)),
            ),
          ],
        ),
      );

  Widget _emptyLoans(bool isDark, Color ink, Color muted) => Container(
        padding: const EdgeInsets.all(20),
        decoration: _card(isDark),
        child: Column(
          children: [
            Icon(Icons.account_balance_outlined, size: 28, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine),
            const SizedBox(height: 8),
            Text('No active loans', style: PayPinkTheme.body(fontSize: 14, fontWeight: FontWeight.w700, color: ink)),
            const SizedBox(height: 4),
            Text('Loans you accept will appear here, with what’s due next.',
                textAlign: TextAlign.center, style: PayPinkTheme.body(fontSize: 12, color: muted)),
          ],
        ),
      );

  Widget _borrowCard(bool isDark, Color ink, Color muted) {
    final e = _eligibility;
    final canApply = e == null || e.eligible;
    final headline = e == null
        ? 'Need a little extra?'
        : e.eligible
            ? 'You can borrow up to ${formatPeso(e.available)}'
            : 'You can’t apply right now';
    final detail = e == null
        ? 'Get an instant decision with your PayPink account.'
        : e.eligible
            ? 'Credit limit ${formatPeso(e.creditLimit)} · ${formatPeso(e.outstanding)} already borrowed.'
            : (e.reason?.isNotEmpty == true ? e.reason! : 'Pay down your current loan to borrow again.');

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: _card(isDark),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(headline, style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w700, color: ink)),
          const SizedBox(height: 4),
          Text(detail, style: PayPinkTheme.body(fontSize: 12, color: muted, height: 1.5)),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            height: 46,
            child: FilledButton(
              onPressed: canApply ? _apply : null,
              style: FilledButton.styleFrom(
                backgroundColor: PayPinkTheme.wine,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm)),
              ),
              child: Text('Apply for a loan', style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white)),
            ),
          ),
        ],
      ),
    );
  }
}
