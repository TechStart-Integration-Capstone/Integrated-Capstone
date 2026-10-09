import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/screens/loans_screen.dart';
import 'package:paypink_mobile/services/account_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (MethodCall methodCall) async => null,
    );
  });

  final loan = BankAccount.fromLoanJson({
    'loanId': 7,
    'referenceNo': 'LN-2026-0007',
    'accountNo': '001181233469',
    'status': 'ACTIVE',
    'outstandingPrincipal': 20000,
    'penaltyDue': 0,
    'annualRate': 12,
    'termMonths': 12,
    'nextDue': {'amount': 1800, 'dueDate': '2026-11-01'},
  }).withSchedule([
    LoanInstallment(installmentNo: 1, dueDate: '2026-10-01', totalDue: 1800, amountPaid: 1800, status: 'PAID'),
    LoanInstallment(installmentNo: 2, dueDate: '2026-11-01', totalDue: 1800, amountPaid: 0, status: 'PENDING'),
  ]);
  final savings = BankAccount(
    accountId: 1,
    accountNumber: '001181233469',
    accountType: 'SAVINGS_ACCOUNT',
    currency: 'PHP',
    currentBalance: 5000,
    status: 'ACTIVE',
  );

  testWidgets('Loans hub shows loans with Pay, Details and Apply in one place', (tester) async {
    await tester.pumpWidget(MaterialApp(home: LoansScreen(initialAccounts: [savings, loan])));
    await tester.pump();

    expect(find.text('Loans'), findsOneWidget);
    expect(find.text('Your loans'), findsOneWidget);
    expect(find.text('1 of 2 monthly payments left'), findsOneWidget);
    expect(find.text('Pay'), findsOneWidget);
    expect(find.text('Details & schedule'), findsOneWidget);
    expect(find.text('Apply for a loan'), findsOneWidget);
  });

  testWidgets('Without loans it shows an empty state and Apply', (tester) async {
    await tester.pumpWidget(MaterialApp(home: LoansScreen(initialAccounts: [savings])));
    await tester.pump();

    expect(find.text('No active loans'), findsOneWidget);
    expect(find.text('Apply for a loan'), findsOneWidget);
    expect(find.text('Pay'), findsNothing);
  });

  testWidgets('Loans hub AppBar and content are constrained to 440px on wide viewports', (tester) async {
    tester.view.physicalSize = const Size(1000, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(MaterialApp(home: LoansScreen(initialAccounts: [savings, loan])));
    await tester.pump();

    final appBarFinder = find.byType(AppBar);
    expect(appBarFinder, findsOneWidget);
    final appBarSize = tester.getSize(appBarFinder);
    expect(appBarSize.width, lessThanOrEqualTo(440.0));

    final appBarTopLeft = tester.getTopLeft(appBarFinder);
    expect(appBarTopLeft.dx, greaterThanOrEqualTo(250.0));
  });
}
