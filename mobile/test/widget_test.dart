import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/main.dart';

void main() {
  testWidgets('PayPink Mobile App smoke test renders overview and navigation', (WidgetTester tester) async {
    await tester.pumpWidget(const PayPinkMobileApp());
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
