import 'package:get_it/get_it.dart';
import 'package:paypink_mobile/src/core/network/dio_client.dart';
import 'package:paypink_mobile/src/core/security/session_manager.dart';
import 'package:paypink_mobile/src/core/telemetry/telemetry_service.dart';
import 'package:paypink_mobile/src/features/accounts/data/repositories/account_repository_impl.dart';
import 'package:paypink_mobile/src/features/accounts/domain/repositories/account_repository.dart';
import 'package:paypink_mobile/src/features/accounts/presentation/bloc/accounts_bloc.dart';
import 'package:paypink_mobile/src/features/auth/data/repositories/auth_repository_impl.dart';
import 'package:paypink_mobile/src/features/auth/domain/repositories/auth_repository.dart';
import 'package:paypink_mobile/src/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:paypink_mobile/src/features/remittance/data/repositories/remittance_repository_impl.dart';
import 'package:paypink_mobile/src/features/remittance/domain/repositories/remittance_repository.dart';
import 'package:paypink_mobile/src/features/remittance/presentation/bloc/remittance_bloc.dart';
import 'package:paypink_mobile/src/features/transactions/data/repositories/transaction_repository_impl.dart';
import 'package:paypink_mobile/src/features/transactions/domain/repositories/transaction_repository.dart';
import 'package:paypink_mobile/src/features/transactions/presentation/bloc/transactions_bloc.dart';

final sl = GetIt.instance;

/// Initialize Service Locator Dependency Injection (GetIt)
Future<void> initServiceLocator() async {
  // 0. Core Telemetry & Security
  sl.registerLazySingleton<TelemetryService>(() => TelemetryService());
  sl.registerLazySingleton<SessionManager>(
    () => SessionManager(
      inactivityTimeout: const Duration(minutes: 3),
      onSessionLocked: () {
        sl<TelemetryService>().logWarning(
          'Session auto-locked due to inactivity or backgrounding.',
          category: 'SECURITY',
        );
      },
    ),
  );

  // 1. Core Network
  sl.registerLazySingleton<DioClient>(
    () => DioClient(telemetryService: sl<TelemetryService>()),
  );


  // 2. Auth Domain & Feature
  sl.registerLazySingleton<AuthRepository>(
    () => AuthRepositoryImpl(dioClient: sl()),
  );
  sl.registerFactory<AuthBloc>(
    () => AuthBloc(authRepository: sl()),
  );

  // 3. Accounts Domain & Feature
  sl.registerLazySingleton<AccountRepository>(
    () => AccountRepositoryImpl(dioClient: sl()),
  );
  sl.registerFactory<AccountsBloc>(
    () => AccountsBloc(accountRepository: sl()),
  );

  // 4. Remittance Domain & Feature
  sl.registerLazySingleton<RemittanceRepository>(
    () => RemittanceRepositoryImpl(dioClient: sl()),
  );
  sl.registerFactory<RemittanceBloc>(
    () => RemittanceBloc(remittanceRepository: sl()),
  );

  // 5. Transactions Domain & Feature
  sl.registerLazySingleton<TransactionRepository>(
    () => TransactionRepositoryImpl(dioClient: sl()),
  );
  sl.registerFactory<TransactionsBloc>(
    () => TransactionsBloc(transactionRepository: sl()),
  );
}
