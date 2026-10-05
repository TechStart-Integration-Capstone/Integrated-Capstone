import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/bottom_sheets.dart';
import 'transactions_screen.dart';

class DashboardScreen extends StatefulWidget {
  final bool hideBalances;
  final VoidCallback onToggleHideBalances;
  final Function(int) onNavigateTab;
  final List<TransactionItem> transactions;

  const DashboardScreen({
    super.key,
    required this.hideBalances,
    required this.onToggleHideBalances,
    required this.onNavigateTab,
    required this.transactions,
  });

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  bool _maskEveryday = true;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Greeting matching mockup
          Text(
            'Hello, Trixie.',
            style: PayPinkTheme.display(
              fontSize: 26,
              fontWeight: FontWeight.w800,
              color: PayPinkTheme.ink,
              letterSpacing: -0.8,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            "Your everyday, at a glance. It's good to have you here.",
            style: PayPinkTheme.body(fontSize: 12.5, color: PayPinkTheme.muted),
          ),
          const SizedBox(height: 18),

          // Hero Wine Balance Card with Concentric Ripple Rings
          Container(
            width: double.infinity,
            decoration: PayPinkTheme.wineHeroDecoration(),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(24),
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
                        Text(
                          widget.hideBalances ? '••••••' : '₱50.00',
                          style: PayPinkTheme.display(
                            fontSize: 38,
                            fontWeight: FontWeight.w700,
                            color: Colors.white,
                            letterSpacing: -1.2,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Across 2 accounts. All yours.',
                          style: PayPinkTheme.body(
                            color: const Color(0xFFE2B4CB),
                            fontSize: 11.5,
                          ),
                        ),
                        const SizedBox(height: 18),
                        Divider(color: Colors.white.withValues(alpha: 0.15), height: 1),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'Your money, in view.',
                              style: PayPinkTheme.body(
                                color: PayPinkTheme.pink,
                                fontSize: 11,
                              ),
                            ),
                            GestureDetector(
                              onTap: () => widget.onNavigateTab(1), // Go to accounts
                              child: Row(
                                children: [
                                  Text(
                                    'View accounts',
                                    style: PayPinkTheme.body(
                                      color: Colors.white,
                                      fontSize: 11.5,
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

          // Quick Action Capsules matching mockup exactly
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _buildQuickAction(
                context,
                icon: Icons.arrow_outward_rounded,
                label: 'Send',
                isPrimary: true,
                onTap: () => widget.onNavigateTab(2), // Transfer tab
              ),
              _buildQuickAction(
                context,
                icon: Icons.south_west_rounded,
                label: 'Request',
                onTap: () => PayPinkBottomSheets.showRequestQr(context),
              ),
              _buildQuickAction(
                context,
                icon: Icons.qr_code_scanner_rounded,
                label: 'Scan QR',
                onTap: () => PayPinkBottomSheets.showScanQr(context),
              ),
              _buildQuickAction(
                context,
                icon: Icons.description_outlined,
                label: 'Report',
                onTap: () => PayPinkBottomSheets.showReportModal(context),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Monthly Flow Card: "This month, so far"
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
                        fontSize: 13.5,
                        fontWeight: FontWeight.w700,
                        color: PayPinkTheme.ink,
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        'Oct 2026',
                        style: PayPinkTheme.body(
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: PayPinkTheme.muted,
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
                            decoration: const BoxDecoration(
                              color: PayPinkTheme.greenBg,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.arrow_downward_rounded,
                              color: PayPinkTheme.green,
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
                                  fontSize: 10.5,
                                  color: PayPinkTheme.muted,
                                ),
                              ),
                              Text(
                                widget.hideBalances ? '••••••' : '₱50.00',
                                style: PayPinkTheme.display(
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
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
                            decoration: const BoxDecoration(
                              color: PayPinkTheme.pinkSubtle,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.arrow_upward_rounded,
                              color: PayPinkTheme.wine,
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
                                  fontSize: 10.5,
                                  color: PayPinkTheme.muted,
                                ),
                              ),
                              Text(
                                widget.hideBalances ? '••••••' : '₱0.00',
                                style: PayPinkTheme.display(
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
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
                const Divider(color: PayPinkTheme.line, height: 1),
                const SizedBox(height: 8),
                Text(
                  'Based on your latest 200 transactions.',
                  style: PayPinkTheme.body(fontSize: 10, color: PayPinkTheme.muted),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Spending Patterns & Customer 360 Insights Card
          GlassCard(
            onTap: () => PayPinkBottomSheets.showHardwareVault(context),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.donut_large_rounded, color: PayPinkTheme.wine, size: 18),
                        const SizedBox(width: 8),
                        Text(
                          'Spending Patterns & Insights',
                          style: PayPinkTheme.display(
                            fontSize: 13.5,
                            fontWeight: FontWeight.w700,
                            color: PayPinkTheme.ink,
                          ),
                        ),
                      ],
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: PayPinkTheme.greenBg,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        'Customer 360',
                        style: PayPinkTheme.body(
                          fontSize: 9.5,
                          fontWeight: FontWeight.w700,
                          color: PayPinkTheme.green,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                // Multi-Segment Spending Distribution Bar
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: SizedBox(
                    height: 8,
                    child: Row(
                      children: [
                        Expanded(flex: 60, child: Container(color: PayPinkTheme.wine)),
                        const SizedBox(width: 2),
                        Expanded(flex: 25, child: Container(color: PayPinkTheme.green)),
                        const SizedBox(width: 2),
                        Expanded(flex: 15, child: Container(color: PayPinkTheme.indigo)),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                // Legend
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Container(width: 8, height: 8, decoration: const BoxDecoration(color: PayPinkTheme.wine, shape: BoxShape.circle)),
                        const SizedBox(width: 4),
                        Text('Transfers (60%)', style: PayPinkTheme.body(fontSize: 9.5, color: PayPinkTheme.muted)),
                      ],
                    ),
                    Row(
                      children: [
                        Container(width: 8, height: 8, decoration: const BoxDecoration(color: PayPinkTheme.green, shape: BoxShape.circle)),
                        const SizedBox(width: 4),
                        Text('Bills & Utilities (25%)', style: PayPinkTheme.body(fontSize: 9.5, color: PayPinkTheme.muted)),
                      ],
                    ),
                    Row(
                      children: [
                        Container(width: 8, height: 8, decoration: const BoxDecoration(color: PayPinkTheme.indigo, shape: BoxShape.circle)),
                        const SizedBox(width: 4),
                        Text('Services (15%)', style: PayPinkTheme.body(fontSize: 9.5, color: PayPinkTheme.muted)),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Divider(color: PayPinkTheme.line, height: 1),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.security_rounded, size: 13, color: PayPinkTheme.green),
                        const SizedBox(width: 5),
                        Text(
                          'Risk Score: 0.12 (Safe) · KYC L3',
                          style: PayPinkTheme.mono(fontSize: 10, fontWeight: FontWeight.w700, color: PayPinkTheme.green),
                        ),
                      ],
                    ),
                    Text(
                      'Security vault →',
                      style: PayPinkTheme.body(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: PayPinkTheme.wine,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Your Accounts Section Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Text(
                    'Your accounts',
                    style: PayPinkTheme.display(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: PayPinkTheme.ink,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: PayPinkTheme.pinkSubtle,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: PayPinkTheme.pink),
                    ),
                    child: Text(
                      '3 linked',
                      style: PayPinkTheme.body(
                        fontSize: 9.5,
                        fontWeight: FontWeight.w700,
                        color: PayPinkTheme.wine,
                      ),
                    ),
                  ),
                ],
              ),
              GestureDetector(
                onTap: () => widget.onNavigateTab(1),
                child: Text(
                  'Manage view →',
                  style: PayPinkTheme.body(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w700,
                    color: PayPinkTheme.wine,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Everyday Account Card
          GlassCard(
            onTap: () => PayPinkBottomSheets.showAccountDetails(
              context,
              name: 'Everyday account',
              fullNumber: '001 1 5046 8001',
              balance: 50.00,
              type: 'EVERYDAY_ACCOUNT',
              status: 'Active',
              ledgerId: 'everyday-5046',
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
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: PayPinkTheme.greenBg,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(
                            Icons.account_balance_wallet_rounded,
                            color: PayPinkTheme.green,
                            size: 18,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Everyday account',
                              style: PayPinkTheme.display(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 2),
                            GestureDetector(
                              onTap: () => setState(() => _maskEveryday = !_maskEveryday),
                              child: Row(
                                children: [
                                  Text(
                                    _maskEveryday ? '•••• •••• 5046' : '001 1 5046 8001',
                                    style: PayPinkTheme.mono(
                                      fontSize: 10,
                                      color: PayPinkTheme.muted,
                                    ),
                                  ),
                                  const SizedBox(width: 4),
                                  Icon(
                                    _maskEveryday
                                        ? Icons.visibility_outlined
                                        : Icons.visibility_off_outlined,
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
                        '• Active',
                        style: PayPinkTheme.body(
                          fontSize: 9.5,
                          color: PayPinkTheme.green,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Text(
                  widget.hideBalances ? '••••••' : '₱50.00',
                  style: PayPinkTheme.display(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 12),
                const Divider(color: PayPinkTheme.line, height: 1),
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Available balance',
                      style: PayPinkTheme.body(fontSize: 10.5, color: PayPinkTheme.muted),
                    ),
                    Text(
                      'Account details →',
                      style: PayPinkTheme.body(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: PayPinkTheme.wine,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Savings Account Card
          GlassCard(
            onTap: () => PayPinkBottomSheets.showAccountDetails(
              context,
              name: 'Savings account',
              fullNumber: '001 1 5968504 7',
              balance: 0.00,
              type: 'SAVINGS_ACCOUNT',
              status: 'Active',
              ledgerId: 'savings-8504',
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
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: PayPinkTheme.pinkSubtle,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(
                            Icons.savings_rounded,
                            color: PayPinkTheme.wine,
                            size: 18,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Savings account',
                              style: PayPinkTheme.display(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '001 1 5968504 7',
                              style: PayPinkTheme.mono(
                                fontSize: 10,
                                color: PayPinkTheme.muted,
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
                        '• Active',
                        style: PayPinkTheme.body(
                          fontSize: 9.5,
                          color: PayPinkTheme.green,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Text(
                  widget.hideBalances ? '••••••' : '₱0.00',
                  style: PayPinkTheme.display(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 12),
                const Divider(color: PayPinkTheme.line, height: 1),
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Available balance',
                      style: PayPinkTheme.body(fontSize: 10.5, color: PayPinkTheme.muted),
                    ),
                    Text(
                      'Account details →',
                      style: PayPinkTheme.body(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: PayPinkTheme.wine,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Personal Loan Preview Card
          GlassCard(
            onTap: () => PayPinkBottomSheets.showLoanDetails(context),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: PayPinkTheme.indigoBg,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(
                            Icons.real_estate_agent_rounded,
                            color: PayPinkTheme.indigo,
                            size: 18,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Personal Loan',
                              style: PayPinkTheme.display(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '001 9 9921 4410',
                              style: PayPinkTheme.mono(
                                fontSize: 10,
                                color: PayPinkTheme.muted,
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
                        '• Current',
                        style: PayPinkTheme.body(
                          fontSize: 9.5,
                          color: PayPinkTheme.indigo,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.hideBalances ? '••••••' : '₱45,000.00',
                          style: PayPinkTheme.display(
                            fontSize: 24,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.5,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Remaining loan balance',
                          style: PayPinkTheme.body(fontSize: 10.5, color: PayPinkTheme.muted),
                        ),
                      ],
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: PayPinkTheme.pinkSubtle,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        'Due: Oct 25 (₱3,750)',
                        style: PayPinkTheme.body(
                          fontSize: 9.5,
                          fontWeight: FontWeight.w700,
                          color: PayPinkTheme.wine,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Divider(color: PayPinkTheme.line, height: 1),
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '5.50% p.a. · 12 Mo',
                      style: PayPinkTheme.body(fontSize: 10.5, color: PayPinkTheme.muted),
                    ),
                    Text(
                      'Loan details →',
                      style: PayPinkTheme.body(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: PayPinkTheme.wine,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 22),

          // Recent Activity Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Recent activity',
                style: PayPinkTheme.display(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: PayPinkTheme.ink,
                ),
              ),
              GestureDetector(
                onTap: () => widget.onNavigateTab(3), // Activity tab
                child: Text(
                  'View all →',
                  style: PayPinkTheme.body(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w700,
                    color: PayPinkTheme.wine,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Recent Activity Panel
          GlassCard(
            padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: widget.transactions.take(3).length,
              separatorBuilder: (_, __) => const Divider(color: PayPinkTheme.line, height: 1),
              itemBuilder: (context, index) {
                final tx = widget.transactions[index];
                return Material(
                  color: Colors.transparent,
                  child: ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Container(
                      width: 36,
                      height: 36,
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
                      style: PayPinkTheme.display(fontSize: 12.5, fontWeight: FontWeight.w700),
                    ),
                    subtitle: Text(
                      '${tx.account} · ${tx.date}',
                      style: PayPinkTheme.body(fontSize: 10, color: PayPinkTheme.muted),
                    ),
                    trailing: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          '${tx.isCredit ? '+' : '-'}₱${tx.amount.toStringAsFixed(2)}',
                          style: PayPinkTheme.display(
                            fontSize: 13,
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
                      auditHash: 'pg-audit-sha256-8f2c91a0c4',
                    ),
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 20),

          // Privacy Note Glass Card
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
                        'A little privacy goes a long way.',
                        style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        'Keep your account details and password just for you. Masked by default for mobile safety.',
                        style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted, height: 1.4),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildQuickAction(
    BuildContext context, {
    required IconData icon,
    required String label,
    bool isPrimary = false,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              gradient: isPrimary
                  ? const LinearGradient(
                      colors: [Color(0xFF7A204C), Color(0xFF551633)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    )
                  : null,
              color: isPrimary ? null : Colors.white.withValues(alpha: 0.9),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: isPrimary ? Colors.white.withValues(alpha: 0.3) : Colors.white,
                width: 1.4,
              ),
              boxShadow: [
                BoxShadow(
                  color: (isPrimary ? PayPinkTheme.wine : Colors.black).withValues(alpha: 0.1),
                  blurRadius: 14,
                  offset: const Offset(0, 5),
                ),
              ],
            ),
            child: Icon(
              icon,
              color: isPrimary ? Colors.white : PayPinkTheme.wine,
              size: 22,
            ),
          ),
          const SizedBox(height: 7),
          Text(
            label,
            style: PayPinkTheme.body(
              fontSize: 11.5,
              fontWeight: FontWeight.w600,
              color: PayPinkTheme.ink,
            ),
          ),
        ],
      ),
    );
  }
}
