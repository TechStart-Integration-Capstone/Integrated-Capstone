import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/screens/savings_hub_screen.dart';
import 'package:paypink_mobile/services/account_service.dart';
import 'package:paypink_mobile/services/savings_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (MethodCall methodCall) async => null,
    );
  });

  final savingsAccount = BankAccount(
    accountId: 1,
    accountNumber: '001181233469',
    accountType: 'SAVINGS_ACCOUNT',
    currency: 'PHP',
    currentBalance: 50000,
    status: 'ACTIVE',
  );

  testWidgets('SavingsHubScreen renders title, tabs and category actions', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: SavingsHubScreen(initialAccounts: [savingsAccount]),
    ));
    await tester.pump();

    expect(find.text('Savings Hub'), findsOneWidget);
    expect(find.text('My Savings'), findsOneWidget);
    expect(find.text('PinkCircles'), findsOneWidget);
    expect(find.text('Make room for your first goal'), findsOneWidget);
    expect(find.text('New Goal'), findsOneWidget);

    await tester.tap(find.text('PinkCircles'));
    await tester.pump();
    expect(find.text('Create Circle'), findsOneWidget);
  });

  testWidgets('SavingsHubScreen is constrained to 440px on wide viewports', (tester) async {
    tester.view.physicalSize = const Size(1200, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(MaterialApp(
      home: SavingsHubScreen(initialAccounts: [savingsAccount]),
    ));
    await tester.pump();

    final appBarFinder = find.byType(AppBar);
    expect(appBarFinder, findsOneWidget);
    final appBarSize = tester.getSize(appBarFinder);
    expect(appBarSize.width, lessThanOrEqualTo(440.0));
  });

  test('SavingsGoalItem parses JSON matching backend contract', () {
    final json = {
      'goal_id': '101',
      'name': 'Japan Trip',
      'category': 'TRAVEL',
      'target_amount': 80000.0,
      'savedAmount': 20000.0,
      'target_date': '2026-12-31',
      'account_id': 1,
    };

    final item = SavingsGoalItem.fromJson(json);
    expect(item.goalId, '101');
    expect(item.name, 'Japan Trip');
    expect(item.category, 'TRAVEL');
    expect(item.targetAmount, 80000.0);
    expect(item.savedAmount, 20000.0);
    expect(item.progressPercent, 25);
    expect(item.isCompleted, false);
  });

  test('PinkCircleItem parses JSON matching backend contract', () {
    final json = {
      'circle_id': '202',
      'name': 'Squad Concert Fund',
      'target_amount': 50000.0,
      'savedAmount': 15000.0,
      'membership_status': 'ACTIVE',
      'admin_customer_id': 1,
      'members': [
        {
          'customer_id': 1,
          'first_name': 'Levi',
          'last_name': 'Viernes',
          'status': 'ACTIVE',
          'target_amount': 25000.0,
          'savedAmount': 10000.0,
          'goal_id': '101',
          'share_progress': true,
        },
        {
          'customer_id': 2,
          'first_name': 'Maria',
          'last_name': 'Santos',
          'status': 'INVITED',
          'target_amount': 25000.0,
          'savedAmount': 5000.0,
          'share_progress': false,
        }
      ]
    };

    final circle = PinkCircleItem.fromJson(json);
    expect(circle.circleId, '202');
    expect(circle.name, 'Squad Concert Fund');
    expect(circle.isActive, true);
    expect(circle.members.length, 2);
    expect(circle.progressPercent, 30);
    expect(circle.members.first.fullName, 'Levi Viernes');
    expect(circle.members.first.isYou, true);
  });

  test('SavingsActivityItem parses JSON matching backend app.SAVINGS_ACTIVITY', () {
    final json = {
      'operation_id': 'OP-998811',
      'customer_id': 1,
      'account_id': 11,
      'status': 'CONFIRMED',
      'request_json': '{"command":{"type":"ALLOCATE","lines":[{"goalId":"101","amount":1500.0}]}}',
      'created_at': '2026-10-12T02:15:00Z',
    };

    final items = SavingsActivityItem.fromListJson(json);
    expect(items.length, 1);
    final act = items.first;
    expect(act.operationId, 'OP-998811');
    expect(act.isAllocate, true);
    expect(act.isRelease, false);
    expect(act.amount, 1500.0);
    expect(act.goalId, '101');
    expect(act.status, 'CONFIRMED');
  });

  test('SavingsActivityItem parses multi-line split or RELEASE', () {
    final json = {
      'operation_id': 'OP-998812',
      'customer_id': 1,
      'account_id': 11,
      'status': 'CONFIRMED',
      'request_json': '{"command":{"type":"RELEASE","lines":[{"goalId":"101","amount":500.0}]}}',
      'created_at': '2026-10-12T03:00:00Z',
    };

    final items = SavingsActivityItem.fromListJson(json);
    expect(items.length, 1);
    final act = items.first;
    expect(act.isRelease, true);
    expect(act.amount, 500.0);
  });
}
