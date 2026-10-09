import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:paypink_mobile/src/features/remittance/domain/entities/remittance_entity.dart';
import 'package:paypink_mobile/src/features/remittance/domain/repositories/remittance_repository.dart';

// EVENTS
abstract class RemittanceEvent {}

class SubmitRemittanceEvent extends RemittanceEvent {
  final String sourceAccount;
  final String recipientAccount;
  final double amount;
  final String channel;

  SubmitRemittanceEvent({
    required this.sourceAccount,
    required this.recipientAccount,
    required this.amount,
    required this.channel,
  });
}

// STATES
abstract class RemittanceState {}

class RemittanceInitialState extends RemittanceState {}

class RemittanceLoadingState extends RemittanceState {}

class RemittanceSuccessState extends RemittanceState {
  final RemittanceEntity remittance;
  RemittanceSuccessState(this.remittance);
}

class RemittanceFraudBlockedState extends RemittanceState {
  final String reason;
  RemittanceFraudBlockedState(this.reason);
}

class RemittanceErrorState extends RemittanceState {
  final String message;
  RemittanceErrorState(this.message);
}

// BLOC
class RemittanceBloc extends Bloc<RemittanceEvent, RemittanceState> {
  final RemittanceRepository remittanceRepository;

  RemittanceBloc({required this.remittanceRepository}) : super(RemittanceInitialState()) {
    on<SubmitRemittanceEvent>((event, emit) async {
      emit(RemittanceLoadingState());
      try {
        final result = await remittanceRepository.executeRemittance(
          sourceAccount: event.sourceAccount,
          recipientAccount: event.recipientAccount,
          amount: event.amount,
          channel: event.channel,
        );

        if (result.status.contains('FRAUD') || result.status.contains('REJECT')) {
          emit(RemittanceFraudBlockedState('Transaction blocked by Real-Time Fraud Engine.'));
        } else {
          emit(RemittanceSuccessState(result));
        }
      } catch (e) {
        emit(RemittanceErrorState(e.toString().replaceAll('Exception: ', '')));
      }
    });
  }
}
