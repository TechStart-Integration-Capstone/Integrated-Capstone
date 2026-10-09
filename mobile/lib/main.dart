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
import 'services/api_client.dart';
import 'services/auth_service.dart';
import 'services/notification_service.dart';
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
    ApiClient.unauthorizedNotifier.addListener(_handleSessionExpired);
    _checkInitialAuth();
  }

  @override
  void dispose() {
    ApiClient.unauthorizedNotifier.removeListener(_handleSessionExpired);
    super.dispose();
  }

  /// The server rejected the token (expired or revoked): send the user back to
  /// password sign-in. Their MPIN stays on the device.
  void _handleSessionExpired() {
    if (!ApiClient.unauthorizedNotifier.value || !_isAuthenticated) return;
    _handleLogout();
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
        // A live session is unlocked with the MPIN the user already created,
        // or they create one now if this device doesn't have it yet.
        if (await SecureTokenStorage.hasPinFor(user)) {
          _showPinLogin = true;
        } else {
          _requirePinSetup = true;
        }
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
    // Only ask for a new MPIN the first time this user signs in on this device.
    final hasPin = await SecureTokenStorage.hasPinFor(user);
    if (!mounted) return;
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
                          onFallbackToPassword: _handleLogout,
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
  // In-app notifications, derived from server transactions (same rules as the web app).
  List<Map<String, dynamic>> _notifications = [];
  List<TransactionItem> _serverTransactions = [];
  Set<String> _readNotificationIds = {};
  Set<String> _hiddenNotificationIds = {};

  final List<TransactionItem> _transactions = [];

  UserProfile? _userProfile;
  String? _profileError;

  @override
  void initState() {
    super.initState();
    _loadNotificationState();
    _loadLiveDatabaseData();
  }

  Future<void> _loadNotificationState() async {
    final read = await NotificationService.loadIds(widget.currentUser, 'read');
    final hidden = await NotificationService.loadIds(widget.currentUser, 'hidden');
    if (!mounted) return;
    setState(() {
      _readNotificationIds = read;
      _hiddenNotificationIds = hidden;
      _rebuildNotifications();
    });
  }

  void _rebuildNotifications() {
    _notifications = NotificationService.fromTransactions(
      _serverTransactions,
      read: _readNotificationIds,
      hidden: _hiddenNotificationIds,
    );
  }

  void _persistNotificationState() {
    final current = NotificationService.fromTransactions(_serverTransactions).map((n) => n['id'] as String);
    NotificationService.saveIds(widget.currentUser, 'read', _readNotificationIds, current);
    NotificationService.saveIds(widget.currentUser, 'hidden', _hiddenNotificationIds, current);
  }

  void _loadLiveDatabaseData({bool preserveLocalTransactions = false, bool bypassCache = false}) async {
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

      _serverTransactions = txs;
      _rebuildNotifications();

      if (txs.isNotEmpty) {
        if (!preserveLocalTransactions || _transactions.isEmpty) {
          _transactions.clear();
          _transactions.addAll(txs);
        } else {
          // Merge live transactions preserving newly posted local items at the top
          final fetchedIds = txs.map((t) => t.id).toSet();
          final localUnsynced = _transactions.where((t) {
            if (fetchedIds.contains(t.id)) return false;
            // Also check if any fetched transaction shares the same reference/id (e.g. substring or prefixed)
            final hasIdMatch = txs.any((f) =>
                f.id == t.id ||
                (t.id.isNotEmpty && f.id.isNotEmpty && (f.id.contains(t.id) || t.id.contains(f.id))));
            if (hasIdMatch) return false;
            // Also deduplicate by fuzzy match: same amount, same debit/credit, recent timestamp (< 15 mins)
            if (t.timestamp != null) {
              final hasRecentMatch = txs.any((f) {
                final amountMatches = (f.amount - t.amount).abs() < 0.001;
                final creditMatches = f.isCredit == t.isCredit;
                final timeMatches = f.timestamp == null ||
                    f.timestamp!.difference(t.timestamp!).inMinutes.abs() < 15;
                return amountMatches && creditMatches && timeMatches;
              });
              if (hasRecentMatch) return false;
            }
            return true;
          }).toList();
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

    // Check if the transfer is between the user's OWN accounts
    final ownAccounts = _userProfile?.accounts ?? [];
    final ownAccountNumbers = ownAccounts.map((a) => a.accountNumber.replaceAll(RegExp(r'\D'), '')).toSet();
    final isOwnAccountTransfer = ownAccountNumbers.contains(destClean);

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

        // Credit receiver account ONLY if it belongs to user's own accounts
        if (isOwnAccountTransfer && (acctClean == destClean || acct.accountNumber == recipient || acct.accountId.toString() == recipient || (destClean.length >= 4 && acctClean.endsWith(destClean)))) {
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

    final cleanRefId = (refId.isNotEmpty)
        ? refId
        : 'TXN-${DateTime.now().year}-${(DateTime.now().millisecondsSinceEpoch % 100000).toString().padLeft(5, '0')}';

    // Transaction title and credit leg determination
    String txTitle;
    TransactionItem? creditTx;

    if (isOwnAccountTransfer) {
      final isToSavings = recipient.toLowerCase().contains('saving') || destClean.startsWith('0011');
      final isToChecking = recipient.toLowerCase().contains('checking') || destClean.startsWith('0013');
      if (isToSavings) {
        txTitle = 'Transfer to Savings';
      } else if (isToChecking) {
        txTitle = 'Transfer to Checking';
      } else {
        txTitle = 'Transfer to Own Account';
      }

      creditTx = TransactionItem(
        id: '$cleanRefId-CR',
        title: 'Transfer from $sourceCategory',
        date: dateStr,
        account: '${isToSavings ? 'Savings' : 'Checking'} Account •••• ${destClean.length >= 4 ? destClean.substring(destClean.length - 4) : destClean}',
        amount: amount,
        isCredit: true,
        transactionType: 'TRANSFER_IN',
        counterparty: last4.isNotEmpty ? '$sourceCategory •••• $last4' : sourceCategory,
        status: 'Completed',
        timestamp: now,
        sourceAccount: source,
        recipientAccount: recipient,
      );
    } else {
      // External / P2P transfer — NO incoming credit leg should be created for sender
      final cleanRecipientName = recipient.split('·').first.split('(').first.trim();
      txTitle = cleanRecipientName.isNotEmpty && cleanRecipientName != destClean
          ? 'Transfer to $cleanRecipientName'
          : (recipient.toLowerCase().contains('paypink') ? 'Transfer to PayPink Recipient' : 'Transfer to $recipient');
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

    setState(() {
      _transactions.removeWhere((t) => t.id == cleanRefId || t.id == '$cleanRefId-CR');
      if (creditTx != null) {
        _transactions.insert(0, creditTx);
      }
      _transactions.insert(0, newTx);


      _currentIndex = 0; // Return to Dashboard overview where Recent Activity is at the top
    });

    _triggerStatusToast('Sent ₱${amount.toStringAsFixed(2)} to $recipient', '✅');

    // Bypass client-side caching to retrieve fresh balances directly from Azure SQL
    _loadLiveDatabaseData(preserveLocalTransactions: false, bypassCache: true);
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
      _currentIndex = 0;
    });

    _triggerStatusToast('Loan payment of ₱${amount.toStringAsFixed(2)} processed!', '💳');
    _loadLiveDatabaseData(preserveLocalTransactions: false, bypassCache: true);
  }

  void _markAllNotificationsRead() {
    setState(() {
      _readNotificationIds.addAll(_notifications.map((n) => n['id'] as String));
      _rebuildNotifications();
    });
    _persistNotificationState();
  }

  void _dismissNotification(String id) {
    setState(() {
      _readNotificationIds.add(id);
      _hiddenNotificationIds.add(id);
      _rebuildNotifications();
    });
    _persistNotificationState();
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
        accounts: _userProfile?.accounts ?? const [],
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
