import 'dart:convert';
import 'package:http/http.dart' as http;
import 'api_config.dart';
import 'secure_token_storage.dart';
import '../screens/transactions_screen.dart';

class BankAccount {
  final int accountId;
  final String accountNumber;
  final String accountType;
  final String currency;
  final double currentBalance;
  final String status;

  BankAccount({
    required this.accountId,
    required this.accountNumber,
    required this.accountType,
    required this.currency,
    required this.currentBalance,
    required this.status,
  });

  factory BankAccount.fromJson(Map<String, dynamic> json) {
    return BankAccount(
      accountId: json['accountId'] is int
          ? json['accountId']
          : int.tryParse(json['accountId']?.toString() ?? '0') ?? 0,
      accountNumber: json['accountNumber']?.toString() ?? '',
      accountType: json['accountType']?.toString() ?? 'SAVINGS_ACCOUNT',
      currency: json['currency']?.toString() ?? 'PHP',
      currentBalance: (json['currentBalance'] is num)
          ? (json['currentBalance'] as num).toDouble()
          : double.tryParse(json['currentBalance']?.toString() ?? '0.0') ?? 0.0,
      status: json['status']?.toString() ?? 'ACTIVE',
    );
  }

  String get displayName {
    switch (accountType) {
      case 'SAVINGS_ACCOUNT':
        return 'Savings account';
      case 'CHECKING_ACCOUNT':
        return 'Checking account';
      case 'EVERYDAY_ACCOUNT':
        return 'Everyday account';
      case 'STRESS_TEST_ACCOUNT':
        return 'Stress Test account';
      case 'TIME_DEPOSIT':
        return 'Time Deposit';
      default:
        return accountType
            .replaceAll('_', ' ')
            .toLowerCase()
            .split(' ')
            .map((w) => w.isNotEmpty ? '${w[0].toUpperCase()}${w.substring(1)}' : '')
            .join(' ');
    }
  }

  String get maskedNumber {
    if (accountNumber.length <= 4) return accountNumber;
    final last4 = accountNumber.substring(accountNumber.length - 4);
    return '•••• •••• $last4';
  }

  String get formattedNumber {
    if (accountNumber.length == 12) {
      return '${accountNumber.substring(0, 3)} ${accountNumber.substring(3, 4)} ${accountNumber.substring(4, 11)} ${accountNumber.substring(11)}';
    }
    return accountNumber;
  }
}

class UserProfile {
  final String firstName;
  final String fullName;
  final String username;
  final String email;
  final List<BankAccount> accounts;

  UserProfile({
    required this.firstName,
    required this.fullName,
    required this.username,
    required this.email,
    required this.accounts,
  });

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    final list = json['accounts'] as List? ?? [];
    return UserProfile(
      firstName: json['firstName']?.toString() ?? '',
      fullName: json['fullName']?.toString() ?? '',
      username: json['username']?.toString() ?? '',
      email: json['email']?.toString() ?? '',
      accounts: list.map((a) => BankAccount.fromJson(a as Map<String, dynamic>)).toList(),
    );
  }

  double get totalBalance {
    return accounts.fold(0.0, (sum, a) => sum + a.currentBalance);
  }
}

class AccountService {
  /// Fetches the live authenticated customer profile and accounts from the database
  static Future<UserProfile> fetchProfile({String? fallbackUsername}) async {
    final token = await SecureTokenStorage.getToken();
    final effectiveUsername = fallbackUsername ?? 'tsamson';

    if (token != null && token.isNotEmpty && !token.startsWith('mock_')) {
      try {
        final url = Uri.parse('${ApiConfig.baseUrl}/auth/banking/me');
        final response = await http.get(
          url,
          headers: {
            'Authorization': 'Bearer $token',
            'Content-Type': 'application/json',
          },
        ).timeout(ApiConfig.requestTimeout);

        if (response.statusCode == 200) {
          final data = jsonDecode(response.body);
          return UserProfile.fromJson(data);
        }
      } catch (_) {
        // Fall back to seed cache if backend offline
      }
    }

    return _fallbackProfile(effectiveUsername);
  }

  /// Fetches live customer transaction activity from the database
  static Future<List<TransactionItem>> fetchTransactions() async {
    final token = await SecureTokenStorage.getToken();
    if (token != null && token.isNotEmpty && !token.startsWith('mock_')) {
      try {
        final url = Uri.parse('${ApiConfig.baseUrl}/auth/banking/transactions');
        final response = await http.get(
          url,
          headers: {
            'Authorization': 'Bearer $token',
            'Content-Type': 'application/json',
          },
        ).timeout(ApiConfig.requestTimeout);

        if (response.statusCode == 200) {
          final List list = jsonDecode(response.body);
          if (list.isNotEmpty) {
            return list.map<TransactionItem>((item) {
              final amt = (item['amount'] is num)
                  ? (item['amount'] as num).toDouble()
                  : double.tryParse(item['amount']?.toString() ?? '0.0') ?? 0.0;
              final isCredit = item['operation']?.toString().toUpperCase() == 'CREDIT';
              return TransactionItem(
                id: 'TRX-${item['transactionId'] ?? DateTime.now().millisecondsSinceEpoch}',
                title: item['counterpartyName'] ?? item['reference'] ?? 'Banking Transfer',
                date: item['date']?.toString().split('T').first ?? 'Recent',
                account: 'Account ${item['accountNumber'] ?? ''}',
                amount: amt,
                isCredit: isCredit,
                ofscore: 'FUNDS.TRANSFER,AUTH/I/PROCESS,//${item['reference'] ?? 'TXN'},DEBIT.ACCT.NO=${item['accountNumber']},AMOUNT=${amt.toStringAsFixed(2)},CCY=PHP',
                status: item['status']?.toString() ?? 'COMPLETED',
              );
            }).toList();
          }
        }
      } catch (_) {
        // Fall back to default transaction items
      }
    }

    return [
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
    ];
  }

  static UserProfile _fallbackProfile(String username) {
    final lower = username.toLowerCase();
    if (lower == 'arosales') {
      return UserProfile(
        firstName: 'Aly',
        fullName: 'Aly Rosales',
        username: 'arosales',
        email: 'aly.rosales@paypink.ph',
        accounts: [
          BankAccount(
            accountId: 4,
            accountNumber: '001153954837',
            accountType: 'SAVINGS_ACCOUNT',
            currency: 'PHP',
            currentBalance: 84320.50,
            status: 'ACTIVE',
          ),
        ],
      );
    } else if (lower == 'lviernes') {
      return UserProfile(
        firstName: 'Levi',
        fullName: 'Levi Viernes',
        username: 'lviernes',
        email: 'jonlevi.jlv@gmail.com',
        accounts: [
          BankAccount(
            accountId: 1,
            accountNumber: '001161426604',
            accountType: 'SAVINGS_ACCOUNT',
            currency: 'PHP',
            currentBalance: 126950.00,
            status: 'ACTIVE',
          ),
          BankAccount(
            accountId: 2,
            accountNumber: '001361426602',
            accountType: 'CHECKING_ACCOUNT',
            currency: 'PHP',
            currentBalance: 50000.00,
            status: 'ACTIVE',
          ),
        ],
      );
    } else {
      // Default / tsamson (Trixie Samson)
      return UserProfile(
        firstName: 'Trixie',
        fullName: 'Trixie Samson',
        username: 'tsamson',
        email: 'tbsamson@eastwestbanker.com',
        accounts: [
          BankAccount(
            accountId: 7,
            accountNumber: '001259685046',
            accountType: 'EVERYDAY_ACCOUNT',
            currency: 'PHP',
            currentBalance: 50.00,
            status: 'ACTIVE',
          ),
          BankAccount(
            accountId: 6,
            accountNumber: '001159685047',
            accountType: 'SAVINGS_ACCOUNT',
            currency: 'PHP',
            currentBalance: 0.00,
            status: 'ACTIVE',
          ),
        ],
      );
    }
  }
}
