import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'theme/paypink_theme.dart';
import 'screens/dashboard_screen.dart';
import 'screens/accounts_screen.dart';
import 'screens/remittance_screen.dart';
import 'screens/transactions_screen.dart';
import 'screens/circuit_breaker_screen.dart';
import 'services/circuit_breaker_client.dart';
import 'widgets/bottom_sheets.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
    ),
  );
  runApp(const PayPinkMobileApp());
}

class PayPinkMobileApp extends StatelessWidget {
  const PayPinkMobileApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PayPink Mobile Banking',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        fontFamily: 'DM Sans',
        scaffoldBackgroundColor: PayPinkTheme.paper,
        colorScheme: ColorScheme.fromSeed(
          seedColor: PayPinkTheme.wine,
          surface: PayPinkTheme.paper,
        ),
        useMaterial3: true,
      ),
      home: const MainNavigationShell(),
    );
  }
}

class MainNavigationShell extends StatefulWidget {
  const MainNavigationShell({super.key});

  @override
  State<MainNavigationShell> createState() => _MainNavigationShellState();
}

class _MainNavigationShellState extends State<MainNavigationShell> {
  int _currentIndex = 0;
  bool _hideBalances = false;
  final CircuitBreakerClient _circuitBreaker = CircuitBreakerClient();

  // In-App Notifications state
  final List<Map<String, dynamic>> _notifications = [
    {
      'id': 1,
      'title': 'Welcome gift received',
      'message': '₱50.00 credited to Everyday account •••• 5046.',
      'time': 'Just now',
      'unread': true,
    },
    {
      'id': 2,
      'title': 'Security Perimeter Active',
      'message': 'Hardware KeyStore encryption initialized.',
      'time': '10 mins ago',
      'unread': false,
    },
  ];

  final List<TransactionItem> _transactions = [
    TransactionItem(
      id: 'TRX-20261002-001',
      title: 'Welcome gift',
      date: 'Oct 2, 2026',
      account: 'Account •••• 5046',
      amount: 50.00,
      isCredit: true,
      ofscore: 'FUNDS.TRANSFER,AUTH/I/PROCESS,//PH100201,DEBIT.ACCT.NO=CORE.POOL,CREDIT.ACCT.NO=5046,AMOUNT=50.00,CCY=PHP',
    ),
    TransactionItem(
      id: 'TRX-20260925-882',
      title: 'Personal Loan Disbursement',
      date: 'Sep 25, 2026',
      account: 'Loan •••• 9921',
      amount: 50000.00,
      isCredit: true,
      ofscore: 'LD.LOANS.AND.DEPOSITS,AUTH/I/PROCESS,//PH092501,DEBIT.ACCT.NO=TREASURY.POOL,CREDIT.ACCT.NO=9921,AMOUNT=50000.00,CCY=PHP',
    ),
    TransactionItem(
      id: 'TRX-20260928-104',
      title: 'Coffee Bean Manila (Reversed)',
      date: 'Sep 28, 2026',
      account: 'Account •••• 5046',
      amount: 185.00,
      isCredit: false,
      ofscore: 'FUNDS.TRANSFER,REVERSE/I/PROCESS,//REV20260928,DEBIT.ACCT.NO=MERCH.COFFEE,CREDIT.ACCT.NO=5046,AMOUNT=185.00,CCY=PHP',
      status: 'REVERSED',
    ),
    TransactionItem(
      id: 'TRX-20261001-331',
      title: 'External Transfer (DLQ Retrying)',
      date: 'Oct 1, 2026',
      account: 'Account •••• 5046',
      amount: 500.00,
      isCredit: false,
      ofscore: 'FUNDS.TRANSFER,AUTH/I/PROCESS,//PH100133,DEBIT.ACCT.NO=5046,CREDIT.ACCT.NO=EXT.9912,AMOUNT=500.00,CCY=PHP',
      status: 'FAILED_DLQ',
    ),
  ];

  @override
  void initState() {
    super.initState();
    _circuitBreaker.addListener(_onCircuitBreakerChange);
  }

  @override
  void dispose() {
    _circuitBreaker.removeListener(_onCircuitBreakerChange);
    super.dispose();
  }

  void _onCircuitBreakerChange() {
    setState(() {});
  }

  void _triggerStatusToast(String message, String icon) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: PayPinkTheme.wine,
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 90),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        content: Row(
          children: [
            Text(icon, style: const TextStyle(fontSize: 16)),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                message,
                style: PayPinkTheme.body(color: Colors.white, fontWeight: FontWeight.w700),
              ),
            ),
          ],
        ),
        duration: const Duration(seconds: 3),
      ),
    );
  }

  void _handleTransferSuccess(double amount, String refId, String source, String recipient) {
    setState(() {
      _transactions.insert(
        0,
        TransactionItem(
          id: refId,
          title: recipient.contains('Savings') ? 'Transfer to Savings' : 'Remittance Sent',
          date: 'Oct 2, 2026',
          account: 'Account •••• 5046',
          amount: amount,
          isCredit: false,
          ofscore: 'FUNDS.TRANSFER,AUTH/I/PROCESS,//$refId,DEBIT.ACCT.NO=5046,AMOUNT=${amount.toStringAsFixed(2)},CCY=PHP',
        ),
      );

      _notifications.insert(
        0,
        {
          'id': DateTime.now().millisecondsSinceEpoch,
          'title': 'Transfer successful',
          'message': 'Sent ₱${amount.toStringAsFixed(2)} to $recipient. Ref: $refId',
          'time': 'Just now',
          'unread': true,
        },
      );

      _currentIndex = 0; // Return to dashboard
    });

    _triggerStatusToast('Sent ₱${amount.toStringAsFixed(2)} to $recipient', '✅');
  }

  void _markAllNotificationsRead() {
    setState(() {
      for (var n in _notifications) {
        n['unread'] = false;
      }
    });
  }

  void _dismissNotification(int id) {
    setState(() {
      _notifications.removeWhere((n) => n['id'] == id);
    });
  }

  void _showChaosEngineeringMenu() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(22),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Chaos & Architecture Controls',
                  style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w700),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: PayPinkTheme.pinkSubtle,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: PayPinkTheme.pink),
                  ),
                  child: Text(
                    'Capstone 2',
                    style: PayPinkTheme.body(fontSize: 10, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ListTile(
              leading: const Icon(Icons.flash_on_rounded, color: PayPinkTheme.red),
              title: const Text('Trip Circuit Breaker', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
              subtitle: const Text('Simulate SLA timeout <= 200ms -> Trips to OPEN fail-fast', style: TextStyle(fontSize: 11)),
              onTap: () {
                Navigator.pop(ctx);
                _circuitBreaker.tripBreaker();
                _triggerStatusToast('Circuit Breaker: OPEN', '⚡');
              },
            ),
            ListTile(
              leading: const Icon(Icons.speed_rounded, color: PayPinkTheme.amber),
              title: const Text('Trigger 429 Rate Limit', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
              subtitle: const Text('Redis token-bucket rate limiter (>10 req/s simulation)', style: TextStyle(fontSize: 11)),
              onTap: () {
                Navigator.pop(ctx);
                _triggerStatusToast('Edge Rate Limit: HTTP 429', '⚠️');
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    backgroundColor: PayPinkTheme.amber,
                    content: Text('HTTP 429: Too Many Requests (>10 req/s). Redis token bucket active.'),
                  ),
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.key_rounded, color: PayPinkTheme.wine),
              title: const Text('Hardware KeyStore / Vault', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
              subtitle: const Text('Inspect iOS Keychain / Android KeyStore hardware tokens', style: TextStyle(fontSize: 11)),
              onTap: () {
                Navigator.pop(ctx);
                PayPinkBottomSheets.showHardwareVault(
                  context,
                  hardwareKeyId: 'secp256r1-keychain-hardware-tsamson',
                  circuitStatus: _circuitBreaker.isOpen ? 'OPEN (Tripped)' : 'CLOSED (Healthy)',
                  gatewayRoute: '127.0.0.1:8080 (Reverse Proxy)',
                  jwtToken: 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0c2Ftc29uIiwicm9sZSI6IkNVU1RPTUVSIiwiZXhwIjoxNzkxMDEwMDAwfQ',
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_circuitBreaker.isOpen) {
      return Container(
        color: const Color(0xFFF0EAEF),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: CircuitBreakerScreen(
              onRecover: () {
                setState(() {});
                _triggerStatusToast('Circuit Breaker: CLOSED', '🛡️');
              },
            ),
          ),
        ),
      );
    }

    final hasUnreadNotifs = _notifications.any((n) => n['unread'] == true);

    final screens = [
      DashboardScreen(
        hideBalances: _hideBalances,
        onToggleHideBalances: () => setState(() => _hideBalances = !_hideBalances),
        onNavigateTab: (idx) => setState(() => _currentIndex = idx),
        transactions: _transactions,
      ),
      AccountsScreen(
        hideBalances: _hideBalances,
        onToggleHideBalances: () => setState(() => _hideBalances = !_hideBalances),
        onNavigateTab: (idx) => setState(() => _currentIndex = idx),
      ),
      RemittanceScreen(
        onTransferSuccess: _handleTransferSuccess,
      ),
      TransactionsScreen(
        transactions: _transactions,
      ),
    ];

    return Container(
      color: const Color(0xFFEFE8EC), // Elegant neutral frame background on desktop web
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Scaffold(
            extendBody: true,
            backgroundColor: PayPinkTheme.paper,
            body: SafeArea(
              bottom: false,
              child: Column(
                children: [
                  // App Header Bar matching mockup
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 18.0, vertical: 12.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Container(
                              width: 34,
                              height: 34,
                              decoration: BoxDecoration(
                                color: PayPinkTheme.wine,
                                borderRadius: BorderRadius.circular(10),
                                boxShadow: [
                                  BoxShadow(
                                    color: PayPinkTheme.wine.withValues(alpha: 0.25),
                                    blurRadius: 8,
                                    offset: const Offset(0, 3),
                                  ),
                                ],
                              ),
                              child: const Center(
                                child: Text(
                                  'p',
                                  style: TextStyle(
                                    fontFamily: 'Manrope',
                                    fontSize: 22,
                                    fontWeight: FontWeight.w800,
                                    color: Colors.white,
                                    fontStyle: FontStyle.italic,
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'PayPink®',
                                  style: PayPinkTheme.display(
                                    fontSize: 18,
                                    fontWeight: FontWeight.w800,
                                    color: PayPinkTheme.wine,
                                    letterSpacing: -0.8,
                                  ),
                                ),
                                Text(
                                  'Fri, October 2, 2026',
                                  style: PayPinkTheme.body(fontSize: 9.5, color: PayPinkTheme.muted),
                                ),
                              ],
                            ),
                          ],
                        ),
                        Row(
                          children: [
                            // Chaos Testing Trigger Button
                            IconButton(
                              icon: const Icon(Icons.tune_rounded, color: PayPinkTheme.wine, size: 20),
                              tooltip: 'Chaos & Arch Controls',
                              onPressed: _showChaosEngineeringMenu,
                            ),
                            // Notification Bell with live red unread dot
                            Stack(
                              children: [
                                IconButton(
                                  icon: const Icon(Icons.notifications_none_rounded, color: PayPinkTheme.wine, size: 22),
                                  onPressed: () {
                                    PayPinkBottomSheets.showNotificationsDrawer(
                                      context,
                                      notifications: _notifications,
                                      onMarkAllRead: _markAllNotificationsRead,
                                      onDismiss: _dismissNotification,
                                    );
                                  },
                                ),
                                if (hasUnreadNotifs)
                                  Positioned(
                                    top: 8,
                                    right: 8,
                                    child: Container(
                                      width: 8,
                                      height: 8,
                                      decoration: const BoxDecoration(
                                        color: PayPinkTheme.red,
                                        shape: BoxShape.circle,
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                            const SizedBox(width: 4),
                            // Customer Avatar TS
                            GestureDetector(
                              onTap: () {
                                PayPinkBottomSheets.showHardwareVault(
                                  context,
                                  hardwareKeyId: 'secp256r1-keychain-hardware-tsamson',
                                  circuitStatus: _circuitBreaker.isOpen ? 'OPEN (Tripped)' : 'CLOSED (Healthy)',
                                  gatewayRoute: '127.0.0.1:8080 (Reverse Proxy)',
                                  jwtToken: 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0c2Ftc29uIiwicm9sZSI6IkNVU1RPTUVSIiwiZXhwIjoxNzkxMDEwMDAwfQ',
                                );
                              },
                              child: CircleAvatar(
                                radius: 17,
                                backgroundColor: PayPinkTheme.pink,
                                child: Text(
                                  'TS',
                                  style: PayPinkTheme.display(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w800,
                                    color: PayPinkTheme.wine,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  // Scrollable Active View Screen
                  Expanded(
                    child: screens[_currentIndex],
                  ),
                ],
              ),
            ),

            // Dynamic Glassmorphism Floating Bottom Navigation Bar
            bottomNavigationBar: _buildDynamicBottomBar(),
          ),
        ),
      ),
    );
  }

  Widget _buildDynamicBottomBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(32),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
          child: Container(
            height: 64,
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.84),
              borderRadius: BorderRadius.circular(32),
              border: Border.all(
                color: Colors.white.withValues(alpha: 0.95),
                width: 1.2,
              ),
              boxShadow: [
                BoxShadow(
                  color: PayPinkTheme.wine.withValues(alpha: 0.12),
                  blurRadius: 28,
                  offset: const Offset(0, 10),
                ),
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.04),
                  blurRadius: 10,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildDynamicNavItem(0, Icons.grid_view_rounded, 'Overview'),
                _buildDynamicNavItem(1, Icons.account_balance_wallet_rounded, 'Accounts'),
                _buildDynamicNavItem(2, Icons.swap_horiz_rounded, 'Transfer'),
                _buildDynamicNavItem(3, Icons.receipt_long_rounded, 'Activity'),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDynamicNavItem(int index, IconData icon, String label) {
    final isSelected = _currentIndex == index;
    return GestureDetector(
      onTap: () {
        HapticFeedback.lightImpact();
        setState(() => _currentIndex = index);
      },
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOutCubic,
        padding: EdgeInsets.symmetric(
          horizontal: isSelected ? 15 : 10,
          vertical: 8,
        ),
        decoration: BoxDecoration(
          color: isSelected ? PayPinkTheme.wine : Colors.transparent,
          borderRadius: BorderRadius.circular(22),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: PayPinkTheme.wine.withValues(alpha: 0.28),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ]
              : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 20,
              color: isSelected ? Colors.white : PayPinkTheme.muted,
            ),
            AnimatedCrossFade(
              firstChild: Padding(
                padding: const EdgeInsets.only(left: 6),
                child: Text(
                  label,
                  style: PayPinkTheme.body(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                  ),
                ),
              ),
              secondChild: const SizedBox.shrink(),
              crossFadeState: isSelected ? CrossFadeState.showFirst : CrossFadeState.showSecond,
              duration: const Duration(milliseconds: 200),
            ),
          ],
        ),
      ),
    );
  }
}
