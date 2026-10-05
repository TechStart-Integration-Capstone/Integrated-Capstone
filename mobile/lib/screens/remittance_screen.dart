import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../services/remittance_service.dart';

class RemittanceScreen extends StatefulWidget {
  final Function(double amount, String refId, String source, String recipient) onTransferSuccess;

  const RemittanceScreen({super.key, required this.onTransferSuccess});

  @override
  State<RemittanceScreen> createState() => _RemittanceScreenState();
}

class _RemittanceScreenState extends State<RemittanceScreen> {
  int _selectedModeIndex = 0; // 0: My own, 1: Another PayPink, 2: Outside
  String _sourceAccount = 'everyday-5046';
  String _ownTargetAccount = 'savings-8504';

  final TextEditingController _amountController = TextEditingController(text: '');
  final TextEditingController _recipientController = TextEditingController();

  double _riskScore = 0.12;
  String _ofscorePreview = 'FUNDS.TRANSFER,AUTH/I/PROCESS,//PH100223,DEBIT.ACCT.NO=5046,CREDIT.ACCT.NO=8504,AMOUNT=0.00,CCY=PHP';
  String? _verifiedName;

  final List<Map<String, String>> _favorites = [
    {'name': 'Carlos Mendoza', 'number': '001 1 2234567 8', 'avatar': 'CM'},
    {'name': 'Maria Santos', 'number': '001 1 9876543 2', 'avatar': 'MS'},
    {'name': 'David Lee', 'number': '001 1 4567890 1', 'avatar': 'DL'},
  ];

  @override
  void initState() {
    super.initState();
    _updateCalculations();
  }

  void _updateCalculations() {
    final amt = double.tryParse(_amountController.text) ?? 0.0;
    final debitAcct = _sourceAccount.contains('everyday') ? '5046' : '8504';
    final creditAcct = _selectedModeIndex == 0
        ? (_ownTargetAccount.contains('savings') ? '8504' : '5046')
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

    if (_sourceAccount.contains('everyday') && amt > 50.00) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Insufficient balance in Everyday account (₱50.00)'),
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

    final fromAccountName = _sourceAccount.contains('everyday')
        ? 'Everyday account (•••• 5046)'
        : 'Savings account (•••• 8504)';
    final toAccountName = _selectedModeIndex == 0
        ? 'Savings account · 001 1 5968504 7'
        : (_verifiedName != null ? '$_verifiedName · ${_recipientController.text}' : 'External Account');

    _showConfirmationBottomSheet(amt, fromAccountName, toAccountName);
  }

  void _showConfirmationBottomSheet(double amt, String fromAcc, String toAcc) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.only(top: 10, left: 22, right: 22, bottom: 28),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
          boxShadow: [
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
                color: Colors.grey.shade300,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 14),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Confirm Remittance',
                  style: PayPinkTheme.display(fontSize: 17, fontWeight: FontWeight.w700),
                ),
                IconButton(
                  icon: const Icon(Icons.close, size: 20, color: PayPinkTheme.muted),
                  onPressed: () => Navigator.pop(ctx),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              'Transfer Amount',
              style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted),
            ),
            const SizedBox(height: 4),
            Text(
              '₱${amt.toStringAsFixed(2)}',
              style: PayPinkTheme.display(
                fontSize: 34,
                fontWeight: FontWeight.w800,
                color: PayPinkTheme.ink,
              ),
            ),
            const SizedBox(height: 18),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: PayPinkTheme.paper,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: PayPinkTheme.line),
              ),
              child: Column(
                children: [
                  _confirmRow('From Account', fromAcc),
                  const Divider(color: PayPinkTheme.line, height: 16),
                  _confirmRow('To Recipient', toAcc),
                  const Divider(color: PayPinkTheme.line, height: 16),
                  _confirmRow('Fee', '₱0.00 (Free)', valColor: PayPinkTheme.green),
                  const Divider(color: PayPinkTheme.line, height: 16),
                  _confirmRow('Risk Screening', 'Cleared (${_riskScore.toStringAsFixed(2)})', valColor: PayPinkTheme.green),
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
    final refId = 'TRX-20261002-${DateTime.now().millisecondsSinceEpoch.toString().substring(8)}';

    // Show completion receipt modal
    _showReceiptBottomSheet(amt, refId, fromAcc, toAcc);

    // Notify parent to append transaction
    widget.onTransferSuccess(amt, refId, fromAcc, toAcc);
  }

  void _showReceiptBottomSheet(double amt, String refId, String fromAcc, String toAcc) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.only(top: 10, left: 22, right: 22, bottom: 28),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
          boxShadow: [
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
                color: Colors.grey.shade300,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 20),
            Container(
              width: 60,
              height: 60,
              decoration: const BoxDecoration(
                color: PayPinkTheme.greenBg,
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
              style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 4),
            Text(
              '₱${amt.toStringAsFixed(2)}',
              style: PayPinkTheme.display(
                fontSize: 32,
                fontWeight: FontWeight.w800,
                color: PayPinkTheme.ink,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              'Committed to Dual-Store Ledger & Temenos T24 Core',
              style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted),
            ),
            const SizedBox(height: 18),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: PayPinkTheme.paper,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: PayPinkTheme.line),
              ),
              child: Column(
                children: [
                  _confirmRow('Reference ID', refId, isMono: true),
                  const Divider(color: PayPinkTheme.line, height: 16),
                  _confirmRow('Date & Time', 'Oct 2, 2026 15:00'),
                  const Divider(color: PayPinkTheme.line, height: 16),
                  _confirmRow('Source', fromAcc),
                  const Divider(color: PayPinkTheme.line, height: 16),
                  _confirmRow('Recipient', toAcc),
                  const Divider(color: PayPinkTheme.line, height: 16),
                  _confirmRow('Temenos OFSCore Record', _ofscorePreview, isMono: true, isSmall: true),
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
                      side: const BorderSide(color: PayPinkTheme.pink),
                      backgroundColor: PayPinkTheme.pinkSubtle,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: Text(
                      'Save Receipt (PNG)',
                      style: PayPinkTheme.body(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: PayPinkTheme.wine,
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

  Widget _confirmRow(String label, String value, {Color? valColor, bool isMono = false, bool isSmall = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: PayPinkTheme.body(fontSize: 11.5, color: PayPinkTheme.muted)),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: isMono
                ? PayPinkTheme.mono(
                    fontSize: isSmall ? 9 : 11.5,
                    fontWeight: FontWeight.w600,
                    color: valColor ?? PayPinkTheme.ink,
                  )
                : PayPinkTheme.body(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w700,
                    color: valColor ?? PayPinkTheme.ink,
                  ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
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
              color: PayPinkTheme.ink,
              letterSpacing: -0.8,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Transfer between your accounts or send to another PayPink account.',
            style: PayPinkTheme.body(fontSize: 12.5, color: PayPinkTheme.muted),
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
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: PayPinkTheme.greenBg,
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
                Text('Transfer from', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600)),
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: PayPinkTheme.line),
                  ),
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<String>(
                      value: _sourceAccount,
                      isExpanded: true,
                      items: const [
                        DropdownMenuItem(
                          value: 'everyday-5046',
                          child: Text('Everyday account · •••• 5046 · ₱50.00'),
                        ),
                        DropdownMenuItem(
                          value: 'savings-8504',
                          child: Text('Savings account · 001 1 5968504 7 · ₱0.00'),
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
                    color: PayPinkTheme.wine.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      _buildTabBtn('My own account', 0),
                      _buildTabBtn('Another PayPink', 1),
                      _buildTabBtn('Outside PayPink', 2),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Recipient Fields
                if (_selectedModeIndex == 0) ...[
                  Text('Transfer to', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: PayPinkTheme.line),
                    ),
                    child: DropdownButtonHideUnderline(
                      child: DropdownButton<String>(
                        value: _ownTargetAccount,
                        isExpanded: true,
                        items: const [
                          DropdownMenuItem(
                            value: 'savings-8504',
                            child: Text('Savings account · 001 1 5968504 7 · ₱0.00'),
                          ),
                          DropdownMenuItem(
                            value: 'everyday-5046',
                            child: Text('Everyday account · •••• 5046 · ₱50.00'),
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
                                color: PayPinkTheme.pinkSubtle,
                                shape: BoxShape.circle,
                                border: Border.all(color: PayPinkTheme.pink),
                              ),
                              child: Center(
                                child: Text(
                                  fav['avatar']!,
                                  style: PayPinkTheme.display(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w800,
                                    color: PayPinkTheme.wine,
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              fav['name']!.split(' ')[0],
                              style: PayPinkTheme.body(fontSize: 10, fontWeight: FontWeight.w600),
                            ),
                          ],
                        ),
                      );
                    }),
                  ),
                  const SizedBox(height: 12),
                  Text('Recipient Account Number', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _recipientController,
                    onChanged: (_) => _updateCalculations(),
                    decoration: InputDecoration(
                      hintText: 'e.g. 001 1 2234567 8 (Carlos Mendoza)',
                      filled: true,
                      fillColor: Colors.white,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: const BorderSide(color: PayPinkTheme.line),
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                    ),
                  ),
                  if (_verifiedName != null) ...[
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color: PayPinkTheme.greenBg,
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
                Text('Amount', style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w600)),
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: PayPinkTheme.line),
                  ),
                  child: Row(
                    children: [
                      Text(
                        'PHP',
                        style: PayPinkTheme.display(
                          fontSize: 18,
                          fontWeight: FontWeight.w800,
                          color: PayPinkTheme.wine,
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
                          ),
                          decoration: const InputDecoration(
                            hintText: '0.00',
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
                    _buildAmtChip('+₱10', 10),
                    const SizedBox(width: 8),
                    _buildAmtChip('+₱20', 20),
                    const SizedBox(width: 8),
                    _buildAmtChip('+₱50', 50),
                  ],
                ),
                const SizedBox(height: 16),

                // Asynchronous Risk Analytics Panel
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.85),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: isHighRisk ? PayPinkTheme.red.withValues(alpha: 0.4) : PayPinkTheme.line,
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
                                  color: PayPinkTheme.muted,
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
                          backgroundColor: Colors.grey.shade200,
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
                        style: PayPinkTheme.body(fontSize: 9.5, color: PayPinkTheme.muted),
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
                    color: PayPinkTheme.wine.withValues(alpha: 0.04),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: PayPinkTheme.wine.withValues(alpha: 0.15)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Simulated Temenos T24 Core OFS String',
                        style: PayPinkTheme.display(
                          fontSize: 9,
                          fontWeight: FontWeight.w700,
                          color: PayPinkTheme.wine,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _ofscorePreview,
                        style: PayPinkTheme.mono(
                          fontSize: 9.5,
                          color: PayPinkTheme.ink,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Transfer fee', style: PayPinkTheme.body(fontSize: 11.5, color: PayPinkTheme.muted)),
                    Text('₱0.00', style: PayPinkTheme.display(fontSize: 12.5, fontWeight: FontWeight.w700)),
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
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildAmtChip(String label, double amount) {
    return GestureDetector(
      onTap: () => _addQuickAmount(amount),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: PayPinkTheme.pinkSubtle,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: PayPinkTheme.pink),
        ),
        child: Text(
          label,
          style: PayPinkTheme.body(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: PayPinkTheme.wine,
          ),
        ),
      ),
    );
  }

  Widget _buildTabBtn(String label, int index) {
    final isSelected = _selectedModeIndex == index;
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
              color: isSelected ? Colors.white : PayPinkTheme.muted,
            ),
          ),
        ),
      ),
    );
  }
}
