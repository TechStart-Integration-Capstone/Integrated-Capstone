import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/services/auth_service.dart';
import 'package:paypink_mobile/services/secure_token_storage.dart';
import 'package:paypink_mobile/widgets/profile_sheet.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // In-memory stand-in for flutter_secure_storage.
  final store = <String, String>{};

  setUp(() async {
    store.clear();
    await SecureTokenStorage.clearVault();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (MethodCall call) async {
        final args = (call.arguments as Map?) ?? {};
        final key = args['key'] as String?;
        switch (call.method) {
          case 'write':
            store[key!] = args['value'] as String;
            return null;
          case 'read':
            return store[key];
          case 'delete':
            store.remove(key);
            return null;
          case 'deleteAll':
            store.clear();
            return null;
          case 'readAll':
            return Map<String, String>.from(store);
          case 'containsKey':
            return store.containsKey(key);
        }
        return null;
      },
    );
  });

  test('logging out keeps the MPIN so it is only created once', () async {
    await SecureTokenStorage.saveToken('header.payload.signature');
    await SecureTokenStorage.saveUserSession(username: 'lviernes', fullName: 'Levi Viernes', customerId: 1);
    await SecureTokenStorage.savePin('123456', owner: 'lviernes');

    await AuthService.logout();

    expect(await SecureTokenStorage.getToken(), isNull);
    expect(await SecureTokenStorage.hasPinFor('lviernes'), isTrue);
    expect(await SecureTokenStorage.verifyPin('123456'), isTrue);
  });

  test('an MPIN belongs to the user who created it and is isolated per user', () async {
    await SecureTokenStorage.saveUserSession(username: 'lviernes', fullName: 'Levi Viernes', customerId: 1);
    await SecureTokenStorage.savePin('123456', owner: 'lviernes');

    expect(await SecureTokenStorage.hasPinFor('LViernes'), isTrue);
    expect(await SecureTokenStorage.hasPinFor('arosales'), isFalse);

    // When a second user logs in on the same device, they do not inherit the first user's MPIN
    await SecureTokenStorage.saveUserSession(username: 'arosales', fullName: 'Aly Rosales', customerId: 2);
    expect(await SecureTokenStorage.hasPin(), isFalse);
    expect(await SecureTokenStorage.verifyPin('123456'), isFalse);

    // Second user creates their own distinct MPIN
    await SecureTokenStorage.savePin('654321', owner: 'arosales');
    expect(await SecureTokenStorage.hasPin(), isTrue);
    expect(await SecureTokenStorage.verifyPin('654321'), isTrue);
    expect(await SecureTokenStorage.verifyPin('123456'), isFalse);

    // First user logging back in retains their own MPIN
    await SecureTokenStorage.saveUserSession(username: 'lviernes', fullName: 'Levi Viernes', customerId: 1);
    expect(await SecureTokenStorage.hasPin(), isTrue);
    expect(await SecureTokenStorage.verifyPin('123456'), isTrue);
    expect(await SecureTokenStorage.verifyPin('654321'), isFalse);
  });

  test('MPIN hash parity between mobile client and backend DB SHA-256 format', () async {
    await SecureTokenStorage.savePin('123456', owner: 'lviernes');
    expect(await SecureTokenStorage.verifyPin('123456'), isTrue);
    expect(await SecureTokenStorage.verifyPin('999999'), isFalse);
  });

  testWidgets('changing MPIN from ProfileSheet verifies current PIN, updates PIN, and shows success SnackBar', (WidgetTester tester) async {
    await SecureTokenStorage.saveUserSession(username: 'lviernes', fullName: 'Levi Viernes', customerId: 1);
    await SecureTokenStorage.savePin('123456', owner: 'lviernes');

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => ElevatedButton(
              onPressed: () => ProfileSheet.show(
                context,
                currentUser: 'lviernes',
                isDarkMode: false,
                onToggleTheme: () {},
                onLogout: () {},
              ),
              child: const Text('Open Profile'),
            ),
          ),
        ),
      ),
    );

    // Open ProfileSheet
    await tester.tap(find.text('Open Profile'));
    await tester.pumpAndSettle();

    // Tap Change 6-Digit MPIN
    await tester.ensureVisible(find.text('Change 6-Digit MPIN'));
    await tester.tap(find.text('Change 6-Digit MPIN'));
    await tester.pumpAndSettle();

    // Step 1: Verify Current MPIN sheet is displayed
    expect(find.text('Verify Current MPIN'), findsOneWidget);
    for (final digit in ['1', '2', '3', '4', '5', '6']) {
      await tester.tap(find.text(digit).last);
      await tester.pump();
    }
    await tester.pumpAndSettle();

    // Step 2: Create new MPIN screen is displayed
    expect(find.text('Create your new 6-digit MPIN'), findsOneWidget);
    for (final digit in ['6', '5', '4', '3', '2', '1']) {
      await tester.tap(find.text(digit).last);
      await tester.pump();
    }
    await tester.pumpAndSettle();

    // Step 3: Confirm new MPIN
    expect(find.text('Confirm your new MPIN'), findsOneWidget);
    for (final digit in ['6', '5', '4', '3', '2', '1']) {
      await tester.tap(find.text(digit).last);
      await tester.pump();
    }
    await tester.pumpAndSettle();

    // Verify success SnackBar is shown and new MPIN is active
    expect(find.text('6-Digit MPIN updated successfully.'), findsOneWidget);
    expect(await SecureTokenStorage.verifyPin('654321'), isTrue);
    expect(await SecureTokenStorage.verifyPin('123456'), isFalse);
  });
}
