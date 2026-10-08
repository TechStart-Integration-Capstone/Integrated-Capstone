import 'package:flutter/material.dart';
import '../services/account_service.dart';
import 'dynamic_card_deck.dart';

/// Account Card Carousel backed by the physical 3D Dynamic Card Deck
/// Inspired by modern luxury fintech (portrait 0.66 aspect ratio, 3D turn, specular sheen)
class AccountCardCarousel extends StatelessWidget {
  final List<BankAccount> accounts;
  final bool hideBalances;
  final VoidCallback onToggleHideBalances;
  final Function(int tabIndex) onNavigateTab;
  final VoidCallback onOpenLoanPayment;
  final Function(BankAccount account)? onSelectAccount;
  final String cardHolder;
  final bool isDark;

  const AccountCardCarousel({
    super.key,
    required this.accounts,
    required this.hideBalances,
    required this.onToggleHideBalances,
    required this.onNavigateTab,
    required this.onOpenLoanPayment,
    this.onSelectAccount,
    this.cardHolder = 'PayPink Client',
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return DynamicCardDeck(
      accounts: accounts,
      cardHolder: cardHolder,
      hideBalances: hideBalances,
      onToggleHideBalances: onToggleHideBalances,
      onSelectAccount: onSelectAccount,
      onOpenTransfer: () => onNavigateTab(2),
      onOpenDetails: () {
        if (accounts.isNotEmpty) {
          onSelectAccount?.call(accounts.first);
        }
      },
      isDark: isDark,
    );
  }
}
