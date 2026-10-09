import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/bottom_sheets.dart';
import '../services/remittance_service.dart';
import '../services/account_service.dart';
import '../widgets/pin_auth_sheet.dart';

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

  String? _verifiedName;

  // MOB-305: saga 202 grace-window state
  Timer? _statusPollTimer;
  String? _pendingReferenceNo;
  bool _isSagaPending = false;
  String? _sagaErrorMessage;
  double? _pendingAmt;
  String? _pendingFromAcc;
  String? _pendingToAcc;

  @override
  void dispose() {
    _statusPollTimer?.cancel();
    _amountController.dispose();
    _recipientController.dispose();
    super.dispose();
  }

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
    setState(() {
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

  void _swapOwnAccounts() {
    HapticFeedback.lightImpact();
    setState(() {
      final temp = _sourceAccount;
      _sourceAccount = _ownTargetAccount;
      _ownTargetAccount = temp;
      _updateCalculations();
    });
  }

  BankAccount? _getAccount(String acctNum) {
    if (widget.accounts == null || widget.accounts!.isEmpty) return null;
    try {
      return widget.accounts!.firstWhere(
        (a) => a.accountNumber == acctNum,
        orElse: () => widget.accounts!.first,
      );
    } catch (_) {
      return null;
    }
  }

  void _showAccountPicker({required bool isSource}) {
    if (widget.accounts == null || widget.accounts!.length <= 1) return;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final eligible = isSource
        ? widget.accounts!.where((a) => a.canBeTransferSource).toList()
        : widget.accounts!.toList();

    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: isDark ? PayPinkTheme.darkPaper : Colors.white,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: isDark ? textLine : Colors.grey.shade300,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 14),
            Text(
              isSource ? 'Select Funding Account' : 'Select Destination Account',
              style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w700, color: textInk),
            ),
            const SizedBox(height: 12),
            ...eligible.map((acct) {
              final isCurrent = isSource ? _sourceAccount == acct.accountNumber : _ownTargetAccount == acct.accountNumber;
              return Container(
                margin: const EdgeInsets.only(bottom: 8),
                decoration: BoxDecoration(
                  color: isCurrent
                      ? (isDark ? PayPinkTheme.wine.withValues(alpha: 0.25) : PayPinkTheme.pinkSubtle)
                      : (isDark ? PayPinkTheme.darkCard : Colors.grey.shade50),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: isCurrent ? PayPinkTheme.wine : textLine,
                  ),
                ),
                child: ListTile(
                  title: Text(acct.displayName, style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700, color: textInk)),
                  subtitle: Text('${acct.maskedNumber} · Available: ₱${acct.currentBalance.toStringAsFixed(2)}', style: PayPinkTheme.body(fontSize: 11, color: textMuted)),
                  trailing: isCurrent ? const Icon(Icons.check_circle_rounded, color: PayPinkTheme.wine, size: 20) : null,
                  onTap: () {
                    Navigator.pop(ctx);
                    setState(() {
                      if (isSource) {
                        _sourceAccount = acct.accountNumber;
                        if (_ownTargetAccount == acct.accountNumber) {
                          final other = widget.accounts!.firstWhere((a) => a.accountNumber != acct.accountNumber, orElse: () => acct);
                          _ownTargetAccount = other.accountNumber;
                        }
                      } else {
                        _ownTargetAccount = acct.accountNumber;
                        if (_sourceAccount == acct.accountNumber) {
                          final other = widget.accounts!.firstWhere((a) => a.accountNumber != acct.accountNumber && a.canBeTransferSource, orElse: () => acct);
                          _sourceAccount = other.accountNumber;
                        }
                      }
                      _updateCalculations();
                    });
                  },
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  Widget _buildBetweenMyAccountsSelector({
    required bool isDark,
    required Color cardBg,
    required Color textLine,
    required Color textInk,
    required Color textMuted,
  }) {
    final fromAcct = _getAccount(_sourceAccount);
    final toAcct = _getAccount(_ownTargetAccount);

    final fromName = fromAcct?.displayName ?? 'Checking Account';
    final fromNum = fromAcct?.maskedNumber ?? (_sourceAccount.length >= 4 ? '•••• ${_sourceAccount.substring(_sourceAccount.length - 4)}' : _sourceAccount);
    final fromBal = fromAcct?.currentBalance ?? 0.0;

    final toName = toAcct?.displayName ?? 'Savings Account';
    final toNum = toAcct?.maskedNumber ?? (_ownTargetAccount.length >= 4 ? '•••• ${_ownTargetAccount.substring(_ownTargetAccount.length - 4)}' : _ownTargetAccount);
    final toBal = toAcct?.currentBalance ?? 0.0;

    return Container(
      decoration: BoxDecoration(
        color: isDark ? PayPinkTheme.darkCard.withValues(alpha: 0.5) : Colors.white.withValues(alpha: 0.7),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: isDark ? PayPinkTheme.darkGlassBorder : PayPinkTheme.pink.withValues(alpha: 0.3)),
      ),
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          // From Card
          InkWell(
            onTap: () => _showAccountPicker(isSource: true),
            borderRadius: BorderRadius.circular(14),
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: cardBg,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: textLine),
              ),
              child: Row(
                children: [
                  Container(
                    width: 38,
                    height: 38,
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.arrow_upward_rounded,
                      color: PayPinkTheme.green,
                      size: 18,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
                              decoration: BoxDecoration(
                                color: isDark ? Colors.white.withValues(alpha: 0.08) : PayPinkTheme.pinkSubtle,
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Text(
                                'FROM',
                                style: PayPinkTheme.mono(fontSize: 8.5, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                fromName,
                                style: PayPinkTheme.display(fontSize: 12.5, fontWeight: FontWeight.w700, color: textInk),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            Icon(Icons.unfold_more_rounded, size: 14, color: textMuted),
                          ],
                        ),
                        const SizedBox(height: 2),
                        Text(fromNum, style: PayPinkTheme.mono(fontSize: 10, color: textMuted)),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        '₱${fromBal.toStringAsFixed(2)}',
                        style: PayPinkTheme.display(fontSize: 13.5, fontWeight: FontWeight.w800, color: textInk),
                      ),
                      Text('Available', style: PayPinkTheme.body(fontSize: 9.5, color: textMuted)),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Quick One-Tap Swap Button
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Stack(
              alignment: Alignment.center,
              children: [
                Divider(color: textLine, height: 28),
                InkWell(
                  onTap: _swapOwnAccounts,
                  borderRadius: BorderRadius.circular(20),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: isDark
                            ? [PayPinkTheme.wine, const Color(0xFF6A1A3A)]
                            : [PayPinkTheme.wine, const Color(0xFF8B2550)],
                      ),
                      borderRadius: BorderRadius.circular(20),
                      boxShadow: [
                        BoxShadow(
                          color: PayPinkTheme.wine.withValues(alpha: 0.35),
                          blurRadius: 8,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.swap_vert_rounded, color: Colors.white, size: 16),
                        const SizedBox(width: 5),
                        Text(
                          'One-Tap Swap',
                          style: PayPinkTheme.body(fontSize: 10.5, fontWeight: FontWeight.w700, color: Colors.white),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),

          // To Card
          InkWell(
            onTap: () => _showAccountPicker(isSource: false),
            borderRadius: BorderRadius.circular(14),
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: cardBg,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: textLine),
              ),
              child: Row(
                children: [
                  Container(
                    width: 38,
                    height: 38,
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.arrow_downward_rounded,
                      color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                      size: 18,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
                              decoration: BoxDecoration(
                                color: isDark ? Colors.white.withValues(alpha: 0.08) : PayPinkTheme.greenBg,
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Text(
                                'TO',
                                style: PayPinkTheme.mono(fontSize: 8.5, fontWeight: FontWeight.w700, color: PayPinkTheme.green),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                toName,
                                style: PayPinkTheme.display(fontSize: 12.5, fontWeight: FontWeight.w700, color: textInk),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            Icon(Icons.unfold_more_rounded, size: 14, color: textMuted),
                          ],
                        ),
                        const SizedBox(height: 2),
                        Text(toNum, style: PayPinkTheme.mono(fontSize: 10, color: textMuted)),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        '₱${toBal.toStringAsFixed(2)}',
                        style: PayPinkTheme.display(fontSize: 13.5, fontWeight: FontWeight.w800, color: textInk),
                      ),
                      Text('Balance', style: PayPinkTheme.body(fontSize: 9.5, color: textMuted)),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
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

    final srcAcc = _getAccount(_sourceAccount);
    final srcDisplayName = srcAcc?.displayName ?? 'Checking Account';
    final srcLast4 = _sourceAccount.length >= 4 ? _sourceAccount.substring(_sourceAccount.length - 4) : _sourceAccount;
    final fromAccountName = '$srcDisplayName (•••• $srcLast4)';

    String toAccountName = '';
    if (_selectedModeIndex == 0) {
      final targetAcc = _getAccount(_ownTargetAccount);
      final targetDisplayName = targetAcc?.displayName ?? 'Savings Account';
      final targetLast4 = _ownTargetAccount.length >= 4 ? _ownTargetAccount.substring(_ownTargetAccount.length - 4) : _ownTargetAccount;
      toAccountName = '$targetDisplayName (•••• $targetLast4)';
    } else if (_selectedModeIndex == 1) {
      final recNumber = _recipientController.text.trim();
      final recLast4 = recNumber.length >= 4 ? recNumber.substring(recNumber.length - 4) : recNumber;
      final name = _verifiedName ?? 'PayPink Account';
      toAccountName = '$name (•••• $recLast4)';
    } else {
      final recNumber = _recipientController.text.trim();
      final recLast4 = recNumber.length >= 4 ? recNumber.substring(recNumber.length - 4) : recNumber;
      toAccountName = '$_destinationBank Account (•••• $recLast4)';
    }

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
                  _executeTransferWithPin(amt, fromAcc, toAcc);
                },
                icon: const Icon(Icons.send_rounded, size: 18),
                label: const Text('Confirm & Send Transfer'),
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

  void _executeTransferWithPin(double amt, String fromAcc, String toAcc) async {
    final pinOk = await PinAuthSheet.show(
      context,
      title: 'Authorize Transfer',
      description: 'Enter your 6-digit MPIN to authorize transfer of ₱${amt.toStringAsFixed(2)}',
      amount: amt,
    );
    if (!pinOk) return;

    final cleanDest = _selectedModeIndex == 0
        ? _ownTargetAccount
        : _recipientController.text.replaceAll(' ', '');

    final result = await RemittanceService.submitRemittance(
      sourceAccountId: _sourceAccount,
      destinationAccountNumber: cleanDest,
      amount: amt,
    );

    if (!mounted) return;

    if (result.success && result.status == 'RESERVED') {
      // 202 — enter saga grace window
      final ref = result.referenceId ?? '';
      setState(() {
        _pendingReferenceNo = ref;
        _isSagaPending = true;
        _sagaErrorMessage = null;
      });
      _startStatusPolling(ref, amt, fromAcc, toAcc);
    } else if (result.success) {
      final cleanRef = result.referenceId != null && result.referenceId!.isNotEmpty
          ? result.referenceId!
          : 'TXN-2026-${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}';
      _showScreenshotReceiptDialog(amt, cleanRef, fromAcc, toAcc);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text(result.message),
        ),
      );
    }
  }

  // ── MOB-305: Saga 202 grace-window helpers ────────────────────────────────

  void _startStatusPolling(String ref, double amt, String fromAcc, String toAcc) {
    _pendingAmt = amt;
    _pendingFromAcc = fromAcc;
    _pendingToAcc = toAcc;
    _statusPollTimer?.cancel();
    _statusPollTimer = Timer.periodic(const Duration(seconds: 2), (timer) async {
      final status = await RemittanceService.getStatus(ref);
      if (!mounted) { timer.cancel(); return; }
      if (status == 'COMPLETED' || status == 'POSTED') {
        timer.cancel();
        setState(() {
          _isSagaPending = false;
          _pendingReferenceNo = null;
          _pendingAmt = null;
          _pendingFromAcc = null;
          _pendingToAcc = null;
        });
        _showScreenshotReceiptDialog(amt, ref, fromAcc, toAcc);
      } else if (status == 'FAILED' || status == 'CANCELLED') {
        timer.cancel();
        setState(() {
          _isSagaPending = false;
          _pendingReferenceNo = null;
          _pendingAmt = null;
          _pendingFromAcc = null;
          _pendingToAcc = null;
          _sagaErrorMessage = status == 'CANCELLED'
              ? 'Transfer was cancelled.'
              : 'Transfer failed. Please try again.';
        });
      }
    });
  }

  Future<void> _onSagaCancel() async {
    final ref = _pendingReferenceNo;
    if (ref == null) return;
    _statusPollTimer?.cancel();
    final result = await RemittanceService.cancel(ref);
    if (!mounted) return;
    setState(() {
      _isSagaPending = false;
      _pendingReferenceNo = null;
      _pendingAmt = null;
      _pendingFromAcc = null;
      _pendingToAcc = null;
      _sagaErrorMessage = result.success ? null : result.message;
    });
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      backgroundColor: result.success ? PayPinkTheme.green : PayPinkTheme.red,
      content: Text(result.message),
    ));
  }

  Future<void> _onSagaSendNow() async {
    final ref = _pendingReferenceNo;
    final amt = _pendingAmt;
    final fromAcc = _pendingFromAcc;
    final toAcc = _pendingToAcc;
    if (ref == null) return;
    _statusPollTimer?.cancel();
    final result = await RemittanceService.sendNow(ref);
    if (!mounted) return;
    if (result.success) {
      setState(() {
        _isSagaPending = false;
        _pendingReferenceNo = null;
        _pendingAmt = null;
        _pendingFromAcc = null;
        _pendingToAcc = null;
      });
      if (amt != null && fromAcc != null && toAcc != null) {
        _showScreenshotReceiptDialog(amt, ref, fromAcc, toAcc);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          backgroundColor: PayPinkTheme.green,
          content: Text(result.message),
        ));
      }
    } else {
      setState(() { _sagaErrorMessage = result.message; });
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        backgroundColor: PayPinkTheme.red,
        content: Text(result.message),
      ));
    }
  }

  void _showScreenshotReceiptDialog(double amt, String refId, String fromAcc, String toAcc) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final paperBg = isDark ? PayPinkTheme.darkCard : PayPinkTheme.paper;
    final dialogBg = isDark ? PayPinkTheme.darkPaper : Colors.white;

    final now = DateTime.now();
    final months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final hour = now.hour > 12 ? now.hour - 12 : (now.hour == 0 ? 12 : now.hour);
    final ampm = now.hour >= 12 ? 'PM' : 'AM';
    final timeStr = '${hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')} $ampm';
    final formattedDate = '${months[now.month - 1]} ${now.day}, ${now.year} · $timeStr';

    final transferType = _selectedModeIndex == 0
        ? 'Own Accounts Transfer'
        : (_selectedModeIndex == 1 ? 'PayPink P2P Transfer' : 'InstaPay Bank Transfer');

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        backgroundColor: dialogBg,
        elevation: 20,
        insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 390),
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Camera / Screenshot hint pill
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.25) : PayPinkTheme.pinkSubtle,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: PayPinkTheme.pink.withValues(alpha: 0.5)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.camera_alt_rounded, size: 12, color: PayPinkTheme.wine),
                        const SizedBox(width: 5),
                        Text(
                          'SCREENSHOT READY RECEIPT',
                          style: PayPinkTheme.mono(fontSize: 9.5, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),

                  // Brand Header
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        width: 26,
                        height: 26,
                        decoration: const BoxDecoration(
                          gradient: PayPinkTheme.cardPinkGradient,
                          shape: BoxShape.circle,
                        ),
                        child: const Center(
                          child: Icon(Icons.flash_on_rounded, size: 15, color: Colors.white),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'PAYPINK DIGITAL BANK',
                        style: PayPinkTheme.display(
                          fontSize: 14,
                          fontWeight: FontWeight.w800,
                          color: textInk,
                          letterSpacing: 0.8,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'OFFICIAL TRANSFER CONFIRMATION',
                    style: PayPinkTheme.mono(fontSize: 9, fontWeight: FontWeight.w600, color: textMuted),
                  ),

                  const SizedBox(height: 16),

                  // Big Amount & Success Seal
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg,
                      shape: BoxShape.circle,
                      border: Border.all(color: PayPinkTheme.green.withValues(alpha: 0.4), width: 1.5),
                    ),
                    child: const Icon(Icons.check_rounded, color: PayPinkTheme.green, size: 30),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Transfer Successful',
                    style: PayPinkTheme.display(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: PayPinkTheme.green,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '₱${amt.toStringAsFixed(2)}',
                    style: PayPinkTheme.display(
                      fontSize: 34,
                      fontWeight: FontWeight.w800,
                      color: textInk,
                      letterSpacing: -0.5,
                    ),
                  ),

                  const SizedBox(height: 16),

                  // Structured Receipt Table
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                    decoration: BoxDecoration(
                      color: paperBg,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: textLine),
                    ),
                    child: Column(
                      children: [
                        _receiptRow('Reference No.', refId, isMono: true, isDark: isDark, canCopy: true),
                        Divider(color: textLine, height: 14),
                        _receiptRow('Date & Time', formattedDate, isDark: isDark),
                        Divider(color: textLine, height: 14),
                        _receiptRow('Transfer Type', transferType, isDark: isDark),
                        Divider(color: textLine, height: 14),
                        _receiptRow('From Account', fromAcc, isDark: isDark),
                        Divider(color: textLine, height: 14),
                        _receiptRow('To Recipient', toAcc, isDark: isDark, isBold: true),
                        Divider(color: textLine, height: 14),
                        _receiptRow('Service Fee', '₱0.00 (Waived)', valColor: PayPinkTheme.green, isDark: isDark),
                        Divider(color: textLine, height: 14),
                        _receiptRow('Settlement', 'Cleared & Verified', valColor: PayPinkTheme.green, isBold: true, isDark: isDark),
                      ],
                    ),
                  ),



                  const SizedBox(height: 16),

                  // Buttons: Copy Text and Done
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () {
                            final receiptText = '''
========================================
       PAYPINK OFFICIAL RECEIPT
========================================
Reference: $refId
Status: COMPLETED
Amount: ₱${amt.toStringAsFixed(2)}
Type: $transferType
Date: $formattedDate
From: $fromAcc
To: $toAcc
Transfer Fee: ₱0.00
Settlement: Cleared & Verified
15-Min Reversal: Active in Activity
========================================
Thank you for banking with PayPink!
''';
                            Clipboard.setData(ClipboardData(text: receiptText));
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                backgroundColor: PayPinkTheme.wine,
                                content: Text('Receipt details copied! Take a screenshot for photo proof.'),
                              ),
                            );
                          },
                          icon: const Icon(Icons.copy_rounded, size: 15, color: PayPinkTheme.wine),
                          label: Text(
                            'Copy Text',
                            style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                          ),
                          style: OutlinedButton.styleFrom(
                            side: BorderSide(color: isDark ? PayPinkTheme.wine : PayPinkTheme.pink),
                            backgroundColor: isDark ? PayPinkTheme.wine.withValues(alpha: 0.2) : PayPinkTheme.pinkSubtle,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: ElevatedButton(
                          onPressed: () {
                            Navigator.pop(ctx);
                            _amountController.clear();
                            widget.onTransferSuccess(amt, refId, fromAcc, toAcc);
                          },
                          style: ElevatedButton.styleFrom(
                            backgroundColor: PayPinkTheme.wine,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                          child: Text(
                            'Done',
                            style: PayPinkTheme.body(fontSize: 12.5, fontWeight: FontWeight.w700, color: Colors.white),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _receiptRow(String label, String value, {Color? valColor, bool isMono = false, bool isBold = false, bool isDark = false, bool canCopy = false}) {
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: PayPinkTheme.body(fontSize: 11, color: textMuted)),
        const SizedBox(width: 12),
        Flexible(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: Text(
                  value,
                  textAlign: TextAlign.end,
                  style: isMono
                      ? PayPinkTheme.mono(
                          fontSize: 11,
                          fontWeight: isBold ? FontWeight.w800 : FontWeight.w700,
                          color: valColor ?? textInk,
                        )
                      : PayPinkTheme.body(
                          fontSize: 11,
                          fontWeight: isBold ? FontWeight.w800 : FontWeight.w700,
                          color: valColor ?? textInk,
                        ),
                ),
              ),
              if (canCopy) ...[
                const SizedBox(width: 4),
                InkWell(
                  onTap: () {
                    Clipboard.setData(ClipboardData(text: value));
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        backgroundColor: PayPinkTheme.wine,
                        content: Text('Copied $value'),
                        duration: const Duration(seconds: 1),
                      ),
                    );
                  },
                  child: const Icon(Icons.copy_rounded, size: 12, color: PayPinkTheme.wine),
                ),
              ],
            ],
          ),
        ),
      ],
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

  // ── MOB-305: Saga pending screen ─────────────────────────────────────────
  Widget _buildSagaPendingScreen({
    required bool isDark,
    required Color textInk,
    required Color textMuted,
    required Color textLine,
  }) {
    final ref = _pendingReferenceNo ?? '';
    final shortRef = ref.length > 16 ? ref.substring(ref.length - 16) : ref;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          const SizedBox(height: 24),
          const CircularProgressIndicator(color: PayPinkTheme.wine, strokeWidth: 2.5),
          const SizedBox(height: 24),
          Text(
            'Transfer Reserved',
            style: PayPinkTheme.display(
              fontSize: 22,
              fontWeight: FontWeight.w800,
              color: textInk,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            'Your transfer is in a 15-second grace window.\nYou can cancel or send it right now.',
            style: PayPinkTheme.body(fontSize: 13, color: textMuted),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            decoration: BoxDecoration(
              color: isDark ? PayPinkTheme.darkCard : PayPinkTheme.paper,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: textLine),
            ),
            child: Text(
              'Ref: $shortRef',
              style: PayPinkTheme.mono(fontSize: 12, color: textMuted),
            ),
          ),
          if (_sagaErrorMessage != null) ...[
            const SizedBox(height: 12),
            Text(
              _sagaErrorMessage!,
              style: PayPinkTheme.body(fontSize: 12, color: PayPinkTheme.red),
              textAlign: TextAlign.center,
            ),
          ],
          const SizedBox(height: 32),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _onSagaCancel,
                  icon: const Icon(Icons.cancel_outlined, size: 18),
                  label: const Text('Cancel'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: textInk,
                    side: BorderSide(color: textLine),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12)),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _onSagaSendNow,
                  icon: const Icon(Icons.send_rounded, size: 18),
                  label: const Text('Send Now'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: PayPinkTheme.wine,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12)),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final cardBg = isDark ? PayPinkTheme.darkCard : Colors.white;

    // MOB-305: show saga pending screen while RESERVED
    if (_isSagaPending && _pendingReferenceNo != null) {
      return _buildSagaPendingScreen(
        isDark: isDark,
        textInk: textInk,
        textMuted: textMuted,
        textLine: textLine,
      );
    }

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

                // Transfer Mode Segmented Tabs
                Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    color: isDark ? Colors.white.withValues(alpha: 0.06) : PayPinkTheme.wine.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      _buildTabBtn('Between My Accounts', 0, isDark: isDark),
                      _buildTabBtn('Another PayPink', 1, isDark: isDark),
                      _buildTabBtn('Outside PayPink', 2, isDark: isDark),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Account Selection & Inputs
                if (_selectedModeIndex == 0) ...[
                  _buildBetweenMyAccountsSelector(
                    isDark: isDark,
                    cardBg: cardBg,
                    textLine: textLine,
                    textInk: textInk,
                    textMuted: textMuted,
                  ),
                ] else ...[
                  // Source Account Dropdown for external transfers
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
                            ? widget.accounts!.where((a) => a.canBeTransferSource).map((a) {
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
                  const SizedBox(height: 14),
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

                // Security & Protection status — static badge (server-side risk engine handles fraud screening)
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: isDark ? PayPinkTheme.darkPaper.withValues(alpha: 0.85) : Colors.white.withValues(alpha: 0.85),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: textLine),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.shield_rounded,
                        size: 20,
                        color: PayPinkTheme.green,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Protected by PayPink Fraud Shield',
                              style: PayPinkTheme.body(
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                                color: textInk,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Real-time encryption & step-up MPIN verification active.',
                              style: PayPinkTheme.body(fontSize: 10, color: textMuted),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),

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
