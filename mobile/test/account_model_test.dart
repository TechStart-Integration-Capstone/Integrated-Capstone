import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/services/account_service.dart';

BankAccount _account(String type, double balance) => BankAccount(
      accountId: 1,
      accountNumber: '001196394082',
      accountType: type,
      currency: 'PHP',
      currentBalance: balance,
      status: 'ACTIVE',
    );

void main() {
  group('Savings interest follows transaction-service InterestPolicy tiers', () {
    test('1% below 1,000, 2.5% below 10,000, 4% from 10,000', () {
      expect(_account('SAVINGS_ACCOUNT', 999.99).annualInterestRatePercent, 1.0);
      expect(_account('SAVINGS_ACCOUNT', 1000).annualInterestRatePercent, 2.5);
      expect(_account('SAVINGS', 9999.99).annualInterestRatePercent, 2.5);
      expect(_account('SAVINGS_ACCOUNT', 10000).annualInterestRatePercent, 4.0);
    });

    test('checking accounts earn no interest', () {
      final checking = _account('CHECKING_ACCOUNT', 50000);
      expect(checking.annualInterestRatePercent, isNull);
      expect(checking.estimatedDailyInterest, 0.0);
    });

    test('daily accrual is balance x rate / 365', () {
      expect(_account('SAVINGS_ACCOUNT', 36500).estimatedDailyInterest, closeTo(4.0, 1e-9));
    });

    test('interest posts on the last day of the month', () {
      expect(BankAccount.nextInterestPostingDate(DateTime(2026, 10, 9)), DateTime(2026, 10, 31));
      expect(BankAccount.nextInterestPostingDate(DateTime(2028, 2, 3)), DateTime(2028, 2, 29));
    });
  });

  group('Account JSON never invents loan figures', () {
    test('loan without payment or due date stays null', () {
      final loan = BankAccount.fromJson({
        'accountId': 7,
        'accountNumber': 'LN-1',
        'accountType': 'LOAN_ACCOUNT',
        'currentBalance': 25000,
      });
      expect(loan.minimumPayment, isNull);
      expect(loan.dueDate, isNull);
      expect(loan.outstandingDebt, 25000);
    });

    test('string amounts are parsed', () {
      final acct = BankAccount.fromJson({'accountType': 'SAVINGS_ACCOUNT', 'currentBalance': '1500.50'});
      expect(acct.currentBalance, 1500.50);
    });
  });

  group('loan-service LoanSummary mapping', () {
    final loan = BankAccount.fromLoanJson({
      'loanId': 42,
      'referenceNo': 'LN-202610-0042',
      'accountNo': '001196394082',
      'principal': 50000,
      'annualRate': 7.0,
      'termMonths': 12,
      'monthlyInstallment': 4326.24,
      'outstandingPrincipal': 45000,
      'penaltyDue': 86.52,
      'status': 'OVERDUE',
      'nextDue': {'dueDate': '2026-10-28', 'amount': 4326.24},
    });

    test('uses the loan ID and linked repayment account from loan-service', () {
      expect(loan.loanId, 42);
      expect(loan.isLoan, isTrue);
      expect(loan.accountNumber, 'LN-202610-0042');
      expect(loan.repaymentAccountNumber, '001196394082');
    });

    test('next payment includes penalty, due date and rate are real', () {
      expect(loan.minimumPayment, closeTo(4412.76, 1e-9));
      expect(loan.dueDate, 'Oct 28, 2026');
      expect(loan.interestRate, 7.0);
      expect(loan.annualInterestRatePercent, 7.0);
      expect(loan.outstandingDebt, 45000);
      expect(loan.termMonths, 12);
    });

    test('a loan with no next installment has no due date', () {
      final paidUp = BankAccount.fromLoanJson({'loanId': 1, 'referenceNo': 'LN-1', 'status': 'ACTIVE', 'outstandingPrincipal': 0});
      expect(paidUp.dueDate, isNull);
      expect(paidUp.minimumPayment, isNull);
    });
  });

  test('LoanInstallment remaining excludes what was already paid', () {
    final row = LoanInstallment.fromJson({
      'installmentNo': 3,
      'dueDate': '2026-12-28',
      'totalDue': 4326.24,
      'amountPaid': 1000,
      'status': 'PENDING',
    });
    expect(row.remaining, closeTo(3326.24, 1e-9));
    expect(row.dueDate, 'Dec 28, 2026');
    expect(row.isPaid, isFalse);
  });

  test('formatPeso uses comma grouping', () {
    expect(formatPeso(1500), '₱1,500.00');
    expect(formatPeso(1234567.891), '₱1,234,567.89');
  });
}
