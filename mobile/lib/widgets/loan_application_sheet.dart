import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../services/account_service.dart';

/// Dedicated Loan Application Bottom Sheet for PayPink Mobile
/// Matches Web SPA loan application lifecycle:
/// Eligibility check -> Amount & Term form -> Instant Decision / Offer -> Terms Review & Agreement -> Disbursement
class LoanApplicationSheet extends StatefulWidget {
  final List<BankAccount> depositAccounts;
  final VoidCallback onLoanAccepted;

  const LoanApplicationSheet({
    super.key,
    required this.depositAccounts,
    required this.onLoanAccepted,
  });

  static void show(
    BuildContext context, {
    required List<BankAccount> accounts,
    required VoidCallback onLoanAccepted,
  }) {
    final depositAccounts = accounts.where((a) => a.canBeTransferSource).toList();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => LoanApplicationSheet(
        depositAccounts: depositAccounts,
        onLoanAccepted: onLoanAccepted,
      ),
    );
  }

  @override
  State<LoanApplicationSheet> createState() => _LoanApplicationSheetState();
}

class _LoanApplicationSheetState extends State<LoanApplicationSheet> {
  final _formKey = GlobalKey<FormState>();
  final TextEditingController _amountController = TextEditingController(text: '250000.00');

  BankAccount? _selectedAccount;
  int _selectedTermMonths = 12;
  bool _isLoadingEligibility = true;
  LoanEligibility? _eligibility;
  String? _errorMessage;

  bool _isSubmitting = false;
  LoanOffer? _offer;

  final List<int> _availableTerms = [3, 6, 9, 12, 18, 24, 36, 48, 60];

  @override
  void initState() {
    super.initState();
    if (widget.depositAccounts.isNotEmpty) {
      _selectedAccount = widget.depositAccounts.first;
    }
    _loadEligibility();
  }

  Future<void> _loadEligibility() async {
    setState(() {
      _isLoadingEligibility = true;
    });
    final elig = await AccountService.fetchLoanEligibility();
    if (mounted) {
      setState(() {
        _eligibility = elig;
        _isLoadingEligibility = false;
      });
    }
  }

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _submitApplication() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedAccount == null) {
      setState(() => _errorMessage = 'Please select an account for disbursement.');
      return;
    }

    final amount = double.tryParse(_amountController.text.replaceAll(',', '')) ?? 0.0;
    if (amount < 5000) {
      setState(() => _errorMessage = 'Minimum loan amount is ₱5,000.00.');
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final offer = await AccountService.applyForLoan(
        accountNo: _selectedAccount!.accountNumber,
        amount: amount,
        termMonths: _selectedTermMonths,
      );
      if (mounted) {
        setState(() {
          _offer = offer;
          _isSubmitting = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceFirst('Exception: ', '');
          _isSubmitting = false;
        });
      }
    }
  }

  void _showTermsDialog(LoanOffer offer) {
    bool agreedToTerms = false;
    bool isAccepting = false;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (dialogCtx) => StatefulBuilder(
        builder: (ctx, setDialogState) {
          final amt = offer.amount ?? 0.0;
          final installment = offer.monthlyInstallment ?? 0.0;
          final totalRepay = offer.totalRepayment ?? (installment * (offer.termMonths ?? 12));
          final totalInterest = offer.totalInterest ?? (totalRepay - amt);
          final accNum = _selectedAccount?.accountNumber ?? '';
          final maskedAcc = accNum.length >= 4 ? '•••• ${accNum.substring(accNum.length - 4)}' : accNum;

          return AlertDialog(
            backgroundColor: PayPinkTheme.paper,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            title: Row(
              children: [
                const Icon(Icons.gavel_rounded, color: PayPinkTheme.wine),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Review Loan Agreement',
                    style: TextStyle(
                      fontFamily: PayPinkTheme.fontFamily,
                      fontWeight: FontWeight.bold,
                      fontSize: 18,
                      color: PayPinkTheme.ink,
                    ),
                  ),
                ),
              ],
            ),
            content: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text(
                    'Please check the key facts and agree to the terms before receiving your funds.',
                    style: TextStyle(fontSize: 13, color: PayPinkTheme.muted),
                  ),
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: PayPinkTheme.pinkSubtle,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: PayPinkTheme.line),
                    ),
                    child: Column(
                      children: [
                        _buildFactRow('Disbursement into', maskedAcc),
                        _buildFactRow('Principal Amount', formatPeso(amt), isBold: true),
                        _buildFactRow('Term', '${offer.termMonths ?? 12} monthly installments'),
                        _buildFactRow('Annual Interest Rate', '${offer.annualRate ?? 7.0}% / year'),
                        _buildFactRow('Monthly Installment', formatPeso(installment), isBold: true),
                        _buildFactRow('Total Repayment', '${formatPeso(totalRepay)} (${formatPeso(totalInterest)} interest)'),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Terms & Conditions (RA 3765 & RA 10173)',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: PayPinkTheme.ink),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    height: 120,
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: PayPinkTheme.line),
                    ),
                    child: const SingleChildScrollView(
                      child: Text(
                        '1. Disbursement: Acceptance is final. Funds will be credited directly to your selected account.\n'
                        '2. Repayment: You agree to pay the monthly installments on their due dates.\n'
                        '3. Auto-debit: You authorize PayPink to automatically debit repayments on their due date.\n'
                        '4. Disclosure: Figures provided constitute full disclosure under the Truth in Lending Act.',
                        style: TextStyle(fontSize: 11, color: PayPinkTheme.ink, height: 1.4),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Checkbox(
                        value: agreedToTerms,
                        activeColor: PayPinkTheme.wine,
                        onChanged: isAccepting
                            ? null
                            : (val) {
                                setDialogState(() => agreedToTerms = val == true);
                              },
                      ),
                      const Expanded(
                        child: Text(
                          'I have read and agree to the loan terms and authorize the auto-debit.',
                          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: PayPinkTheme.ink),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: isAccepting ? null : () => Navigator.of(dialogCtx).pop(),
                child: const Text('Cancel', style: TextStyle(color: PayPinkTheme.muted)),
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: PayPinkTheme.wine,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                ),
                onPressed: (!agreedToTerms || isAccepting)
                    ? null
                    : () async {
                        setDialogState(() => isAccepting = true);
                        try {
                          await AccountService.acceptLoanOffer(offer.referenceNo);
                          if (dialogCtx.mounted && mounted) {
                            Navigator.of(dialogCtx).pop(); // close dialog
                            Navigator.of(context).pop(); // close bottom sheet
                            widget.onLoanAccepted();
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                backgroundColor: PayPinkTheme.green,
                                content: Row(
                                  children: [
                                    const Icon(Icons.check_circle_outline, color: Colors.white),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Text(
                                        'Loan of ${formatPeso(amt)} successfully credited to your account!',
                                        style: const TextStyle(fontWeight: FontWeight.bold),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          }
                        } catch (e) {
                          setDialogState(() => isAccepting = false);
                          if (dialogCtx.mounted) {
                            ScaffoldMessenger.of(dialogCtx).showSnackBar(
                              SnackBar(
                                backgroundColor: PayPinkTheme.red,
                                content: Text(e.toString().replaceFirst('Exception: ', '')),
                              ),
                            );
                          }
                        }
                      },
                child: isAccepting
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                      )
                    : Text(
                        'Agree & Receive ${formatPeso(amt)}',
                        style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white),
                      ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildFactRow(String label, String value, {bool isBold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 12, color: PayPinkTheme.muted)),
          Text(
            value,
            style: TextStyle(
              fontSize: 12,
              fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
              color: isBold ? PayPinkTheme.wine : PayPinkTheme.ink,
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final bottomInset = mediaQuery.viewInsets.bottom;

    return Container(
      padding: EdgeInsets.only(
        top: 24,
        left: 20,
        right: 20,
        bottom: bottomInset + 24,
      ),
      decoration: const BoxDecoration(
        color: PayPinkTheme.paper,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Handle bar
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: PayPinkTheme.line,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),

            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(Icons.account_balance_wallet_rounded, color: PayPinkTheme.wine, size: 28),
                    const SizedBox(width: 10),
                    Text(
                      'Apply for a Loan',
                      style: TextStyle(
                        fontFamily: PayPinkTheme.fontFamily,
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: PayPinkTheme.ink,
                      ),
                    ),
                  ],
                ),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded, color: PayPinkTheme.muted),
                ),
              ],
            ),
            const Text(
              'Borrow with confidence. Get an instant credit decision.',
              style: TextStyle(fontSize: 13, color: PayPinkTheme.muted),
            ),
            const SizedBox(height: 16),

            // Eligibility Card Banner
            if (_isLoadingEligibility)
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: PayPinkTheme.pinkSubtle,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Row(
                  children: [
                    SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: PayPinkTheme.wine)),
                    SizedBox(width: 12),
                    Text('Checking borrowing eligibility…', style: TextStyle(fontSize: 13, color: PayPinkTheme.muted)),
                  ],
                ),
              )
            else if (_eligibility != null)
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: _eligibility!.eligible
                      ? PayPinkTheme.greenBg
                      : PayPinkTheme.redBg,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: _eligibility!.eligible ? PayPinkTheme.green : PayPinkTheme.red,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      _eligibility!.eligible ? Icons.verified_user_rounded : Icons.info_outline_rounded,
                      color: _eligibility!.eligible ? PayPinkTheme.green : PayPinkTheme.red,
                      size: 24,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _eligibility!.eligible ? 'Eligible for instant loan' : 'Loan Application Notice',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 13,
                              color: _eligibility!.eligible ? PayPinkTheme.green : PayPinkTheme.red,
                            ),
                          ),
                          Text(
                            _eligibility!.eligible
                                ? 'Available limit: ${formatPeso(_eligibility!.available)} (Max ${formatPeso(_eligibility!.creditLimit)})'
                                : (_eligibility!.reason ?? 'Currently ineligible for a new loan.'),
                            style: const TextStyle(fontSize: 12, color: PayPinkTheme.ink),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            const SizedBox(height: 20),

            if (_errorMessage != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: PayPinkTheme.redBg,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  _errorMessage!,
                  style: const TextStyle(color: PayPinkTheme.red, fontSize: 13, fontWeight: FontWeight.w600),
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Form or Offer display
            if (_offer == null) ...[
              Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Disbursement Account', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: PayPinkTheme.ink)),
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: PayPinkTheme.line),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<BankAccount>(
                          isExpanded: true,
                          value: _selectedAccount,
                          items: widget.depositAccounts.map((acc) {
                            return DropdownMenuItem<BankAccount>(
                              value: acc,
                              child: Text(
                                '${acc.displayName} • ${acc.maskedNumber} (${formatPeso(acc.currentBalance)})',
                                style: const TextStyle(fontSize: 14, color: PayPinkTheme.ink),
                              ),
                            );
                          }).toList(),
                          onChanged: (val) {
                            if (val != null) setState(() => _selectedAccount = val);
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    const Text('Loan Amount (PHP)', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: PayPinkTheme.ink)),
                    const SizedBox(height: 6),
                    TextFormField(
                      controller: _amountController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: PayPinkTheme.ink),
                      decoration: InputDecoration(
                        prefixText: '₱ ',
                        hintText: '250,000.00',
                        filled: true,
                        fillColor: Colors.white,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: PayPinkTheme.line)),
                        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: PayPinkTheme.line)),
                      ),
                      validator: (val) {
                        if (val == null || val.trim().isEmpty) return 'Enter amount';
                        final parsed = double.tryParse(val.replaceAll(',', ''));
                        if (parsed == null || parsed < 5000) return 'Minimum loan is ₱5,000.00';
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    const Text('Repayment Term', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: PayPinkTheme.ink)),
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: PayPinkTheme.line),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<int>(
                          isExpanded: true,
                          value: _selectedTermMonths,
                          items: _availableTerms.map((term) {
                            return DropdownMenuItem<int>(
                              value: term,
                              child: Text(
                                '$term Months',
                                style: const TextStyle(fontSize: 14, color: PayPinkTheme.ink),
                              ),
                            );
                          }).toList(),
                          onChanged: (val) {
                            if (val != null) setState(() => _selectedTermMonths = val);
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),

                    SizedBox(
                      width: double.infinity,
                      height: 52,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: PayPinkTheme.wine,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        ),
                        onPressed: _isSubmitting ? null : _submitApplication,
                        child: _isSubmitting
                            ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5))
                            : const Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Text('Get My Decision', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white)),
                                  SizedBox(width: 8),
                                  Icon(Icons.arrow_forward_rounded, color: Colors.white, size: 20),
                                ],
                              ),
                      ),
                    ),
                  ],
                ),
              ),
            ] else ...[
              // Offer Card Result
              Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: PayPinkTheme.line),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Ref: ${_offer!.referenceNo}',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: PayPinkTheme.ink),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: _offer!.decision == 'APPROVED' ? PayPinkTheme.greenBg : PayPinkTheme.redBg,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            _offer!.decision,
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 12,
                              color: _offer!.decision == 'APPROVED' ? PayPinkTheme.green : PayPinkTheme.red,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const Divider(height: 24),

                    if (_offer!.decision == 'DECLINED') ...[
                      Text(
                        _offer!.declineReason ?? 'We cannot offer a loan right now based on our credit parameters.',
                        style: const TextStyle(color: PayPinkTheme.red, fontSize: 13),
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => setState(() => _offer = null),
                        child: const Text('Try Again'),
                      ),
                    ] else ...[
                      _buildFactRow('Approved Amount', formatPeso(_offer!.amount ?? 0.0), isBold: true),
                      _buildFactRow('Repayment Term', '${_offer!.termMonths ?? 12} Months'),
                      _buildFactRow('Annual Rate', '${_offer!.annualRate ?? 7.0}% / yr'),
                      _buildFactRow('Monthly Installment', formatPeso(_offer!.monthlyInstallment ?? 0.0), isBold: true),
                      const SizedBox(height: 20),

                      SizedBox(
                        width: double.infinity,
                        height: 50,
                        child: ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: PayPinkTheme.wine,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                          ),
                          onPressed: () => _showTermsDialog(_offer!),
                          child: const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text('Review & Accept Terms', style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white)),
                              SizedBox(width: 8),
                              Icon(Icons.arrow_forward_rounded, color: Colors.white, size: 18),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
