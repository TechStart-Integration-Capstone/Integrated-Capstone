import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/bottom_sheets.dart';
import '../services/remittance_service.dart';
import '../services/account_service.dart';

class RemittanceScreen extends StatefulWidget {
  final Function(double amount, String refId, String source, String recipient) onTransferSuccess;
  final List<BankAccount>? accounts;

  const RemittanceScreen({
    super.key,
    required this.onTransferSuccess,
    this.accounts,
  });

  @override
  State<RemittanceScreen> createState() => _RemittanceScreenState();
}

class _RemittanceScreenState extends State<RemittanceScreen> {
  int _selectedModeIndex = 0; // 0: My own, 1: Another PayPink, 2: Outside
  String _sourceAccount = '';
  String _ownTargetAccount = '';

  final TextEditingController _amountController = TextEditingController(text: '');
  final TextEditingController _recipientController = TextEditingController();

  double _riskScore = 0.12;
  String _ofscorePreview = 'FUNDS.TRANSFER,AUTH/I/PROCESS,//PH100223,DEBIT.ACCT.NO=5046,CREDIT.ACCT.NO=8504,AMOUNT=0.00,CCY=PHP';
  String? _verifiedName;

  bool _isStandingInstruction = false;
  String _standingFrequency = 'Monthly';
  String _transferRail = 'InstaPay';
  String _destinationBank = 'BDO';

  final List<Map<String, String>> _favorites = [
    {'name': 'Carlos Mendoza', 'number': '001 1 2234567 8', 'avatar': 'CM', 'bank': 'PayPink'},
    {'name': 'Maria Santos', 'number': '001 1 9876543 2', 'avatar': 'MS', 'bank': 'PayPink'},
    {'name': 'David Lee', 'number': '001 1 4567890 1', 'avatar': 'DL', 'bank': 'PayPink'},
  ];

  @override
  void initState() {
    super.initState();
    _initDefaultAccounts();
    _updateCalculations();
  }

  void _initDefaultAccounts() {
    if (widget.accounts != null && widget.accounts!.isNotEmpty) {
      _sourceAccount = widget.accounts!.first.accountNumber;
      if (widget.accounts!.length > 1) {
        _ownTargetAccount = widget.accounts![1].accountNumber;
      } else {
        _ownTargetAccount = widget.accounts!.first.accountNumber;
      }
    } else {
      _sourceAccount = 'everyday-5046';
      _ownTargetAccount = 'savings-8504';
    }
  }

  @override
  void didUpdateWidget(covariant RemittanceScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.accounts != oldWidget.accounts && widget.accounts != null && widget.accounts!.isNotEmpty) {
      if (_sourceAccount.isEmpty || _sourceAccount.contains('everyday-5046')) {
        _initDefaultAccounts();
        _updateCalculations();
      }
    }
  }

  void _updateCalculations() {
    final amt = double.tryParse(_amountController.text) ?? 0.0;
    final debitAcct = _sourceAccount.isNotEmpty ? _sourceAccount : '5046';
    final creditAcct = _selectedModeIndex == 0
        ? (_ownTargetAccount.isNotEmpty ? _ownTargetAccount : '8504')
        : (_recipientController.text.isNotEmpty ? _recipientController.text.replaceAll(' ', '') : '2234567');

    setState(() {
      _riskScore = RemittanceService.evaluateRisk(amt);
      _ofscorePreview = 'FUNDS.TRANSFER,AUTH/I/PROCESS,//PH100223,DEBIT.ACCT.NO=$debitAcct,CREDIT.ACCT.NO=$creditAcct,AMOUNT=${amt.toStringAsFixed(2)},CCY=PHP';

      final cleanRec = _recipientController.text.replaceAll(' ', '');
      if (cleanRec.length >= 8) {
        if (cleanRec.endsWith('8')) {
          _verifiedName = 'Carlos Mendoza';
        } else if (cleanRec.endsWith('2')) {
          _verifiedName = 'Maria Santos';
        } else {
          _verifiedName = 'Verified PayPink Recipient';
        }
      } else {
        _verifiedName = null;
      }
    });
  }

  void _selectFavorite(int index) {
    final fav = _favorites[index];
    _recipientController.text = fav['number']!;
    _updateCalculations();
  }

  void _addQuickAmount(double delta) {
    final current = double.tryParse(_amountController.text) ?? 0.0;
    _amountController.text = (current + delta).toStringAsFixed(2);
    _updateCalculations();
  }

  void _handleReviewTransfer() {
    final amt = double.tryParse(_amountController.text);
    if (amt == null || amt <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: PayPinkTheme.wine,
          content: Text('Please enter a valid transfer amount'),
        ),
      );
      return;
    }

    // Dynamic balance check against selected account's live balance
    double maxAvailable = 50.00;
    String sourceName = 'Selected Account';
    if (widget.accounts != null && widget.accounts!.isNotEmpty) {
      final match = widget.accounts!.firstWhere(
        (a) => a.accountNumber == _sourceAccount,
        orElse: () => widget.accounts!.first,
      );
      maxAvailable = match.currentBalance;
      sourceName = match.displayName;
    }

    if (amt > maxAvailable) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Insufficient balance in $sourceName (₱${maxAvailable.toStringAsFixed(2)})'),
        ),
      );
      return;
    }

    if (_riskScore > 0.85) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Transfer blocked by Fraud Screening Model (> 0.85)'),
        ),
      );
      return;
    }

    final fromAccountName = '$sourceName (${_sourceAccount.length >= 4 ? _sourceAccount.substring(_sourceAccount.length - 4) : _sourceAccount})';
    final toAccountName = _selectedModeIndex == 0
        ? 'Target Account · $_ownTargetAccount'
        : (_verifiedName != null ? '$_verifiedName · ${_recipientController.text}' : 'External Account');

    _showConfirmationBottomSheet(amt, fromAccountName, toAccountName);
  }

  void _showConfirmationBottomSheet(double amt, String fromAcc, String toAcc) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final paperBg = isDark ? PayPinkTheme.darkPaper : PayPinkTheme.paper;
    final sheetBg = isDark ? PayPinkTheme.darkPaper : Colors.white;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.only(top: 10, left: 22, right: 22, bottom: 28),
        decoration: BoxDecoration(
          color: sheetBg,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x33000000),
              blurRadius: 30,
              offset: Offset(0, -6),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 38,
              height: 4,
              decoration: BoxDecoration(
                color: isDark ? textLine : Colors.grey.shade300,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 14),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Confirm Remittance',
                  style: PayPinkTheme.display(fontSize: 17, fontWeight: FontWeight.w700, color: textInk),
                ),
                IconButton(
                  icon: Icon(Icons.close, size: 20, color: textMuted),
                  onPressed: () => Navigator.pop(ctx),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              'Transfer Amount',
              style: PayPinkTheme.body(fontSize: 11, color: textMuted),
            ),
            const SizedBox(height: 4),
            Text(
              '₱${amt.toStringAsFixed(2)}',
              style: PayPinkTheme.display(
                fontSize: 34,
                fontWeight: FontWeight.w800,
                color: textInk,
              ),
            ),
            const SizedBox(height: 18),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: paperBg,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: textLine),
              ),
              child: Column(
                children: [
                  _confirmRow('From Account', fromAcc, isDark: isDark),
                  Divider(color: textLine, height: 16),
                  _confirmRow('To Recipient', toAcc, isDark: isDark),
                  if (_selectedModeIndex == 2) ...[
                    Divider(color: textLine, height: 16),
                    _confirmRow('Clearing Rail', _transferRail == 'InstaPay' ? 'InstaPay (Realtime)' : 'PESONet (Batch EOD Cutoff)', isDark: isDark),
                  ],
                  if (_isStandingInstruction) ...[
                    Divider(color: textLine, height: 16),
                    _confirmRow('Standing Schedule', 'Recurring ($_standingFrequency) · Next: Oct 15', valColor: PayPinkTheme.wine, isDark: isDark),
                  ],
                  Divider(color: textLine, height: 16),
                  _confirmRow('Fee', '₱0.00 (Free)', valColor: PayPinkTheme.green, isDark: isDark),
                  Divider(color: textLine, height: 16),
                  _confirmRow('Risk Screening', 'Cleared (${_riskScore.toStringAsFixed(2)})', valColor: PayPinkTheme.green, isDark: isDark),
                ],
              ),
            ),
            const SizedBox(height: 22),
            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton.icon(
                onPressed: () {
                  Navigator.pop(ctx);
                  _executeTransferBiometric(amt, fromAcc, toAcc);
                },
                icon: const Icon(Icons.fingerprint_rounded, size: 20),
                label: const Text('Confirm with Face ID'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: PayPinkTheme.wine,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  textStyle: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _executeTransferBiometric(double amt, String fromAcc, String toAcc) async {
    final cleanDest = _selectedModeIndex == 0
        ? _ownTargetAccount
        : _recipientController.text.replaceAll(' ', '');

    final result = await RemittanceService.submitRemittance(
      sourceAccountId: _sourceAccount,
      destinationAccountNumber: cleanDest,
      amount: amt,
    );

    if (!mounted) return;

    if (result.success) {
      final refId = result.referenceId ?? 'TRX-20261002-${DateTime.now().millisecondsSinceEpoch.toString().substring(8)}';
      _showReceiptBottomSheet(amt, refId, fromAcc, toAcc);
      widget.onTransferSuccess(amt, refId, fromAcc, toAcc);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text(result.message),
        ),
      );
    }
  }

  void _showReceiptBottomSheet(double amt, String refId, String fromAcc, String toAcc) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final paperBg = isDark ? PayPinkTheme.darkPaper : PayPinkTheme.paper;
    final sheetBg = isDark ? PayPinkTheme.darkPaper : Colors.white;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.only(top: 10, left: 22, right: 22, bottom: 28),
        decoration: BoxDecoration(
          color: sheetBg,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x33000000),
              blurRadius: 30,
              offset: Offset(0, -6),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 38,
              height: 4,
              decoration: BoxDecoration(
                color: isDark ? textLine : Colors.grey.shade300,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 20),
            Container(
              width: 60,
              height: 60,
              decoration: BoxDecoration(
                color: isDark ? PayPinkTheme.green.withValues(alpha: 0.15) : PayPinkTheme.greenBg,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.check_rounded,
                color: PayPinkTheme.green,
                size: 36,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Transfer Completed',
              style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w800, color: textInk),
            ),
            const SizedBox(height: 4),
            Text(
              '₱${amt.toStringAsFixed(2)}',
              style: PayPinkTheme.display(
                fontSize: 32,
                fontWeight: FontWeight.w800,
                color: textInk,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              'Committed to Dual-Store Ledger & Temenos T24 Core',
              style: PayPinkTheme.body(fontSize: 11, color: textMuted),
            ),
            const SizedBox(height: 18),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: paperBg,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: textLine),
              ),
              child: Column(
                children: [
                  _confirmRow('Reference ID', refId, isMono: true, isDark: isDark),
                  Divider(color: textLine, height: 16),
                  _confirmRow('Date & Time', 'Oct 2, 2026 15:00', isDark: isDark),
                  Divider(color: textLine, height: 16),
                  _confirmRow('Source', fromAcc, isDark: isDark),
                  Divider(color: textLine, height: 16),
                  _confirmRow('Recipient', toAcc, isDark: isDark),
                  if (_isStandingInstruction) ...[
                    Divider(color: textLine, height: 16),
                    _confirmRow('Standing Instruction', 'Active ($_standingFrequency) · Next: Oct 15', valColor: PayPinkTheme.wine, isDark: isDark),
                  ],
                  if (_selectedModeIndex == 2) ...[
                    Divider(color: textLine, height: 16),
                    _confirmRow('Clearing Rail', _transferRail == 'InstaPay' ? 'InstaPay Real-Time' : 'PESONet Batch Cutoff', isDark: isDark),
                  ],
                  Divider(color: textLine, height: 16),
                  _confirmRow('Temenos OFSCore Record', _ofscorePreview, isMono: true, isSmall: true, isDark: isDark),
                ],
              ),
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          backgroundColor: PayPinkTheme.wine,
                          content: Text('Receipt image saved to Photos'),
                        ),
                      );
                    },
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: isDark ? PayPinkTheme.wine : PayPinkTheme.pink),
                      backgroundColor: isDark ? PayPinkTheme.wine.withValues(alpha: 0.2) : PayPinkTheme.pinkSubtle,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: Text(
                      'Save Receipt (PNG)',
                      style: PayPinkTheme.body(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton(
                    onPressed: () => Navigator.pop(ctx),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: PayPinkTheme.wine,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: Text(
                      'Done',
                      style: PayPinkTheme.body(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _confirmRow(String label, String value, {Color? valColor, bool isMono = false, bool isSmall = false, bool isDark = false}) {
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: PayPinkTheme.body(fontSize: 11.5, color: textMuted)),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: isMono
                ? PayPinkTheme.mono(
                    fontSize: isSmall ? 9 : 11.5,
                    fontWeight: FontWeight.w600,
                    color: valColor ?? textInk,
                  )
                : PayPinkTheme.body(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w700,
                    color: valColor ?? textInk,
                  ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final cardBg = isDark ? PayPinkTheme.darkCard : Colors.white;
    final isHighRisk = _riskScore > 0.85;

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Move money. Make things happen.',
            style: PayPinkTheme.display(
              fontSize: 26,
              fontWeight: FontWeight.w800,
              color: textInk,
              letterSpacing: -0.8,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Transfer between your accounts or send to another PayPink account.',
            style: PayPinkTheme.body(fontSize: 12.5, color: textMuted),
          ),
          const SizedBox(height: 18),

          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Fund transfer',
                      style: PayPinkTheme.display(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: textInk,
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: isDark ? PayPinkTheme.green.withValues(alpha: 0.15) : PayPinkTheme.greenBg,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        '• No transfer fee',
                        style: PayPinkTheme.body(
                          fontSize: 9.5,
                          color: PayPinkTheme.green,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Source Account Dropdown
                Text('Transfer from', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600, color: textInk)),
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  decoration: BoxDecoration(
                    color: cardBg,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: textLine),
                  ),
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<String>(
                      value: _sourceAccount.isNotEmpty ? _sourceAccount : null,
                      isExpanded: true,
                      dropdownColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
                      style: PayPinkTheme.body(fontSize: 13, color: textInk),
                      items: widget.accounts != null && widget.accounts!.isNotEmpty
                          ? widget.accounts!.map((a) {
                              return DropdownMenuItem<String>(
                                value: a.accountNumber,
                                child: Text(
                                  '${a.displayName} · ${a.maskedNumber} · ₱${a.currentBalance.toStringAsFixed(2)}',
                                  style: PayPinkTheme.body(fontSize: 13, color: textInk),
                                ),
                              );
                            }).toList()
                          : [
                              DropdownMenuItem(
                                value: 'everyday-5046',
                                child: Text('Everyday account · •••• 5046 · ₱50.00', style: PayPinkTheme.body(fontSize: 13, color: textInk)),
                              ),
                              DropdownMenuItem(
                                value: 'savings-8504',
                                child: Text('Savings account · 001 1 5968504 7 · ₱0.00', style: PayPinkTheme.body(fontSize: 13, color: textInk)),
                              ),
                            ],
                      onChanged: (val) {
                        if (val != null) {
                          setState(() {
                            _sourceAccount = val;
                            _updateCalculations();
                          });
                        }
                      },
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Transfer Mode Segmented Tabs
                Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    color: isDark ? Colors.white.withValues(alpha: 0.06) : PayPinkTheme.wine.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      _buildTabBtn('My own account', 0, isDark: isDark),
                      _buildTabBtn('Another PayPink', 1, isDark: isDark),
                      _buildTabBtn('Outside PayPink', 2, isDark: isDark),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Recipient Fields
                if (_selectedModeIndex == 0) ...[
                  Text('Transfer to', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600, color: textInk)),
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    decoration: BoxDecoration(
                      color: cardBg,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: textLine),
                    ),
                    child: DropdownButtonHideUnderline(
                      child: DropdownButton<String>(
                        value: _ownTargetAccount.isNotEmpty ? _ownTargetAccount : null,
                        isExpanded: true,
                        dropdownColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
                        style: PayPinkTheme.body(fontSize: 13, color: textInk),
                        items: widget.accounts != null && widget.accounts!.isNotEmpty
                            ? widget.accounts!.map((a) {
                                return DropdownMenuItem<String>(
                                  value: a.accountNumber,
                                  child: Text(
                                    '${a.displayName} · ${a.maskedNumber} · ₱${a.currentBalance.toStringAsFixed(2)}',
                                    style: PayPinkTheme.body(fontSize: 13, color: textInk),
                                  ),
                                );
                              }).toList()
                            : [
                                DropdownMenuItem(
                                  value: 'savings-8504',
                                  child: Text('Savings account · 001 1 5968504 7 · ₱0.00', style: PayPinkTheme.body(fontSize: 13, color: textInk)),
                                ),
                                DropdownMenuItem(
                                  value: 'everyday-5046',
                                  child: Text('Everyday account · •••• 5046 · ₱50.00', style: PayPinkTheme.body(fontSize: 13, color: textInk)),
                                ),
                              ],
                        onChanged: (val) {
                          if (val != null) {
                            setState(() {
                              _ownTargetAccount = val;
                              _updateCalculations();
                            });
                          }
                        },
                      ),
                    ),
                  ),
                ] else ...[
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('Frequent Beneficiaries', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w700, color: textInk)),
                      GestureDetector(
                        onTap: () => PayPinkBottomSheets.showBeneficiaryManager(
                          context,
                          beneficiaries: _favorites,
                          onAddBeneficiary: (b) => setState(() => _favorites.add(b)),
                          onRemoveBeneficiary: (idx) => setState(() => _favorites.removeAt(idx)),
                          onSelect: (name, number) {
                            _recipientController.text = number;
                            _updateCalculations();
                          },
                        ),
                        child: Text(
                          'Manage Directory →',
                          style: PayPinkTheme.body(fontSize: 11, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),

                  // Quick Favorites Row
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: List.generate(_favorites.length, (idx) {
                      final fav = _favorites[idx];
                      return GestureDetector(
                        onTap: () => _selectFavorite(idx),
                        child: Column(
                          children: [
                            Container(
                              width: 44,
                              height: 44,
                              decoration: BoxDecoration(
                                color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.25) : PayPinkTheme.pinkSubtle,
                                shape: BoxShape.circle,
                                border: Border.all(color: isDark ? PayPinkTheme.wine : PayPinkTheme.pink),
                              ),
                              child: Center(
                                child: Text(
                                  fav['avatar']!,
                                  style: PayPinkTheme.display(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w800,
                                    color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              fav['name']!.split(' ')[0],
                              style: PayPinkTheme.body(fontSize: 10, fontWeight: FontWeight.w600, color: textInk),
                            ),
                          ],
                        ),
                      );
                    }),
                  ),
                  const SizedBox(height: 12),
                  if (_selectedModeIndex == 2) ...[
                    Text('Destination Institution', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600, color: textInk)),
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      decoration: BoxDecoration(
                        color: cardBg,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: textLine),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: _destinationBank,
                          isExpanded: true,
                          dropdownColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
                          style: PayPinkTheme.body(fontSize: 13, color: textInk),
                          items: [
                            DropdownMenuItem(value: 'BDO', child: Text('BDO Unibank', style: PayPinkTheme.body(fontSize: 13, color: textInk))),
                            DropdownMenuItem(value: 'BPI', child: Text('Bank of the Philippine Islands (BPI)', style: PayPinkTheme.body(fontSize: 13, color: textInk))),
                            DropdownMenuItem(value: 'UnionBank', child: Text('UnionBank of the Philippines', style: PayPinkTheme.body(fontSize: 13, color: textInk))),
                            DropdownMenuItem(value: 'GCash', child: Text('GCash / G-Xchange Inc.', style: PayPinkTheme.body(fontSize: 13, color: textInk))),
                            DropdownMenuItem(value: 'Maya', child: Text('Maya Philippines', style: PayPinkTheme.body(fontSize: 13, color: textInk))),
                          ],
                          onChanged: (val) {
                            if (val != null) setState(() => _destinationBank = val);
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text('Clearing Rail & Settlement', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600, color: textInk)),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Expanded(
                          child: GestureDetector(
                            onTap: () => setState(() => _transferRail = 'InstaPay'),
                            child: Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: _transferRail == 'InstaPay'
                                    ? (isDark ? PayPinkTheme.wine.withValues(alpha: 0.25) : PayPinkTheme.pinkSubtle)
                                    : cardBg,
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(color: _transferRail == 'InstaPay' ? PayPinkTheme.wine : textLine),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text('InstaPay', style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700, color: textInk)),
                                  const SizedBox(height: 2),
                                  Text('Real-time · Up to ₱50k', style: PayPinkTheme.body(fontSize: 9.5, color: textMuted)),
                                ],
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: GestureDetector(
                            onTap: () => setState(() => _transferRail = 'PESONet'),
                            child: Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: _transferRail == 'PESONet'
                                    ? (isDark ? PayPinkTheme.wine.withValues(alpha: 0.25) : PayPinkTheme.pinkSubtle)
                                    : cardBg,
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(color: _transferRail == 'PESONet' ? PayPinkTheme.wine : textLine),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text('PESONet', style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700, color: textInk)),
                                  const SizedBox(height: 2),
                                  Text('Batch EOD cutoff · Unlimited', style: PayPinkTheme.body(fontSize: 9.5, color: textMuted)),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                  ],
                  Text('Recipient Account Number', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600, color: textInk)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _recipientController,
                    onChanged: (_) => _updateCalculations(),
                    style: PayPinkTheme.body(fontSize: 13, color: textInk),
                    decoration: InputDecoration(
                      hintText: 'e.g. 001 1 2234567 8 (Carlos Mendoza)',
                      hintStyle: PayPinkTheme.body(fontSize: 13, color: textMuted),
                      filled: true,
                      fillColor: cardBg,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(color: textLine),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(color: textLine),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: const BorderSide(color: PayPinkTheme.wine),
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                    ),
                  ),
                  if (_verifiedName != null) ...[
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color: isDark ? PayPinkTheme.green.withValues(alpha: 0.15) : PayPinkTheme.greenBg,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: PayPinkTheme.green.withValues(alpha: 0.3)),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              const Icon(Icons.check_circle_rounded, color: PayPinkTheme.green, size: 16),
                              const SizedBox(width: 8),
                              Text(
                                _verifiedName!,
                                style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w700, color: PayPinkTheme.green),
                              ),
                            ],
                          ),
                          Text('Match', style: PayPinkTheme.body(fontSize: 10, color: PayPinkTheme.green)),
                        ],
                      ),
                    ),
                  ],
                ],
                const SizedBox(height: 16),

                // Amount Field
                Text('Amount', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600, color: textInk)),
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                  decoration: BoxDecoration(
                    color: cardBg,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: textLine),
                  ),
                  child: Row(
                    children: [
                      Text(
                        'PHP',
                        style: PayPinkTheme.display(
                          fontSize: 18,
                          fontWeight: FontWeight.w800,
                          color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: TextField(
                          controller: _amountController,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          onChanged: (_) => _updateCalculations(),
                          style: PayPinkTheme.display(
                            fontSize: 26,
                            fontWeight: FontWeight.w700,
                            color: textInk,
                          ),
                          decoration: InputDecoration(
                            hintText: '0.00',
                            hintStyle: PayPinkTheme.display(
                              fontSize: 26,
                              fontWeight: FontWeight.w700,
                              color: textMuted,
                            ),
                            border: InputBorder.none,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),

                // Quick Amount Chips
                Row(
                  children: [
                    _buildAmtChip('+₱10', 10, isDark: isDark),
                    const SizedBox(width: 8),
                    _buildAmtChip('+₱20', 20, isDark: isDark),
                    const SizedBox(width: 8),
                    _buildAmtChip('+₱50', 50, isDark: isDark),
                  ],
                ),
                const SizedBox(height: 14),

                // Standing Instructions (Recurring Transfer) Switch & Setup
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: _isStandingInstruction
                        ? (isDark ? PayPinkTheme.wine.withValues(alpha: 0.25) : PayPinkTheme.pinkSubtle)
                        : cardBg,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: _isStandingInstruction ? PayPinkTheme.wine.withValues(alpha: 0.5) : textLine,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              Icon(
                                Icons.event_repeat_rounded,
                                size: 18,
                                color: _isStandingInstruction ? (isDark ? PayPinkTheme.pink : PayPinkTheme.wine) : textMuted,
                              ),
                              const SizedBox(width: 8),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Standing Instruction',
                                    style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700, color: textInk),
                                  ),
                                  Text(
                                    'Automate recurring schedule',
                                    style: PayPinkTheme.body(fontSize: 9.5, color: PayPinkTheme.muted),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          Switch.adaptive(
                            value: _isStandingInstruction,
                            activeTrackColor: PayPinkTheme.wine,
                            onChanged: (val) => setState(() => _isStandingInstruction = val),
                          ),
                        ],
                      ),
                      if (_isStandingInstruction) ...[
                        const SizedBox(height: 8),
                        Divider(color: textLine, height: 1),
                        const SizedBox(height: 8),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text('Frequency', style: PayPinkTheme.body(fontSize: 10.5, fontWeight: FontWeight.w600, color: textInk)),
                            Row(
                              children: ['Weekly', '15th & 30th', 'Monthly'].map((freq) {
                                final isSelected = _standingFrequency == freq;
                                return GestureDetector(
                                  onTap: () => setState(() => _standingFrequency = freq),
                                  child: Container(
                                    margin: const EdgeInsets.only(left: 6),
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                    decoration: BoxDecoration(
                                      color: isSelected ? PayPinkTheme.wine : cardBg,
                                      borderRadius: BorderRadius.circular(6),
                                      border: Border.all(color: isSelected ? PayPinkTheme.wine : textLine),
                                    ),
                                    child: Text(
                                      freq,
                                      style: PayPinkTheme.body(
                                        fontSize: 9.5,
                                        fontWeight: FontWeight.w700,
                                        color: isSelected ? Colors.white : textInk,
                                      ),
                                    ),
                                  ),
                                );
                              }).toList(),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text('Next Execution Date', style: PayPinkTheme.body(fontSize: 10, color: textMuted)),
                            Text('Oct 15, 2026 (Auto EOD cutoff)', style: PayPinkTheme.mono(fontSize: 9.5, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine)),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Asynchronous Risk Analytics Panel
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: isDark ? PayPinkTheme.darkPaper.withValues(alpha: 0.85) : Colors.white.withValues(alpha: 0.85),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: isHighRisk ? PayPinkTheme.red.withValues(alpha: 0.4) : textLine,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              Icon(
                                Icons.shield_rounded,
                                size: 14,
                                color: isHighRisk ? PayPinkTheme.red : PayPinkTheme.green,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                'Risk Analytics Score',
                                style: PayPinkTheme.body(
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w700,
                                  color: textMuted,
                                ),
                              ),
                            ],
                          ),
                          Text(
                            '${_riskScore.toStringAsFixed(2)} (${isHighRisk ? 'FRAUD THREAT >0.85' : 'SAFE'})',
                            style: PayPinkTheme.mono(
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                              color: isHighRisk ? PayPinkTheme.red : PayPinkTheme.green,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: _riskScore.clamp(0.0, 1.0),
                          minHeight: 6,
                          backgroundColor: isDark ? PayPinkTheme.darkLine : Colors.grey.shade200,
                          valueColor: AlwaysStoppedAnimation<Color>(
                            isHighRisk ? PayPinkTheme.red : PayPinkTheme.green,
                          ),
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        isHighRisk
                            ? 'Transfer will be automatically dropped by Asynchronous Risk Engine (Score > 0.85 threshold).'
                            : 'Asynchronous Python asyncio screening score is healthy (< 0.85). Normal remittance clearing.',
                        style: PayPinkTheme.body(fontSize: 9.5, color: textMuted),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),

                // Temenos T24 OFSCore Preview Box
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.15) : PayPinkTheme.wine.withValues(alpha: 0.04),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.4) : PayPinkTheme.wine.withValues(alpha: 0.15)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Simulated Temenos T24 Core OFS String',
                        style: PayPinkTheme.display(
                          fontSize: 9,
                          fontWeight: FontWeight.w700,
                          color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _ofscorePreview,
                        style: PayPinkTheme.mono(
                          fontSize: 9.5,
                          color: textInk,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Transfer fee', style: PayPinkTheme.body(fontSize: 11.5, color: textMuted)),
                    Text('₱0.00', style: PayPinkTheme.display(fontSize: 12.5, fontWeight: FontWeight.w700, color: textInk)),
                  ],
                ),
                const SizedBox(height: 16),

                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton(
                    onPressed: _handleReviewTransfer,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: PayPinkTheme.wine,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      textStyle: PayPinkTheme.display(fontSize: 13.5, fontWeight: FontWeight.w700),
                    ),
                    child: const Text('Review transfer →'),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 90),
        ],
      ),
    );
  }

  Widget _buildAmtChip(String label, double amount, {bool isDark = false}) {
    return GestureDetector(
      onTap: () => _addQuickAmount(amount),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.2) : PayPinkTheme.pinkSubtle,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.5) : PayPinkTheme.pink),
        ),
        child: Text(
          label,
          style: PayPinkTheme.body(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
          ),
        ),
      ),
    );
  }

  Widget _buildTabBtn(String label, int index, {bool isDark = false}) {
    final isSelected = _selectedModeIndex == index;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return Expanded(
      child: GestureDetector(
        onTap: () {
          setState(() {
            _selectedModeIndex = index;
            _updateCalculations();
          });
        },
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: isSelected ? PayPinkTheme.wine : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: PayPinkTheme.body(
              fontSize: 10.5,
              fontWeight: FontWeight.w700,
              color: isSelected ? Colors.white : textMuted,
            ),
          ),
        ),
      ),
    );
  }
}
