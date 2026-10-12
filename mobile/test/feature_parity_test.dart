import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/screens/transactions_screen.dart';
import 'package:paypink_mobile/services/account_service.dart';
import 'package:paypink_mobile/services/notification_service.dart';
import 'package:paypink_mobile/services/report_service.dart';
import 'package:paypink_mobile/services/savings_service.dart';

Map<String, dynamic> loanJson({
  String status = 'ACTIVE',
  num penalty = 0,
  Map<String, dynamic>? lastAutoDebit,
  String? nextDue = '2026-11-01',
}) =>
    {
      'loanId': 7,
      'referenceNo': 'LN-7',
      'accountNo': '001181233469',
      'status': status,
      'outstandingPrincipal': 20000,
      'penaltyDue': penalty,
      'annualRate': 12,
      'termMonths': 12,
      if (nextDue != null) 'nextDue': {'amount': 1800, 'dueDate': nextDue},
      if (lastAutoDebit != null) 'lastAutoDebit': lastAutoDebit,
    };

void main() {
  group('Missed loan auto-debit (same rule as web loans.js missedAutoDebit)', () {
    final failed = {'status': 'INSUFFICIENT_FUNDS', 'amount': 1800, 'date': '2026-10-01'};

    test('overdue loan with a short-balance debit is flagged', () {
      final loan = BankAccount.fromLoanJson(loanJson(status: 'OVERDUE', lastAutoDebit: failed));
      expect(loan.hasMissedAutoDebit, isTrue);
      expect(loan.missedAutoDebitAmount, 1800);
    });

    test('a penalty also flags it', () {
      expect(BankAccount.fromLoanJson(loanJson(penalty: 36, lastAutoDebit: failed)).hasMissedAutoDebit, isTrue);
    });

    test('next due on or before the failed debit date flags it', () {
      expect(BankAccount.fromLoanJson(loanJson(nextDue: '2026-10-01', lastAutoDebit: failed)).hasMissedAutoDebit, isTrue);
    });

    test('a later installment after a settled shortfall is not flagged', () {
      expect(BankAccount.fromLoanJson(loanJson(lastAutoDebit: failed)).hasMissedAutoDebit, isFalse);
    });

    test('closed loans and successful debits are never flagged', () {
      expect(BankAccount.fromLoanJson(loanJson(status: 'CLOSED', lastAutoDebit: failed)).hasMissedAutoDebit, isFalse);
      final ok = {'status': 'SUCCESS', 'amount': 1800, 'date': '2026-10-01'};
      expect(BankAccount.fromLoanJson(loanJson(status: 'OVERDUE', lastAutoDebit: ok)).hasMissedAutoDebit, isFalse);
    });
  });

  group('Notifications come from server transactions and PinkCircle invitations', () {
    TransactionItem tx(String id, String type, String status, {bool credit = false}) => TransactionItem(
          id: id,
          title: 't',
          date: 'Today',
          account: 'Savings •••• 3469',
          amount: 1500,
          isCredit: credit,
          transactionType: type,
          counterparty: 'Maria Santos',
          status: status,
        );

    test('maps types and statuses like the web inbox', () {
      final items = NotificationService.fromTransactions([
        tx('1', 'TRANSFER_IN', 'Completed', credit: true),
        tx('2', 'TRANSFER_OUT', 'Completed'),
        tx('3', 'TRANSFER_OUT', 'REVERSED'),
        tx('4', 'EXT_INSTAPAY_BDO', 'PENDING'),
        tx('5', 'LOAN_DISBURSEMENT', 'Completed', credit: true),
        tx('6', 'INTEREST', 'Completed', credit: true),
      ]);
      expect(items.map((n) => n['title']), [
        'Money received',
        'Money sent',
        'Transfer reversed',
        'Transfer pending',
        'Loan approved and received',
      ]);
      expect(items.every((n) => n['unread'] == true), isTrue);
    });

    test('includes pending PinkCircle invitations in notifications', () {
      final invitedCircle = PinkCircleItem.fromJson({
        'circle_id': 'CIR-77',
        'name': 'Baguio Trip',
        'target_amount': 30000.0,
        'savedAmount': 5000.0,
        'target_date': '2026-12-15',
        'membership_status': 'INVITED',
        'members': [],
      });
      final activeCircle = PinkCircleItem.fromJson({
        'circle_id': 'CIR-88',
        'name': 'Active Squad',
        'target_amount': 20000.0,
        'savedAmount': 5000.0,
        'membership_status': 'ACTIVE',
        'members': [],
      });
      final items = NotificationService.fromTransactions(
        [tx('PP-20261012-1-OUT', 'TRANSFER_OUT', 'Completed')],
        circles: [invitedCircle, activeCircle],
      );
      expect(items, hasLength(2));
      expect(items.first['id'], 'CIRCLE_INVITE:CIR-77');
      expect(items.first['title'], 'PinkCircle invitation');
      expect(items.first['message'], contains('Baguio Trip'));
      expect(items.first['unread'], isTrue);
    });

    test('read and dismissed state is applied by ID', () {
      final items = NotificationService.fromTransactions(
        [tx('1', 'TRANSFER_IN', 'Completed', credit: true), tx('2', 'TRANSFER_OUT', 'Completed')],
        read: {'1:COMPLETED'},
        hidden: {'2:COMPLETED'},
      );
      expect(items, hasLength(1));
      expect(items.single['unread'], isFalse);
    });
  });

  test('AccountSavingsBreakdown computes totalAllocated from personal, circle, and unlisted reservations', () {
    final breakdown = AccountSavingsBreakdown.fromJson({
      'accountBalance': 50000,
      'personalReserved': 12000,
      'circleReserved': 3500,
      'unlistedReservations': 0,
      'availableBalance': 34500,
      'allocations': [
        {'goal_id': 'G-1', 'name': 'Emergency', 'saved_amount': 12000, 'goal_type': 'PERSONAL'},
        {'goal_id': 'G-2', 'name': 'Trip Circle', 'saved_amount': 3500, 'goal_type': 'CIRCLE'},
      ],
    });
    expect(breakdown.totalAllocated, 15500);
    expect(breakdown.availableBalance, 34500);
    expect(breakdown.accountBalance, 50000);
    expect(breakdown.allocations, hasLength(2));
  });

  test('report date range follows the web limit of 366 days', () {
    expect(ReportService.validateRange(DateTime(2026, 1, 1), DateTime(2026, 12, 31)), isNull);
    expect(ReportService.validateRange(DateTime(2026, 1, 2), DateTime(2026, 1, 1)), isNotNull);
    expect(ReportService.validateRange(DateTime(2025, 1, 1), DateTime(2026, 1, 3)), isNotNull);
  });

  test('payments left and payoff come from the schedule (same as web loans.js)', () {
    LoanInstallment row(int n, double due, double paid, String status) => LoanInstallment(
          installmentNo: n, dueDate: '2026-1$n-01', totalDue: due, amountPaid: paid, status: status);
    final loan = BankAccount.fromLoanJson(loanJson(penalty: 36)).withSchedule([
      row(1, 1800, 1800, 'PAID'),
      row(2, 1800, 500, 'OVERDUE'),
      row(3, 1800, 0, 'PENDING'),
    ]);
    expect(loan.paymentsRemaining, 2);
    expect(loan.paymentsTotal, 3);
    expect(loan.paymentsLeftLabel, '2 of 3 monthly payments left');
    // penalty 36 + (1800 - 500) + 1800
    expect(loan.payoffAmount, 3136);
  });
}

