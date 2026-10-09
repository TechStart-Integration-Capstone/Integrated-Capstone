import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:paypink_mobile/src/features/accounts/domain/entities/bank_account_entity.dart';
import 'package:paypink_mobile/src/features/accounts/domain/repositories/account_repository.dart';

// EVENTS
abstract class AccountsEvent {}

class FetchAccountsEvent extends AccountsEvent {}

// STATES
abstract class AccountsState {}

class AccountsInitialState extends AccountsState {}

class AccountsLoadingState extends AccountsState {}

class AccountsLoadedState extends AccountsState {
  final List<BankAccountEntity> accounts;
  AccountsLoadedState(this.accounts);
}

class AccountsEmptyState extends AccountsState {}

class AccountsErrorState extends AccountsState {
  final String message;
  AccountsErrorState(this.message);
}

// BLOC
class AccountsBloc extends Bloc<AccountsEvent, AccountsState> {
  final AccountRepository accountRepository;

  AccountsBloc({required this.accountRepository}) : super(AccountsInitialState()) {
    on<FetchAccountsEvent>((event, emit) async {
      emit(AccountsLoadingState());
      try {
        final accounts = await accountRepository.fetchAccounts();
        if (accounts.isEmpty) {
          emit(AccountsEmptyState());
        } else {
          emit(AccountsLoadedState(accounts));
        }
      } catch (e) {
        emit(AccountsErrorState(e.toString()));
      }
    });
  }
}
