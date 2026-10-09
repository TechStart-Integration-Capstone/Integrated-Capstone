import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/services/auth_service.dart';
import 'package:paypink_mobile/services/secure_token_storage.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // In-memory stand-in for flutter_secure_storage.
  final store = <String, String>{};

  setUp(() {
    store.clear();
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

  test('an MPIN belongs to the user who created it', () async {
    await SecureTokenStorage.savePin('123456', owner: 'lviernes');

    expect(await SecureTokenStorage.hasPinFor('LViernes'), isTrue);
    expect(await SecureTokenStorage.hasPinFor('arosales'), isFalse);
  });
}
