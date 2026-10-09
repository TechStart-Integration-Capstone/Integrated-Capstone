import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'theme/paypink_theme.dart';
import 'screens/dashboard_screen.dart';
import 'screens/accounts_screen.dart';
import 'screens/remittance_screen.dart';
import 'screens/transactions_screen.dart';
import 'screens/login_register_screen.dart';
import 'screens/pin_auth_screen.dart';
import 'services/auth_service.dart';
import 'services/account_service.dart';
import 'services/secure_token_storage.dart';
import 'widgets/bottom_sheets.dart';
import 'widgets/profile_sheet.dart';
import 'widgets/paypink_logo.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:paypink_mobile/src/core/di/injection_container.dart' as di;
import 'package:paypink_mobile/src/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:paypink_mobile/src/features/accounts/presentation/bloc/accounts_bloc.dart';
import 'package:paypink_mobile/src/features/remittance/presentation/bloc/remittance_bloc.dart';
import 'package:paypink_mobile/src/features/transactions/presentation/bloc/transactions_bloc.dart';


class DevHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) {
    return super.createHttpClient(context)
      ..badCertificateCallback = (X509Certificate cert, String host, int port) => true;
  }
}


void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  GoogleFonts.config.allowRuntimeFetching = true;
  await di.initServiceLocator();
  if (!kIsWeb) {
    HttpOverrides.global = DevHttpOverrides();
  }
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
    ),
  );
  runApp(
    MultiBlocProvider(
      providers: [
        BlocProvider<AuthBloc>(create: (_) => di.sl<AuthBloc>()),
        BlocProvider<AccountsBloc>(create: (_) => di.sl<AccountsBloc>()..add(FetchAccountsEvent())),
        BlocProvider<RemittanceBloc>(create: (_) => di.sl<RemittanceBloc>()),
        BlocProvider<TransactionsBloc>(create: (_) => di.sl<TransactionsBloc>()..add(FetchTransactionsEvent())),
      ],
      child: const PayPinkMobileApp(),
    ),
  );
}


class PayPinkMobileApp extends StatefulWidget {
  final bool initialAuthenticated;
  const PayPinkMobileApp({super.key, this.initialAuthenticated = false});

  @override
  State<PayPinkMobileApp> createState() => _PayPinkMobileAppState();
}

class _PayPinkMobileAppState extends State<PayPinkMobileApp> {
  late bool _isAuthenticated;
  bool _isDarkMode = false;
  String _currentUser = '';
  String _currentFullName = '';

  bool _isLoadingAuth = true;
  bool _showPinLogin = false;
  bool _requirePinSetup = false;

  @override
  void initState() {
    super.initState();
    _isAuthenticated = widget.initialAuthenticated;
    _checkInitialAuth();
  }

  Future<void> _checkInitialAuth() async {
    if (_isAuthenticated) {
      if (mounted) setState(() => _isLoadingAuth = false);
      return;
    }

    try {
      final hasToken = await AuthService.hasActiveSession();
      final user = await SecureTokenStorage.getUsername() ?? '';
      final fullName = await SecureTokenStorage.getFullName() ?? user;

      if (!mounted) return;

      if (hasToken && user.isNotEmpty) {
        _currentUser = user;
        _currentFullName = fullName.isNotEmpty ? fullName : user;
        // User already has an active session token: bypass PIN login prompt directly to authenticated dashboard
        _isAuthenticated = true;
        _showPinLogin = false;
      }
    } catch (e) {
      debugPrint('[PayPink] Storage check non-critical failure: $e');
    } finally {
      if (mounted) {
        setState(() {
          _isLoadingAuth = false;
        });
      }
    }
  }

  void _toggleTheme() {
    setState(() => _isDarkMode = !_isDarkMode);
  }

  void _handleLoginSuccess(String user, String fullName) async {
    final hasPin = await SecureTokenStorage.hasPin();
    setState(() {
      _currentUser = user;
      _currentFullName = fullName.isNotEmpty ? fullName : user;
      if (!hasPin) {
        _requirePinSetup = true;
      } else {
        _isAuthenticated = true;
        _showPinLogin = false;
        _requirePinSetup = false;
      }
    });
  }

  void _handlePinSetupComplete() {
    setState(() {
      _requirePinSetup = false;
      _isAuthenticated = true;
    });
  }

  void _handleLogout() async {
    await AuthService.logout();
    if (!mounted) return;
    setState(() {
      _isAuthenticated = false;
      _showPinLogin = false;
      _requirePinSetup = false;
      _currentUser = '';
      _currentFullName = '';
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PayPink Mobile Banking',
      debugShowCheckedModeBanner: false,
      theme: PayPinkTheme.lightTheme,
      darkTheme: PayPinkTheme.darkTheme,
      themeMode: _isDarkMode ? ThemeMode.dark : ThemeMode.light,
      home: _isLoadingAuth
          ? Container(
              decoration: BoxDecoration(
                gradient: _isDarkMode ? PayPinkTheme.darkBgGradient : PayPinkTheme.lightBgGradient,
              ),
              child: const Center(
                child: CircularProgressIndicator(color: PayPinkTheme.wine),
              ),
            )
          : _isAuthenticated
              ? MainNavigationShell(
                  isDarkMode: _isDarkMode,
                  onToggleTheme: _toggleTheme,
                  onLogout: _handleLogout,
                  currentUser: _currentUser.isNotEmpty ? _currentUser : 'Customer',
                  currentFullName: _currentFullName.isNotEmpty ? _currentFullName : null,
                )
              : _requirePinSetup
                  ? PinAuthScreen(
                      mode: PinScreenMode.setup,
                      username: _currentUser,
                      fullName: _currentFullName,
                      isDarkMode: _isDarkMode,
                      onAuthSuccess: _handlePinSetupComplete,
                    )
                  : _showPinLogin
                      ? PinAuthScreen(
                          mode: PinScreenMode.login,
                          username: _currentUser,
                          fullName: _currentFullName,
                          isDarkMode: _isDarkMode,
                          onAuthSuccess: () {
                            setState(() {
                              _showPinLogin = false;
                              _isAuthenticated = true;
                            });
                          },
                          onFallbackToPassword: () {
                            setState(() {
                              _showPinLogin = false;
                              _isAuthenticated = false;
                            });
                          },
                        )
                      : LoginRegisterScreen(
                          onLoginSuccess: _handleLoginSuccess,
                          isDarkMode: _isDarkMode,
                          onToggleTheme: _toggleTheme,
                        ),
    );
  }
}

class MainNavigationShell extends StatefulWidget {
  final bool isDarkMode;
  final VoidCallback onToggleTheme;
  final VoidCallback onLogout;
  final String currentUser;
  final String? currentFullName;

  const MainNavigationShell({
    super.key,
    this.isDarkMode = false,
    required this.onToggleTheme,
    required this.onLogout,
    this.currentUser = 'Customer',
    this.currentFullName,
  });

  @override
  State<MainNavigationShell> createState() => _MainNavigationShellState();
}

class _MainNavigationShellState extends State<MainNavigationShell> {
  int _currentIndex = 0;
  bool _hideBalances = false;

  String get _avatarInitials {
    final name = _userProfile?.fullName ?? widget.currentFullName ?? widget.currentUser;
    final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.length >= 2 && parts[0].isNotEmpty && parts[1].isNotEmpty) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    } else if (name.isNotEmpty) {
      return name[0].toUpperCase();
    }
    return 'P';
  }
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
      'title': 'Account Protected',
      'message': 'Biometric & 6-Digit MPIN security active.',
      'time': '10 mins ago',
      'unread': false,
    },
  ];

  final List<TransactionItem> _transactions = [];

  UserProfile? _userProfile;
  String? _profileError;

  @override
  void initState() {
    super.initState();
    _loadLiveDatabaseData();
  }

  void _loadLiveDatabaseData({bool preserveLocalTransactions = true, bool bypassCache = false}) async {
    UserProfile? profile;
    String? profileError;
    try {
      profile = await AccountService.fetchProfile(
        fallbackUsername: widget.currentUser,
        bypassCache: bypassCache,
      );
    } on ProfileUnavailableException catch (e) {
      profileError = e.message;
    }
    final txs = await AccountService.fetchTransactions();
    if (!mounted) return;
    if (profileError != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text(profileError),
          action: SnackBarAction(
            label: 'Retry',
            textColor: Colors.white,
            onPressed: () => _loadLiveDatabaseData(bypassCache: true),
          ),
        ),
      );
    }
    setState(() {
      _profileError = profileError;
      if (profile != null) {
        if (_userProfile == null || !preserveLocalTransactions) {
          _userProfile = profile;
        } else {
          // Merge accounts preserving optimistic real-time debits and credits
          final currentAcctsMap = {for (final a in _userProfile!.accounts) a.accountNumber: a};
          final mergedAccounts = profile.accounts.map((fresh) {
            final local = currentAcctsMap[fresh.accountNumber];
            if (local != null) {
              // Loan repayments are posted synchronously by loan-service, so its figures are current.
              if (local.isLoan) return fresh;
              // If local account was debited, keep the lower balance until backend reflects it
              if (local.currentBalance < fresh.currentBalance) {
                return fresh.copyWith(currentBalance: local.currentBalance);
              }
              // If local account was credited, keep the higher balance
              if (local.currentBalance > fresh.currentBalance) {
                return fresh.copyWith(currentBalance: local.currentBalance);
              }
            }
            return fresh;
          }).toList();
          _userProfile = profile.copyWith(accounts: mergedAccounts);
        }
      }

      if (txs.isNotEmpty) {
        if (!preserveLocalTransactions || _transactions.isEmpty) {
          _transactions.clear();
          _transactions.addAll(txs);
        } else {
          // Merge live transactions preserving newly posted local items at the top
          final fetchedIds = txs.map((t) => t.id).toSet();
          final localUnsynced = _transactions.where((t) => !fetchedIds.contains(t.id)).toList();
          _transactions.clear();
          _transactions.addAll([...localUnsynced, ...txs]);
        }
      }
    });
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
    final cleanSource = source.replaceAll(RegExp(r'\D'), '');
    final last4 = cleanSource.length >= 4 ? cleanSource.substring(cleanSource.length - 4) : cleanSource;
    final destClean = recipient.replaceAll(RegExp(r'\D'), '');
    final now = DateTime.now();
    final months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final hour = now.hour > 12 ? now.hour - 12 : (now.hour == 0 ? 12 : now.hour);
    final ampm = now.hour >= 12 ? 'PM' : 'AM';
    final timeStr = '${hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')} $ampm';
    final dateStr = '${months[now.month - 1]} ${now.day}, ${now.year} · $timeStr';

    String sourceCategory = 'Everyday Checking';
    if (source.toLowerCase().contains('sav') || cleanSource.startsWith('0011')) {
      sourceCategory = 'Savings Account';
    } else if (source.toLowerCase().contains('check') || cleanSource.startsWith('0013') || source.toLowerCase().contains('everyday')) {
      sourceCategory = 'Everyday Checking';
    } else if (source.toLowerCase().contains('paypink')) {
      sourceCategory = 'PayPink Account';
    }

    // 1. Immediate in-memory optimistic balance mutation (sender debited, receiver credited if owned)
    if (_userProfile != null && _userProfile!.accounts.isNotEmpty) {
      final srcClean = source.replaceAll(RegExp(r'\D'), '');

      final updatedAccounts = _userProfile!.accounts.map((acct) {
        final acctClean = acct.accountNumber.replaceAll(RegExp(r'\D'), '');

        // Debit sender account
        if (acctClean == srcClean || acct.accountNumber == source || acct.accountId.toString() == source || (srcClean.length >= 4 && acctClean.endsWith(srcClean))) {
          final newBal = (acct.currentBalance - amount).clamp(0.0, double.infinity);
          return acct.copyWith(
            currentBalance: newBal,
          );
        }

        // Credit receiver account if it belongs to user's own accounts
        if (acctClean == destClean || acct.accountNumber == recipient || acct.accountId.toString() == recipient || (destClean.length >= 4 && acctClean.endsWith(destClean))) {
          final newBal = acct.currentBalance + amount;
          return acct.copyWith(
            currentBalance: newBal,
          );
        }

        return acct;
      }).toList();

      _userProfile = _userProfile!.copyWith(
        accounts: updatedAccounts,
      );
    }

    final cleanRefId = refId.startsWith('TXN-')
        ? refId
        : 'TXN-${DateTime.now().year}-${(DateTime.now().millisecondsSinceEpoch % 100000).toString().padLeft(5, '0')}';

    final isToSavings = recipient.toLowerCase().contains('saving') || destClean.startsWith('0011');
    final isToChecking = recipient.toLowerCase().contains('checking') || destClean.startsWith('0013');

    String txTitle;
    if (isToSavings) {
      txTitle = 'Transfer to Savings';
    } else if (isToChecking) {
      txTitle = 'Transfer to Checking';
    } else if (recipient.toLowerCase().contains('paypink')) {
      final cleanRecipientName = recipient.split('·').first.split('(').first.trim();
      txTitle = 'Transfer to $cleanRecipientName';
    } else {
      final cleanRecipientName = recipient.split('·').first.split('(').first.trim();
      txTitle = 'Transfer to ${cleanRecipientName.isNotEmpty ? cleanRecipientName : recipient}';
    }

    final newTx = TransactionItem(
      id: cleanRefId,
      title: txTitle,
      date: dateStr,
      account: last4.isNotEmpty ? '$sourceCategory •••• $last4' : sourceCategory,
      amount: amount,
      isCredit: false,
      transactionType: 'TRANSFER_OUT',
      counterparty: recipient,
      status: 'Completed',
      timestamp: now,
      sourceAccount: source,
      recipientAccount: recipient,
    );

    TransactionItem? creditTx;
    if (isToSavings) {
      creditTx = TransactionItem(
        id: '$cleanRefId-CR',
        title: 'Transfer from Checking',
        date: dateStr,
        account: 'Savings Account •••• ${destClean.length >= 4 ? destClean.substring(destClean.length - 4) : '3469'}',
        amount: amount,
        isCredit: true,
        transactionType: 'TRANSFER_IN',
        counterparty: last4.isNotEmpty ? '$sourceCategory •••• $last4' : sourceCategory,
        status: 'Completed',
        timestamp: now,
        sourceAccount: source,
        recipientAccount: recipient,
      );
    }

    setState(() {
      _transactions.removeWhere((t) => t.id == cleanRefId || t.id == '$cleanRefId-CR');
      if (creditTx != null) {
        _transactions.insert(0, creditTx);
      }
      _transactions.insert(0, newTx);

      _notifications.insert(
        0,
        {
          'id': DateTime.now().millisecondsSinceEpoch,
          'title': 'Transfer Successful',
          'message': 'Sent ₱${amount.toStringAsFixed(2)} to $recipient. Ref: $cleanRefId',
          'time': 'Just now',
          'unread': true,
        },
      );

      _currentIndex = 0; // Return to Dashboard overview where Recent Activity is at the top
    });

    _triggerStatusToast('Sent ₱${amount.toStringAsFixed(2)} to $recipient', '✅');

    // Bypass client-side caching to retrieve fresh balances directly from Azure SQL
    _loadLiveDatabaseData(preserveLocalTransactions: true, bypassCache: true);
  }

  void _handleTransactionReversal(TransactionItem tx) {
    if (!tx.isReversible && tx.status.toUpperCase() == 'REVERSED') {
      _triggerStatusToast('Transaction has already been reversed.', '⚠️');
      return;
    }

    final refundAmount = tx.amount;
    final now = DateTime.now();
    final months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final hour = now.hour > 12 ? now.hour - 12 : (now.hour == 0 ? 12 : now.hour);
    final ampm = now.hour >= 12 ? 'PM' : 'AM';
    final timeStr = '${hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')} $ampm';
    final dateStr = '${months[now.month - 1]} ${now.day}, ${now.year} · $timeStr';

    // 1. Refund the debited source account in _userProfile
    if (_userProfile != null && _userProfile!.accounts.isNotEmpty) {
      final srcClean = (tx.sourceAccount ?? tx.account).replaceAll(RegExp(r'\D'), '');
      final destClean = (tx.recipientAccount ?? tx.counterparty ?? '').replaceAll(RegExp(r'\D'), '');

      final updatedAccounts = _userProfile!.accounts.map((acct) {
        final acctClean = acct.accountNumber.replaceAll(RegExp(r'\D'), '');

        // Refund sender account
        if (acctClean == srcClean || (acct.last4.isNotEmpty && srcClean.contains(acct.last4)) || acctClean.endsWith(acct.last4)) {
          return acct.copyWith(
            currentBalance: acct.currentBalance + refundAmount,
          );
        }

        // If target was owned account, revert the credit
        if (destClean.isNotEmpty && (acctClean == destClean || (acct.last4.isNotEmpty && destClean.contains(acct.last4)))) {
          return acct.copyWith(
            currentBalance: (acct.currentBalance - refundAmount).clamp(0.0, double.infinity),
          );
        }

        return acct;
      }).toList();

      _userProfile = _userProfile!.copyWith(accounts: updatedAccounts);
    }

    // 2. Mark original transaction as REVERSED
    setState(() {
      final idx = _transactions.indexWhere((t) => t.id == tx.id);
      if (idx != -1) {
        _transactions[idx] = tx.copyWith(
          status: 'REVERSED',
        );
      }

      // If there was an internal companion credit, mark it reversed too
      final crIdx = _transactions.indexWhere((t) => t.id == '${tx.id}-CR');
      if (crIdx != -1) {
        _transactions[crIdx] = _transactions[crIdx].copyWith(status: 'REVERSED');
      }

      // 3. Prepend an explicit Reversal credit entry to activity
      final reversalCreditTx = TransactionItem(
        id: 'REV-${tx.id}',
        title: 'Reversal: ${tx.title}',
        date: dateStr,
        account: tx.account,
        amount: refundAmount,
        isCredit: true,
        transactionType: 'TRANSFER_IN',
        counterparty: 'PayPink 15-Min Reversal Service',
        status: 'Completed',
        timestamp: now,
        sourceAccount: 'PayPink Reversal System',
        recipientAccount: tx.account,
      );
      _transactions.insert(0, reversalCreditTx);

      // 4. Add notification
      _notifications.insert(
        0,
        {
          'id': DateTime.now().millisecondsSinceEpoch,
          'title': 'Transfer Reversed',
          'message': '₱${refundAmount.toStringAsFixed(2)} has been refunded to ${tx.account}. Ref: REV-${tx.id}',
          'time': 'Just now',
          'unread': true,
        },
      );
    });

    _triggerStatusToast('Transfer reversed! ₱${refundAmount.toStringAsFixed(2)} refunded to ${tx.account}', '↩');
  }

  void _handleLoanPaymentSuccess(double amount, String fundingAccount, String loanAccount) {
    final now = DateTime.now();
    final months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final dateStr = '${months[now.month - 1]} ${now.day}, ${now.year}';
    final refId = 'LOAN-PAY-${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}';

    // 1. Immediate in-memory optimistic balance mutation (funding debited, loan outstanding reduced)
    if (_userProfile != null && _userProfile!.accounts.isNotEmpty) {
      final fundClean = fundingAccount.replaceAll(RegExp(r'\D'), '');
      final loanClean = loanAccount.replaceAll(RegExp(r'\D'), '');

      final updatedAccounts = _userProfile!.accounts.map((acc) {
        final accClean = acc.accountNumber.replaceAll(RegExp(r'\D'), '');
        // Debit funding account
        if (accClean == fundClean || acc.accountNumber == fundingAccount) {
          final newBal = (acc.currentBalance - amount).clamp(0.0, double.infinity);
          return acc.copyWith(currentBalance: newBal);
        }
        // Reduce this loan's debt until the refresh below brings loan-service's figures
        if (acc.isLoan && (accClean == loanClean || acc.accountNumber == loanAccount)) {
          final newBal = (acc.currentBalance - amount).clamp(0.0, double.infinity);
          final currentDebt = acc.outstandingDebt ?? 0.0;
          final newDebt = (currentDebt - amount).clamp(0.0, double.infinity);
          return acc.copyWith(currentBalance: newBal, outstandingDebt: newDebt);
        }
        return acc;
      }).toList();

      _userProfile = _userProfile!.copyWith(
        accounts: updatedAccounts,
      );
    }

    final newTx = TransactionItem(
      id: refId,
      title: 'Personal Loan Repayment',
      date: dateStr,
      account: fundingAccount.length >= 4 ? '•••• ${fundingAccount.substring(fundingAccount.length - 4)}' : fundingAccount,
      amount: amount,
      isCredit: false,
      transactionType: 'LOAN_PAYMENT',
      counterparty: 'Personal Loan',
      status: 'COMPLETED',
    );

    setState(() {
      _transactions.insert(0, newTx);
      _notifications.insert(
        0,
        {
          'id': DateTime.now().millisecondsSinceEpoch,
          'title': 'Loan Payment Received',
          'message': '₱${amount.toStringAsFixed(2)} applied toward personal loan balance.',
          'time': 'Just now',
          'unread': true,
        },
      );
      _currentIndex = 0;
    });

    _triggerStatusToast('Loan payment of ₱${amount.toStringAsFixed(2)} processed!', '💳');
    _loadLiveDatabaseData(preserveLocalTransactions: true, bypassCache: true);
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

  @override
  Widget build(BuildContext context) {

    final hasUnreadNotifs = _notifications.any((n) => n['unread'] == true);
    final isDark = widget.isDarkMode;

    final resolvedFullName = _userProfile?.fullName.isNotEmpty == true
        ? _userProfile!.fullName
        : (widget.currentFullName?.isNotEmpty == true ? widget.currentFullName! : widget.currentUser);
    final resolvedFirstName = _userProfile?.firstName.isNotEmpty == true
        ? _userProfile!.firstName
        : (resolvedFullName.split(' ').first.isNotEmpty ? resolvedFullName.split(' ').first : widget.currentUser);
    final primaryAccountNum = _userProfile?.primaryAccount?.accountNumber;

    final screens = [
      DashboardScreen(
        hideBalances: _hideBalances,
        onToggleHideBalances: () => setState(() => _hideBalances = !_hideBalances),
        onNavigateTab: (idx) => setState(() => _currentIndex = idx),
        transactions: _transactions,
        userName: resolvedFirstName,
        userFullName: resolvedFullName,
        accounts: _userProfile?.accounts,
        totalBalance: _userProfile?.totalBalance,
        onLoanPaymentSuccess: _handleLoanPaymentSuccess,
        onReverseTransaction: _handleTransactionReversal,
        onRefreshData: () => _loadLiveDatabaseData(bypassCache: true),
      ),
      AccountsScreen(
        hideBalances: _hideBalances,
        onToggleHideBalances: () => setState(() => _hideBalances = !_hideBalances),
        onNavigateTab: (idx) => setState(() => _currentIndex = idx),
        accounts: _userProfile?.accounts,
        userName: resolvedFullName,
        loadError: _userProfile == null ? _profileError : null,
        onRetry: () => _loadLiveDatabaseData(bypassCache: true),
        onLoanPaymentSuccess: _handleLoanPaymentSuccess,
      ),
      RemittanceScreen(
        onTransferSuccess: _handleTransferSuccess,
        // Loans are not transfer sources or targets; they are paid from the loan sheet.
        accounts: _userProfile?.accounts.where((a) => !a.isLoan).toList(),
      ),
      TransactionsScreen(
        transactions: _transactions,
        customerName: resolvedFullName,
        primaryAccountNumber: primaryAccountNum,
        onReverseTransaction: _handleTransactionReversal,
      ),
    ];

    return Container(
      decoration: BoxDecoration(
        gradient: isDark ? PayPinkTheme.darkBgGradient : PayPinkTheme.lightBgGradient,
      ),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Scaffold(
            extendBody: false,
            backgroundColor: Colors.transparent,
            body: SafeArea(
              bottom: false,
              child: Column(
                children: [
                  // App Header Bar with Theme Toggle and Logout
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 18.0, vertical: 12.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Builder(
                            builder: (context) {
                              final now = DateTime.now();
                              const months = [
                                'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                                'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
                              ];
                              final dynamicDate = '${months[now.month - 1]} ${now.day}, ${now.year}';
                              return PayPinkLogo.header(
                                isDark: isDark,
                                subtitle: dynamicDate,
                              );
                            },
                          ),
                        ),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            // Notification Bell with live red unread dot
                            Stack(
                              children: [
                                IconButton(
                                  visualDensity: VisualDensity.compact,
                                  padding: const EdgeInsets.all(4),
                                  constraints: const BoxConstraints(),
                                  icon: Icon(
                                    Icons.notifications_none_rounded,
                                    color: isDark ? Colors.white : PayPinkTheme.wine,
                                    size: 20,
                                  ),
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
                                    top: 4,
                                    right: 4,
                                    child: Container(
                                      width: 7,
                                      height: 7,
                                      decoration: const BoxDecoration(
                                        color: PayPinkTheme.red,
                                        shape: BoxShape.circle,
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                            const SizedBox(width: 8),
                            // Customer Avatar with dynamic initials (opens dedicated Profile View & Settings)
                            GestureDetector(
                              onTap: () {
                                ProfileSheet.show(
                                  context,
                                  userProfile: _userProfile,
                                  currentUser: widget.currentUser,
                                  isDarkMode: isDark,
                                  onToggleTheme: widget.onToggleTheme,
                                  onLogout: widget.onLogout,
                                  onUpdateProfile: (newName, newEmail) {
                                    setState(() {
                                      if (_userProfile != null) {
                                        _userProfile = _userProfile!.copyWith(
                                          fullName: newName,
                                          firstName: newName.split(' ').first,
                                          email: newEmail,
                                        );
                                      }
                                    });
                                  },
                                );
                              },
                              child: CircleAvatar(
                                radius: 15,
                                backgroundColor: isDark ? PayPinkTheme.wineDark : PayPinkTheme.pink,
                                child: Text(
                                  _avatarInitials,
                                  style: PayPinkTheme.display(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w800,
                                    color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
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

            // Bottom tab bar
            bottomNavigationBar: _buildDynamicBottomBar(),
          ),
        ),
      ),
    );
  }

  // Matches the web's phone tab bar (bank.css @media max-width:680px .sidebar):
  // flat surface, top hairline, icon over an always-visible label, tinted active tab.
  Widget _buildDynamicBottomBar() {
    final isDark = widget.isDarkMode;
    return Container(
      decoration: BoxDecoration(
        color: isDark ? PayPinkTheme.darkPaper : Colors.white,
        border: Border(top: BorderSide(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
          child: Row(
            children: [
              _buildDynamicNavItem(0, Icons.home_outlined, 'Overview'),
              _buildDynamicNavItem(1, Icons.account_balance_wallet_outlined, 'Accounts'),
              _buildDynamicNavItem(2, Icons.swap_horiz_rounded, 'Transfer'),
              _buildDynamicNavItem(3, Icons.receipt_long_outlined, 'Activity'),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDynamicNavItem(int index, IconData icon, String label) {
    final isSelected = _currentIndex == index;
    final isDark = widget.isDarkMode;
    final activeColor = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;
    final idleColor = isDark ? PayPinkTheme.darkMuted : const Color(0xFF867B84);
    return Expanded(
      child: Semantics(
        button: true,
        selected: isSelected,
        label: label,
        excludeSemantics: true,
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: () {
            HapticFeedback.lightImpact();
            setState(() => _currentIndex = index);
          },
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            margin: const EdgeInsets.symmetric(horizontal: 2),
            padding: const EdgeInsets.symmetric(vertical: 7),
            decoration: BoxDecoration(
              color: isSelected
                  ? (isDark ? PayPinkTheme.wine.withValues(alpha: 0.35) : const Color(0xFFF6EAF0))
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: 21, color: isSelected ? activeColor : idleColor),
                const SizedBox(height: 3),
                Text(
                  label,
                  style: PayPinkTheme.body(
                    fontSize: 11,
                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                    color: isSelected ? activeColor : idleColor,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
