import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:paypink_mobile/src/features/auth/domain/entities/user_entity.dart';
import 'package:paypink_mobile/src/features/auth/domain/repositories/auth_repository.dart';

// EVENTS
abstract class AuthEvent {}

class LoginSubmittedEvent extends AuthEvent {
  final String username;
  final String password;
  LoginSubmittedEvent({required this.username, required this.password});
}

class LogoutEvent extends AuthEvent {}

// STATES
abstract class AuthState {}

class AuthInitialState extends AuthState {}

class AuthLoadingState extends AuthState {}

class AuthenticatedState extends AuthState {
  final UserEntity user;
  AuthenticatedState(this.user);
}

class UnauthenticatedState extends AuthState {}

class AuthErrorState extends AuthState {
  final String message;
  AuthErrorState(this.message);
}

// BLOC
class AuthBloc extends Bloc<AuthEvent, AuthState> {
  final AuthRepository authRepository;

  AuthBloc({required this.authRepository}) : super(AuthInitialState()) {
    on<LoginSubmittedEvent>((event, emit) async {
      emit(AuthLoadingState());
      try {
        final user = await authRepository.login(event.username, event.password);
        emit(AuthenticatedState(user));
      } catch (e) {
        emit(AuthErrorState(e.toString().replaceAll('Exception: ', '')));
      }
    });

    on<LogoutEvent>((event, emit) async {
      emit(AuthLoadingState());
      await authRepository.logout();
      emit(UnauthenticatedState());
    });
  }
}
