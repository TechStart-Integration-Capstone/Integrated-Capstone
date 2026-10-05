import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/main.dart';

void main() {
  testWidgets('PayPink Mobile App initial screen is Login/Register', (WidgetTester tester) async {
    await tester.pumpWidget(const PayPinkMobileApp(initialAuthenticated: false));
    await tester.pumpAndSettle();

    // Verify Login Screen elements
    expect(find.text('PayPink®'), findsOneWidget);
    expect(find.text('Sign In'), findsWidgets);
    expect(find.text('Register'), findsOneWidget);
    expect(find.text('Username or Account Number'), findsOneWidget);
  });

  testWidgets('PayPink Mobile App authenticated mode renders overview and navigation', (WidgetTester tester) async {
    await tester.pumpWidget(const PayPinkMobileApp(initialAuthenticated: true));
    await tester.pumpAndSettle();

    // Verify Brand title and greeting
    expect(find.text('PayPink®'), findsOneWidget);
    expect(find.text('Hello, Trixie.'), findsOneWidget);
    expect(find.text('₱50.00'), findsWidgets);

    // Verify Navigation items
    expect(find.text('Overview'), findsOneWidget);
    expect(find.text('Accounts'), findsOneWidget);
    expect(find.text('Transfer'), findsOneWidget);
    expect(find.text('Activity'), findsOneWidget);
  });
}
