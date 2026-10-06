import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/bottom_sheets.dart';
import '../services/account_service.dart';

class AccountsScreen extends StatefulWidget {
  final bool hideBalances;
  final VoidCallback onToggleHideBalances;
  final Function(int)? onNavigateTab;
  final List<BankAccount>? accounts;

  const AccountsScreen({
    super.key,
    required this.hideBalances,
    required this.onToggleHideBalances,
    this.onNavigateTab,
    this.accounts,
  });

  @override
  State<AccountsScreen> createState() => _AccountsScreenState();
}

class _AccountsScreenState extends State<AccountsScreen> {
  final Set<int> _unmaskedAccountIds = {};
  bool _maskEveryday = true;
  bool _maskSavings = false;
  bool _maskLoan = true;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final hasLiveAccounts = widget.accounts != null && widget.accounts!.isNotEmpty;
    final totalLinked = hasLiveAccounts ? widget.accounts!.length : 3;

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
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
            style: PayPinkTheme.body(fontSize: 12.5, color: textMuted),
          ),
          const SizedBox(height: 20),

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
                        fontSize: 9.5,
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
                        fontSize: 11.5,
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

          // Dynamic Live Database Accounts or Fallbacks
          if (hasLiveAccounts) ...[
            ...widget.accounts!.map((account) {
              final isMasked = !_unmaskedAccountIds.contains(account.accountId);
              final isSavings = account.accountType == 'SAVINGS_ACCOUNT';
              final isChecking = account.accountType == 'CHECKING_ACCOUNT';
              final isEveryday = account.accountType == 'EVERYDAY_ACCOUNT';

              final IconData icon = isSavings
                  ? Icons.savings_rounded
                  : (isChecking
                      ? Icons.business_center_rounded
                      : (isEveryday ? Icons.account_balance_wallet_rounded : Icons.credit_card_rounded));

              final Color iconColor = isSavings
                  ? PayPinkTheme.wine
                  : (isChecking
                      ? PayPinkTheme.indigo
                      : (isEveryday ? PayPinkTheme.green : PayPinkTheme.amber));

              final Color iconBg = isSavings
                  ? PayPinkTheme.pinkSubtle
                  : (isChecking
                      ? PayPinkTheme.indigoBg
                      : (isEveryday ? PayPinkTheme.greenBg : PayPinkTheme.amberBg));

              return Padding(
                padding: const EdgeInsets.only(bottom: 14.0),
                child: _buildAccountFullCard(
                  context,
                  name: account.displayName,
                  maskedNumber: isMasked ? account.maskedNumber : account.formattedNumber,
                  fullNumber: account.formattedNumber,
                  isMasked: isMasked,
                  onToggleMask: () {
                    setState(() {
                      if (_unmaskedAccountIds.contains(account.accountId)) {
                        _unmaskedAccountIds.remove(account.accountId);
                      } else {
                        _unmaskedAccountIds.add(account.accountId);
                      }
                    });
                  },
                  balance: account.currentBalance,
                  heldBalance: 0.00,
                  interestRate: isSavings ? 1.50 : 0.25,
                  type: account.accountType,
                  status: account.status.toLowerCase() == 'active' ? 'Active' : account.status,
                  ledgerId: account.accountNumber,
                  icon: icon,
                  iconColor: iconColor,
                  iconBg: iconBg,
                ),
              );
            }),
          ] else ...[
            // Everyday Account Card
            _buildAccountFullCard(
              context,
              name: 'Everyday account',
              maskedNumber: _maskEveryday ? '•••• •••• 5046' : '001 1 5046 8001',
              fullNumber: '001 1 5046 8001',
              isMasked: _maskEveryday,
              onToggleMask: () => setState(() => _maskEveryday = !_maskEveryday),
              balance: 50.00,
              heldBalance: 0.00,
              interestRate: 0.25,
              type: 'EVERYDAY_ACCOUNT',
              status: 'Active',
              ledgerId: 'everyday-5046',
              icon: Icons.account_balance_wallet_rounded,
              iconColor: PayPinkTheme.green,
              iconBg: PayPinkTheme.greenBg,
            ),
            const SizedBox(height: 14),

            // Savings Account Card
            _buildAccountFullCard(
              context,
              name: 'Savings account',
              maskedNumber: _maskSavings ? '•••• •••• 8504' : '001 1 5968504 7',
              fullNumber: '001 1 5968504 7',
              isMasked: _maskSavings,
              onToggleMask: () => setState(() => _maskSavings = !_maskSavings),
              balance: 0.00,
              heldBalance: 0.00,
              interestRate: 1.50,
              type: 'SAVINGS_ACCOUNT',
              status: 'Active',
              ledgerId: 'savings-8504',
              icon: Icons.savings_rounded,
              iconColor: PayPinkTheme.wine,
              iconBg: PayPinkTheme.pinkSubtle,
            ),
            const SizedBox(height: 14),

            // Personal Loan Account Card
            _buildLoanCard(
              context,
              name: 'Personal Loan',
              maskedNumber: _maskLoan ? '•••• •••• 9921' : '001 9 9921 4410',
              fullNumber: '001 9 9921 4410',
              isMasked: _maskLoan,
              onToggleMask: () => setState(() => _maskLoan = !_maskLoan),
              remainingBalance: 45000.00,
              amortization: 3750.00,
              dueDate: 'Oct 25, 2026',
              status: 'Current',
            ),
          ],
          const SizedBox(height: 20),

          // Discretion note card matching mockup
          GlassCard(
            backgroundColor: PayPinkTheme.paper,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.shield_outlined, color: PayPinkTheme.wine, size: 20),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'A little discretion, built in.',
                        style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        'Your account numbers are masked by default. Tap the eye icon to reveal them, or copy the number.',
                        style: PayPinkTheme.body(
                          fontSize: 11,
                          color: PayPinkTheme.muted,
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
    );
  }

  Widget _buildAccountFullCard(
    BuildContext context, {
    required String name,
    required String maskedNumber,
    required String fullNumber,
    required bool isMasked,
    required VoidCallback onToggleMask,
    required double balance,
    double heldBalance = 0.0,
    double interestRate = 1.50,
    required String type,
    required String status,
    required String ledgerId,
    required IconData icon,
    required Color iconColor,
    required Color iconBg,
  }) {
    return GlassCard(
      onTap: () => PayPinkBottomSheets.showAccountDetails(
        context,
        name: name,
        fullNumber: fullNumber,
        balance: balance,
        type: type,
        status: status,
        ledgerId: ledgerId,
      ),
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
                      color: iconBg,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(icon, color: iconColor, size: 18),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        name,
                        style: PayPinkTheme.display(fontSize: 13.5, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 2),
                      GestureDetector(
                        onTap: onToggleMask,
                        child: Row(
                          children: [
                            Text(
                              maskedNumber,
                              style: PayPinkTheme.mono(fontSize: 10, color: PayPinkTheme.muted),
                            ),
                            const SizedBox(width: 4),
                            Icon(
                              isMasked ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                              size: 12,
                              color: PayPinkTheme.muted,
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
                  color: PayPinkTheme.greenBg,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '• $status',
                  style: PayPinkTheme.body(
                    fontSize: 9.5,
                    color: PayPinkTheme.green,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            widget.hideBalances ? '••••••' : '₱${balance.toStringAsFixed(2)}',
            style: PayPinkTheme.display(
              fontSize: 28,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.6,
            ),
          ),
          const SizedBox(height: 10),
          // Available vs Held vs Interest Pills
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                decoration: BoxDecoration(
                  color: PayPinkTheme.greenBg,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  'Avail: ₱${balance.toStringAsFixed(2)}',
                  style: PayPinkTheme.body(fontSize: 9.5, fontWeight: FontWeight.w700, color: PayPinkTheme.green),
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: PayPinkTheme.line),
                ),
                child: Text(
                  'Held: ₱${heldBalance.toStringAsFixed(2)}',
                  style: PayPinkTheme.body(fontSize: 9.5, fontWeight: FontWeight.w600, color: PayPinkTheme.muted),
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                decoration: BoxDecoration(
                  color: PayPinkTheme.pinkSubtle,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '${interestRate.toStringAsFixed(2)}% p.a.',
                  style: PayPinkTheme.body(fontSize: 9.5, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(color: PayPinkTheme.line, height: 1),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Available balance',
                style: PayPinkTheme.body(fontSize: 10.5, color: PayPinkTheme.muted),
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
                        const Icon(Icons.copy_rounded, size: 13, color: PayPinkTheme.wine),
                        const SizedBox(width: 4),
                        Text(
                          'Copy',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: PayPinkTheme.wine,
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
                        const Icon(Icons.swap_horiz_rounded, size: 14, color: PayPinkTheme.wine),
                        const SizedBox(width: 4),
                        Text(
                          'Transfer',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: PayPinkTheme.wine,
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
    required String name,
    required String maskedNumber,
    required String fullNumber,
    required bool isMasked,
    required VoidCallback onToggleMask,
    required double remainingBalance,
    required double amortization,
    required String dueDate,
    required String status,
  }) {
    return GlassCard(
      onTap: () => PayPinkBottomSheets.showLoanDetails(
        context,
        loanNumber: fullNumber,
        remaining: remainingBalance,
        amortization: amortization,
        dueDate: dueDate,
      ),
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
                      color: PayPinkTheme.indigoBg,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.real_estate_agent_rounded, color: PayPinkTheme.indigo, size: 18),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        name,
                        style: PayPinkTheme.display(fontSize: 13.5, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 2),
                      GestureDetector(
                        onTap: onToggleMask,
                        child: Row(
                          children: [
                            Text(
                              maskedNumber,
                              style: PayPinkTheme.mono(fontSize: 10, color: PayPinkTheme.muted),
                            ),
                            const SizedBox(width: 4),
                            Icon(
                              isMasked ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                              size: 12,
                              color: PayPinkTheme.muted,
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
                  color: PayPinkTheme.indigoBg,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '• $status',
                  style: PayPinkTheme.body(
                    fontSize: 9.5,
                    color: PayPinkTheme.indigo,
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
                    widget.hideBalances ? '••••••' : '₱${remainingBalance.toStringAsFixed(2)}',
                    style: PayPinkTheme.display(
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.6,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text('Remaining loan balance', style: PayPinkTheme.body(fontSize: 10.5, color: PayPinkTheme.muted)),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                decoration: BoxDecoration(
                  color: PayPinkTheme.pinkSubtle,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text('Due: $dueDate', style: PayPinkTheme.body(fontSize: 9.5, fontWeight: FontWeight.w700, color: PayPinkTheme.wine)),
                    Text('₱${amortization.toStringAsFixed(0)}/mo', style: PayPinkTheme.mono(fontSize: 10.5, fontWeight: FontWeight.w800, color: PayPinkTheme.ink)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(color: PayPinkTheme.line, height: 1),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '5.50% p.a. · 12 Mo',
                style: PayPinkTheme.body(fontSize: 10.5, color: PayPinkTheme.muted),
              ),
              Row(
                children: [
                  GestureDetector(
                    onTap: () => PayPinkBottomSheets.showLoanDetails(
                      context,
                      loanNumber: fullNumber,
                      remaining: remainingBalance,
                      amortization: amortization,
                      dueDate: dueDate,
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.calendar_month_outlined, size: 13, color: PayPinkTheme.wine),
                        const SizedBox(width: 4),
                        Text(
                          'Schedule',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: PayPinkTheme.wine,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  GestureDetector(
                    onTap: () => PayPinkBottomSheets.showLoanDetails(
                      context,
                      loanNumber: fullNumber,
                      remaining: remainingBalance,
                      amortization: amortization,
                      dueDate: dueDate,
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.payment_rounded, size: 14, color: PayPinkTheme.wine),
                        const SizedBox(width: 4),
                        Text(
                          'Pay Loan',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: PayPinkTheme.wine,
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
