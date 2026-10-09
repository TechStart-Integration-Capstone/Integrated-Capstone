import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../services/circuit_breaker_client.dart';
import '../services/secure_token_storage.dart';

class CircuitBreakerScreen extends StatelessWidget {
  final VoidCallback onRecover;

  const CircuitBreakerScreen({super.key, required this.onRecover});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFFBF7F8),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 32.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Container(
                width: 76,
                height: 76,
                decoration: BoxDecoration(
                  color: PayPinkTheme.redBg,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: PayPinkTheme.red.withValues(alpha: 0.15),
                      blurRadius: 20,
                      spreadRadius: 4,
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.power_off_rounded,
                  color: PayPinkTheme.red,
                  size: 38,
                ),
              ),
              const SizedBox(height: 24),
              Text(
                'Service Temporarily Unavailable',
                textAlign: TextAlign.center,
                style: PayPinkTheme.display(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: PayPinkTheme.wineDark,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                'We could not connect to PayPink services right now. Your account data remains fully protected with automated security guards.',
                textAlign: TextAlign.center,
                style: PayPinkTheme.body(
                  fontSize: 12.5,
                  color: PayPinkTheme.muted,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 28),
              FutureBuilder<double>(
                future: SecureTokenStorage.getCachedBalance(),
                builder: (context, snapshot) {
                  final cached = snapshot.data ?? 50.00;
                  return Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(18),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: PayPinkTheme.line),
                      boxShadow: [
                        BoxShadow(
                          color: PayPinkTheme.wine.withValues(alpha: 0.05),
                          blurRadius: 16,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'LAST SAVED BALANCE',
                          style: PayPinkTheme.mono(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: PayPinkTheme.wine,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '₱${cached.toStringAsFixed(2)}',
                          style: PayPinkTheme.display(
                            fontSize: 26,
                            fontWeight: FontWeight.w800,
                            color: PayPinkTheme.ink,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Encrypted offline security snapshot.',
                          style: PayPinkTheme.body(
                            fontSize: 11,
                            color: PayPinkTheme.muted,
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
              const SizedBox(height: 32),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton.icon(
                  onPressed: () {
                    CircuitBreakerClient().attemptRecovery();
                    onRecover();
                  },
                  icon: const Icon(Icons.refresh_rounded),
                  label: const Text('Try Reconnecting'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: PayPinkTheme.wine,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    textStyle: PayPinkTheme.display(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
