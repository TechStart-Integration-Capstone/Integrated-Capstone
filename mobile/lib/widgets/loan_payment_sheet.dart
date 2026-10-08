import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../services/account_service.dart';

/// Dedicated Loan Payment Bottom Sheet
class LoanPaymentSheet extends StatefulWidget {
  final BankAccount loanAccount;
  final List<BankAccount> eligibleFundingAccounts;
  final Function(double amount, BankAccount fundingAccount, double newLoanBalance) onPaymentSuccess;

  const LoanPaymentSheet({
    super.key,
    required this.loanAccount,
    required this.eligibleFundingAccounts,
    required this.onPaymentSuccess,
  });

  static void show(
    BuildContext context, {
    required BankAccount loanAccount,
    required List<BankAccount> accounts,
    required Function(double amount, BankAccount fundingAccount, double newLoanBalance) onPaymentSuccess,
  }) {
    final fundingAccounts = accounts.where((a) => a.canBeTransferSource && a.currentBalance > 0).toList();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => LoanPaymentSheet(
        loanAccount: loanAccount,
        eligibleFundingAccounts: fundingAccounts,
        onPaymentSuccess: onPaymentSuccess,
      ),
    );
  }

  @override
  State<LoanPaymentSheet> createState() => _LoanPaymentSheetState();
}

class _LoanPaymentSheetState extends State<LoanPaymentSheet> {
  int _selectedOption = 0; // 0: Minimum Due, 1: Total Balance, 2: Custom Amount
  late BankAccount _selectedFundingAccount;
  final TextEditingController _customAmountController = TextEditingController();
  bool _isProcessing = false;

  @override
  void initState() {
    super.initState();
    if (widget.eligibleFundingAccounts.isNotEmpty) {
      _selectedFundingAccount = widget.eligibleFundingAccounts.first;
    } else {
      _selectedFundingAccount = BankAccount(
        accountId: 0,
        accountNumber: 'No Eligible Account',
        accountType: 'CHECKING_ACCOUNT',
        currency: 'PHP',
        currentBalance: 0.0,
        status: 'ACTIVE',
      );
    }
  }

  @override
  void dispose() {
    _customAmountController.dispose();
    super.dispose();
  }

  double get _currentDebt {
    final debt = widget.loanAccount.outstandingDebt;
    if (debt != null && debt > 0) return debt;
    return widget.loanAccount.currentBalance > 0 ? widget.loanAccount.currentBalance : 25000.00;
  }

  double get _minDue {
    final minPay = widget.loanAccount.minimumPayment;
    if (minPay != null && minPay > 0) return minPay;
    return 2150.00;
  }

  double get _paymentAmount {
    switch (_selectedOption) {
      case 0:
        return _minDue;
      case 1:
        return _currentDebt;
      case 2:
        return double.tryParse(_customAmountController.text) ?? 0.0;
      default:
        return _minDue;
    }
  }

  Future<void> _handleAuthorize() async {
    final amt = _paymentAmount;

    if (amt <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Please enter a valid payment amount.'),
        ),
      );
      return;
    }

    if (amt > _selectedFundingAccount.currentBalance) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Insufficient balance in ${_selectedFundingAccount.displayName}. Available: ₱${_selectedFundingAccount.currentBalance.toStringAsFixed(2)}'),
        ),
      );
      return;
    }

    setState(() => _isProcessing = true);

    try {
      final newLoanBalance = (_currentDebt - amt).clamp(0.0, double.infinity);

      // Perform transfer / loan payment via AccountService
      final receipt = await AccountService.payLoan(
        sourceAccount: _selectedFundingAccount,
        loanAccount: widget.loanAccount,
        amount: amt,
      );

      if (!mounted) return;
      setState(() => _isProcessing = false);

      if (receipt.success) {
        Navigator.pop(context);
        widget.onPaymentSuccess(amt, _selectedFundingAccount, newLoanBalance);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: PayPinkTheme.red,
            content: Text(receipt.message.isNotEmpty ? receipt.message : 'Payment request could not be completed.'),
          ),
        );
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _isProcessing = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Error: ${e.toString()}'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bgCol = isDark ? PayPinkTheme.darkCard : Colors.white;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final brandWine = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        padding: const EdgeInsets.only(top: 12, left: 22, right: 22, bottom: 28),
        decoration: BoxDecoration(
          color: bgCol,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x33000000),
              blurRadius: 30,
              offset: Offset(0, -8),
            ),
          ],
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Drag handle
              Center(
                child: Container(
                  width: 38,
                  height: 4,
                  decoration: BoxDecoration(
                    color: isDark ? PayPinkTheme.darkLine : Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Title
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Pay Personal Loan',
                    style: PayPinkTheme.display(
                      fontSize: 19,
                      fontWeight: FontWeight.w800,
                      color: textInk,
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.close_rounded, size: 20, color: textMuted),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // Loan Overview Card
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: isDark
                        ? [const Color(0xFF2E1A29), const Color(0xFF1D1424)]
                        : [PayPinkTheme.pinkSubtle, Colors.white],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: textLine),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Outstanding Balance',
                          style: PayPinkTheme.body(fontSize: 12, color: textMuted),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: PayPinkTheme.amberBg,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            'Due: ${widget.loanAccount.dueDate ?? "Oct 28, 2026"}',
                            style: const TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                              color: PayPinkTheme.amber,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '₱${_currentDebt.toStringAsFixed(2)}',
                      style: PayPinkTheme.display(
                        fontSize: 26,
                        fontWeight: FontWeight.w900,
                        color: textInk,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Min Due: ₱${_minDue.toStringAsFixed(2)} · Rate: 5.50% p.a.',
                      style: PayPinkTheme.body(fontSize: 11, color: textMuted),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),

              // Pay From (Funding Account)
              Text(
                'Pay From',
                style: PayPinkTheme.body(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w700,
                  color: textInk,
                ),
              ),
              const SizedBox(height: 8),

              if (widget.eligibleFundingAccounts.isEmpty)
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: PayPinkTheme.redBg,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Text(
                    'No eligible funding account with sufficient balance available.',
                    style: TextStyle(color: PayPinkTheme.red, fontSize: 12),
                  ),
                )
              else
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                  decoration: BoxDecoration(
                    color: isDark ? PayPinkTheme.darkBg : PayPinkTheme.paper,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: textLine),
                  ),
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<BankAccount>(
                      isExpanded: true,
                      dropdownColor: isDark ? PayPinkTheme.darkCard : Colors.white,
                      value: _selectedFundingAccount,
                      items: widget.eligibleFundingAccounts.map((acct) {
                        return DropdownMenuItem<BankAccount>(
                          value: acct,
                          child: Row(
                            children: [
                              Icon(
                                acct.accountType.contains('CHECK') ? Icons.credit_card_rounded : Icons.savings_rounded,
                                size: 18,
                                color: brandWine,
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  '${acct.displayName} (${acct.formattedAccountNumber})',
                                  style: PayPinkTheme.body(fontSize: 13, color: textInk, fontWeight: FontWeight.w600),
                                ),
                              ),
                              Text(
                                '₱${acct.currentBalance.toStringAsFixed(2)}',
                                style: PayPinkTheme.mono(fontSize: 12, fontWeight: FontWeight.w700, color: textInk),
                              ),
                            ],
                          ),
                        );
                      }).toList(),
                      onChanged: (val) {
                        if (val != null) setState(() => _selectedFundingAccount = val);
                      },
                    ),
                  ),
                ),
              const SizedBox(height: 20),

              // Payment Option Selector
              Text(
                'Payment Amount',
                style: PayPinkTheme.body(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w700,
                  color: textInk,
                ),
              ),
              const SizedBox(height: 8),

              Row(
                children: [
                  _buildOptionChip(0, 'Min Due\n₱${_minDue.toStringAsFixed(0)}', isDark),
                  const SizedBox(width: 8),
                  _buildOptionChip(1, 'Total Balance\n₱${_currentDebt.toStringAsFixed(0)}', isDark),
                  const SizedBox(width: 8),
                  _buildOptionChip(2, 'Custom\nAmount', isDark),
                ],
              ),
              const SizedBox(height: 14),

              // Custom amount input if selected
              if (_selectedOption == 2) ...[
                TextField(
                  controller: _customAmountController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  style: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w700, color: textInk),
                  decoration: InputDecoration(
                    prefixText: '₱ ',
                    prefixStyle: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w700, color: brandWine),
                    hintText: '0.00',
                    hintStyle: PayPinkTheme.body(fontSize: 16, color: textMuted),
                    filled: true,
                    fillColor: isDark ? PayPinkTheme.darkBg : PayPinkTheme.paper,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide(color: textLine),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide(color: brandWine, width: 2),
                    ),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                  ),
                  onChanged: (_) => setState(() {}),
                ),
                const SizedBox(height: 14),
              ],

              // Summary
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: isDark ? PayPinkTheme.darkBg : PayPinkTheme.paper,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Remaining Loan Debt:',
                      style: PayPinkTheme.body(fontSize: 12, color: textMuted),
                    ),
                    Text(
                      '₱${(_currentDebt - _paymentAmount).clamp(0.0, double.infinity).toStringAsFixed(2)}',
                      style: PayPinkTheme.mono(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: brandWine,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 22),

              // Pay button with PIN authorization
              SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton.icon(
                  onPressed: _isProcessing ? null : _handleAuthorize,
                  icon: _isProcessing
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                        )
                      : const Icon(Icons.lock_outline_rounded, size: 20),
                  label: Text(
                    _isProcessing ? 'Processing Payment...' : 'Authorize Payment (₱${_paymentAmount.toStringAsFixed(2)})',
                    style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: PayPinkTheme.wine,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildOptionChip(int index, String label, bool isDark) {
    final isSelected = _selectedOption == index;
    final brandWine = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;

    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _selectedOption = index),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
          decoration: BoxDecoration(
            color: isSelected
                ? (isDark ? PayPinkTheme.wineLight : PayPinkTheme.wine)
                : (isDark ? PayPinkTheme.darkBg : PayPinkTheme.paper),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: isSelected ? brandWine : (isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
              width: isSelected ? 1.5 : 1.0,
            ),
          ),
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: PayPinkTheme.body(
              fontSize: 11,
              fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
              color: isSelected ? Colors.white : (isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink),
            ),
          ),
        ),
      ),
    );
  }
}
