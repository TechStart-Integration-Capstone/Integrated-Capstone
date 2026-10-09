import 'package:flutter_test/flutter_test.dart';
import 'package:paypink_mobile/src/features/auth/domain/entities/user_entity.dart';
import 'package:paypink_mobile/src/features/auth/domain/repositories/auth_repository.dart';
import 'package:paypink_mobile/src/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:paypink_mobile/src/features/accounts/domain/entities/bank_account_entity.dart';
import 'package:paypink_mobile/src/features/accounts/domain/repositories/account_repository.dart';
import 'package:paypink_mobile/src/features/accounts/presentation/bloc/accounts_bloc.dart';

class FakeAuthRepository implements AuthRepository {
  final bool shouldFail;
  FakeAuthRepository({this.shouldFail = false});

  @override
  Future<UserEntity> login(String username, String password) async {
    if (shouldFail) {
      throw Exception('Invalid credentials');
    }
    return UserEntity(
      username: username,
      email: '$username@paypink.com',
      status: 'ACTIVE',
    );
  }

  @override
  Future<void> logout() async {}
}

class FakeAccountRepository implements AccountRepository {
  final List<BankAccountEntity> mockAccounts;
  final bool shouldFail;

  FakeAccountRepository({this.mockAccounts = const <BankAccountEntity>[], this.shouldFail = false});

  @override
  Future<List<BankAccountEntity>> fetchAccounts() async {
    if (shouldFail) {
      throw Exception('Account service unavailable');
    }
    return mockAccounts;
  }
}

void main() {
  group('AuthBloc Unit Tests', () {
    test('initial state is AuthInitialState', () {
      final bloc = AuthBloc(authRepository: FakeAuthRepository());
      expect(bloc.state, isA<AuthInitialState>());
    });

    test('emits [AuthLoadingState, AuthenticatedState] on successful LoginSubmittedEvent', () async {
      final bloc = AuthBloc(authRepository: FakeAuthRepository());

      expectLater(
        bloc.stream,
        emitsInOrder([
          isA<AuthLoadingState>(),
          isA<AuthenticatedState>(),
        ]),
      );

      bloc.add(LoginSubmittedEvent(username: 'lviernes', password: 'password123'));
    });

    test('emits [AuthLoadingState, AuthErrorState] on failed LoginSubmittedEvent', () async {
      final bloc = AuthBloc(authRepository: FakeAuthRepository(shouldFail: true));

      expectLater(
        bloc.stream,
        emitsInOrder([
          isA<AuthLoadingState>(),
          isA<AuthErrorState>(),
        ]),
      );

      bloc.add(LoginSubmittedEvent(username: 'invalid', password: 'wrong'));
    });

    test('emits [AuthLoadingState, UnauthenticatedState] on LogoutEvent', () async {
      final bloc = AuthBloc(authRepository: FakeAuthRepository());

      expectLater(
        bloc.stream,
        emitsInOrder([
          isA<AuthLoadingState>(),
          isA<UnauthenticatedState>(),
        ]),
      );

      bloc.add(LogoutEvent());
    });
  });

  group('AccountsBloc Unit Tests', () {
    test('initial state is AccountsInitialState', () {
      final bloc = AccountsBloc(accountRepository: FakeAccountRepository());
      expect(bloc.state, isA<AccountsInitialState>());
    });

    test('emits [AccountsLoadingState, AccountsLoadedState] when accounts are returned', () async {
      final List<BankAccountEntity> mockAccts = [
        const BankAccountEntity(
          accountId: 1,
          accountNumber: '001196394082',
          displayName: 'Everyday Checking',
          currentBalance: 50000.0,
          accountType: 'EVERYDAY',
          status: 'ACTIVE',
        ),
      ];

      final bloc = AccountsBloc(accountRepository: FakeAccountRepository(mockAccounts: mockAccts));


      expectLater(
        bloc.stream,
        emitsInOrder([
          isA<AccountsLoadingState>(),
          isA<AccountsLoadedState>(),
        ]),
      );

      bloc.add(FetchAccountsEvent());
    });

    test('emits [AccountsLoadingState, AccountsEmptyState] when no accounts exist', () async {
      final bloc = AccountsBloc(accountRepository: FakeAccountRepository(mockAccounts: []));

      expectLater(
        bloc.stream,
        emitsInOrder([
          isA<AccountsLoadingState>(),
          isA<AccountsEmptyState>(),
        ]),
      );

      bloc.add(FetchAccountsEvent());
    });

    test('emits [AccountsLoadingState, AccountsErrorState] on repository error', () async {
      final bloc = AccountsBloc(accountRepository: FakeAccountRepository(shouldFail: true));

      expectLater(
        bloc.stream,
        emitsInOrder([
          isA<AccountsLoadingState>(),
          isA<AccountsErrorState>(),
        ]),
      );

      bloc.add(FetchAccountsEvent());
    });
  });
}
