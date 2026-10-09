import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:paypink_mobile/src/features/transactions/domain/entities/transaction_entity.dart';
import 'package:paypink_mobile/src/features/transactions/domain/repositories/transaction_repository.dart';

// EVENTS
abstract class TransactionsEvent {}

class FetchTransactionsEvent extends TransactionsEvent {}

class FilterTransactionsEvent extends TransactionsEvent {
  final String query;
  final String filterCategory;
  FilterTransactionsEvent({required this.query, required this.filterCategory});
}

// STATES
abstract class TransactionsState {}

class TransactionsInitialState extends TransactionsState {}

class TransactionsLoadingState extends TransactionsState {}

class TransactionsLoadedState extends TransactionsState {
  final List<TransactionEntity> transactions;
  final List<TransactionEntity> filteredTransactions;
  TransactionsLoadedState({
    required this.transactions,
    required this.filteredTransactions,
  });
}

class TransactionsEmptyState extends TransactionsState {}

class TransactionsErrorState extends TransactionsState {
  final String message;
  TransactionsErrorState(this.message);
}

// BLOC
class TransactionsBloc extends Bloc<TransactionsEvent, TransactionsState> {
  final TransactionRepository transactionRepository;

  TransactionsBloc({required this.transactionRepository}) : super(TransactionsInitialState()) {
    on<FetchTransactionsEvent>((event, emit) async {
      emit(TransactionsLoadingState());
      try {
        final txs = await transactionRepository.fetchTransactions();
        if (txs.isEmpty) {
          emit(TransactionsEmptyState());
        } else {
          emit(TransactionsLoadedState(transactions: txs, filteredTransactions: txs));
        }
      } catch (e) {
        emit(TransactionsErrorState(e.toString()));
      }
    });

    on<FilterTransactionsEvent>((event, emit) {
      if (state is TransactionsLoadedState) {
        final current = (state as TransactionsLoadedState).transactions;
        final filtered = current.where((tx) {
          if (event.filterCategory == 'credit' && !tx.isCredit) return false;
          if (event.filterCategory == 'debit' && tx.isCredit) return false;
          if (event.filterCategory == 'reversal' && tx.status != 'REVERSED' && tx.status != 'FAILED_DLQ') return false;

          if (event.query.trim().isNotEmpty) {
            final q = event.query.toLowerCase();
            return tx.title.toLowerCase().contains(q) ||
                tx.id.toLowerCase().contains(q) ||
                tx.account.toLowerCase().contains(q);
          }
          return true;
        }).toList();

        if (filtered.isEmpty) {
          emit(TransactionsEmptyState());
        } else {
          emit(TransactionsLoadedState(transactions: current, filteredTransactions: filtered));
        }
      }
    });
  }
}
