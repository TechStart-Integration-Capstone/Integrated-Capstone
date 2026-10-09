import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import '../theme/paypink_theme.dart';
import '../screens/pin_auth_screen.dart';

class PayPinkBottomSheets {
  static void showAccountDetails(
    BuildContext context, {
    required String name,
    required String fullNumber,
    required double balance,
    required String type,
    required String status,
    String? ledgerId,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SheetContainer(
        title: 'Account Details',
        child: Column(
          children: [
            _DetailRow(label: 'Account Number', value: fullNumber, isMono: true),
            _DetailRow(label: 'Available Balance', value: '₱${balance.toStringAsFixed(2)}', isBold: true),
            const _DetailRow(label: 'Amount on Hold / Reserved', value: '₱0.00 (None)', valueColor: PayPinkTheme.green),
            const _DetailRow(label: 'Interest Accrual Rate', value: '1.50% p.a.'),
            const _DetailRow(label: 'Interest Posting', value: 'Monthly (Oct 31, 2026)'),
            _DetailRow(
              label: 'Account Holder',
              value: name.isNotEmpty ? name : 'PayPink Client',
              isBold: true,
            ),
            _DetailRow(label: 'Account Type', value: type, isMono: true),
            _DetailRow(
              label: 'Status',
              value: status,
              valueColor: PayPinkTheme.green,
              isBold: true,
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton.icon(
                onPressed: () {
                  Clipboard.setData(ClipboardData(text: fullNumber.replaceAll(' ', '')));
                  Navigator.pop(ctx);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      backgroundColor: PayPinkTheme.wine,
                      content: Text('Copied $fullNumber to clipboard'),
                    ),
                  );
                },
                icon: const Icon(Icons.copy_rounded, size: 16),
                label: const Text('Copy Account Number'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: PayPinkTheme.wine,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  textStyle: PayPinkTheme.body(fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Loan Product Details Bottom Sheet
  static void showLoanDetails(
    BuildContext context, {
    String loanNumber = '001 9 9921 4410',
    double principal = 50000.00,
    double remaining = 45000.00,
    double amortization = 3750.00,
    String dueDate = 'Oct 25, 2026',
    double interestRate = 5.50,
    String term = '12 Months',
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SheetContainer(
        title: 'Personal Loan Details',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: PayPinkTheme.indigoBg,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: PayPinkTheme.indigo.withValues(alpha: 0.2)),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Remaining Balance',
                        style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '₱${remaining.toStringAsFixed(2)}',
                        style: PayPinkTheme.display(
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                          color: PayPinkTheme.indigo,
                        ),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '• Current / Good',
                      style: PayPinkTheme.body(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: PayPinkTheme.green,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            _DetailRow(label: 'Loan Account', value: loanNumber, isMono: true),
            _DetailRow(label: 'Original Principal', value: '₱${principal.toStringAsFixed(2)}'),
            _DetailRow(label: 'Monthly Amortization', value: '₱${amortization.toStringAsFixed(2)}', isBold: true),
            _DetailRow(label: 'Next Due Date', value: dueDate, isBold: true, valueColor: PayPinkTheme.wine),
            _DetailRow(label: 'Annual Interest Rate', value: '${interestRate.toStringAsFixed(2)}% p.a.'),
            _DetailRow(label: 'Tenure / Term', value: term),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () {
                      Navigator.pop(ctx);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          backgroundColor: PayPinkTheme.wine,
                          content: Text('Amortization schedule downloaded (PDF)'),
                        ),
                      );
                    },
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: PayPinkTheme.line),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: Text(
                      'Schedule',
                      style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w700, color: PayPinkTheme.ink),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton(
                    onPressed: () {
                      Navigator.pop(ctx);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          backgroundColor: PayPinkTheme.wine,
                          content: Text('Payment of ₱${amortization.toStringAsFixed(2)} scheduled'),
                        ),
                      );
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: PayPinkTheme.wine,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: Text(
                      'Pay ₱${amortization.toStringAsFixed(0)}',
                      style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w700, color: Colors.white),
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

  /// Digital Beneficiary Management Bottom Sheet
  static void showBeneficiaryManager(
    BuildContext context, {
    required List<Map<String, String>> beneficiaries,
    required Function(Map<String, String>) onAddBeneficiary,
    required Function(int) onRemoveBeneficiary,
    Function(String name, String number)? onSelect,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setSheetState) {
          final nameController = TextEditingController();
          final numberController = TextEditingController();
          String selectedBank = 'PayPink';

          return _SheetContainer(
            title: 'Favorites',
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'Save your frequent payees as Favorites for quick and secure 1-tap remittances.',
                    style: PayPinkTheme.body(fontSize: 11.5, color: PayPinkTheme.muted),
                  ),
                  const SizedBox(height: 14),

                  // Beneficiaries list
                  ...List.generate(beneficiaries.length, (idx) {
                    final b = beneficiaries[idx];
                    return GestureDetector(
                      onTap: () {
                        if (onSelect != null) {
                          onSelect(b['name'] ?? '', b['number'] ?? '');
                          Navigator.pop(ctx);
                        }
                      },
                      child: Container(
                        margin: const EdgeInsets.only(bottom: 10),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: PayPinkTheme.line),
                        ),
                      child: Row(
                        children: [
                          Container(
                            width: 38,
                            height: 38,
                            decoration: BoxDecoration(
                              color: PayPinkTheme.pinkSubtle,
                              shape: BoxShape.circle,
                              border: Border.all(color: PayPinkTheme.pink),
                            ),
                            child: Center(
                              child: Text(
                                b['avatar'] ?? '??',
                                style: PayPinkTheme.display(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w800,
                                  color: PayPinkTheme.wine,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  b['name'] ?? '',
                                  style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  '${b['bank'] ?? 'PayPink'} · ${b['number'] ?? ''}',
                                  style: PayPinkTheme.mono(fontSize: 10, color: PayPinkTheme.muted),
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.delete_outline_rounded, size: 18, color: PayPinkTheme.muted),
                            onPressed: () {
                              onRemoveBeneficiary(idx);
                              setSheetState(() {});
                            },
                          ),
                        ],
                      ),
                    ),
                    );
                  }),

                  const SizedBox(height: 12),
                  // Add Favorite Button / Dialog
                  OutlinedButton.icon(
                    onPressed: () {
                      String? dialogError;
                      showDialog(
                        context: context,
                        builder: (dialogCtx) => StatefulBuilder(
                          builder: (context, setDialogState) {
                            return AlertDialog(
                              title: Text('Add New Favorite', style: PayPinkTheme.display(fontSize: 16)),
                              content: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  TextField(
                                    controller: nameController,
                                    decoration: const InputDecoration(labelText: 'Recipient Full Name'),
                                  ),
                                  const SizedBox(height: 10),
                                  TextField(
                                    controller: numberController,
                                    keyboardType: TextInputType.number,
                                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                                    decoration: InputDecoration(
                                      labelText: '12-Digit PayPink Account Number',
                                      hintText: 'e.g. 001381233467',
                                      errorText: dialogError,
                                    ),
                                    onChanged: (_) {
                                      if (dialogError != null) {
                                        setDialogState(() => dialogError = null);
                                      }
                                    },
                                  ),
                                  const SizedBox(height: 10),
                                  DropdownButtonFormField<String>(
                                    initialValue: selectedBank,
                                    items: const [
                                      DropdownMenuItem(value: 'PayPink', child: Text('PayPink')),
                                      DropdownMenuItem(value: 'BDO', child: Text('BDO')),
                                      DropdownMenuItem(value: 'BPI', child: Text('BPI')),
                                      DropdownMenuItem(value: 'UnionBank', child: Text('UnionBank')),
                                      DropdownMenuItem(value: 'GCash', child: Text('GCash')),
                                      DropdownMenuItem(value: 'Maya', child: Text('Maya')),
                                    ],
                                    onChanged: (val) {
                                      if (val != null) selectedBank = val;
                                    },
                                    decoration: const InputDecoration(labelText: 'Destination Bank'),
                                  ),
                                ],
                              ),
                              actions: [
                                TextButton(
                                  onPressed: () => Navigator.pop(dialogCtx),
                                  child: const Text('Cancel'),
                                ),
                                ElevatedButton(
                                  onPressed: () {
                                    final name = nameController.text.trim();
                                    final rawNum = numberController.text.trim();
                                    final cleanDigits = rawNum.replaceAll(RegExp(r'\D'), '');

                                    if (name.isEmpty) {
                                      setDialogState(() => dialogError = 'Please enter recipient name');
                                      return;
                                    }

                                    if (selectedBank == 'PayPink' && cleanDigits.length != 12) {
                                      setDialogState(() => dialogError = 'PayPink account number must be exactly 12 digits');
                                      return;
                                    }

                                    final initials = name
                                        .split(' ')
                                        .take(2)
                                        .map((s) => s.isNotEmpty ? s[0].toUpperCase() : '')
                                        .join();

                                    onAddBeneficiary({
                                      'name': name,
                                      'number': cleanDigits.isNotEmpty ? cleanDigits : rawNum,
                                      'avatar': initials.isEmpty ? 'FAV' : initials,
                                      'bank': selectedBank,
                                    });
                                    Navigator.pop(dialogCtx);
                                    setSheetState(() {});
                                  },
                                  style: ElevatedButton.styleFrom(backgroundColor: PayPinkTheme.wine),
                                  child: const Text('Save Favorite', style: TextStyle(color: Colors.white)),
                                ),
                              ],
                            );
                          },
                        ),
                      );
                    },
                    icon: const Icon(Icons.star_outline_rounded, size: 16, color: PayPinkTheme.wine),
                    label: Text(
                      'Add Favorite',
                      style: PayPinkTheme.body(fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: PayPinkTheme.pink),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      minimumSize: const Size(double.infinity, 46),
                    ),
                  ),
                  const SizedBox(height: 14),
                  SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: ElevatedButton(
                      onPressed: () => Navigator.pop(ctx),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: PayPinkTheme.wine,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      child: const Text('Done'),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  /// Transaction Details Bottom Sheet with 6-Step Real-Time Lifecycle Stepper
  static void showTransactionDetails(
    BuildContext context, {
    required String name,
    required String refId,
    required String date,
    required double amount,
    required bool isCredit,
    required String ofscore,
    required String auditHash,
    String? account,
    String? counterparty,
    String? status,
    bool canReverse = false,
    int reversalMinutesRemaining = 0,
    VoidCallback? onReverse,
  }) {
    final isReversed = status?.toUpperCase() == 'REVERSED' || status == 'Refunded';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SheetContainer(
        title: 'Transaction Details',
        child: SingleChildScrollView(
          child: Column(
            children: [
              const SizedBox(height: 6),
              Text(
                '${isCredit ? '+' : '-'}₱${amount.toStringAsFixed(2)}',
                style: PayPinkTheme.display(
                  fontSize: 32,
                  fontWeight: FontWeight.w800,
                  color: isReversed
                      ? PayPinkTheme.muted
                      : (isCredit ? PayPinkTheme.green : PayPinkTheme.ink),
                ).copyWith(decoration: isReversed ? TextDecoration.lineThrough : null),
              ),
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: isReversed
                      ? PayPinkTheme.amberBg
                      : (isCredit ? PayPinkTheme.greenBg : PayPinkTheme.pinkSubtle),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  isReversed
                      ? 'Reversed & Refunded'
                      : (status ?? 'Completed'),
                  style: PayPinkTheme.body(
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    color: isReversed
                        ? PayPinkTheme.amber
                        : (isCredit ? PayPinkTheme.green : PayPinkTheme.wine),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // 15-Minute Reversal Banner & Action
              if (canReverse) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: PayPinkTheme.amberBg,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: PayPinkTheme.amber.withValues(alpha: 0.4)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.timer_outlined, size: 16, color: PayPinkTheme.amber),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              '15-Minute Reversal Active ($reversalMinutesRemaining mins left)',
                              style: PayPinkTheme.body(
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                                color: PayPinkTheme.amber,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      SizedBox(
                        width: double.infinity,
                        height: 40,
                        child: ElevatedButton.icon(
                          onPressed: () {
                            Navigator.pop(ctx);
                            onReverse?.call();
                          },
                          icon: const Icon(Icons.undo_rounded, size: 16),
                          label: Text(
                            'Reverse Transfer & Refund (Instant)',
                            style: PayPinkTheme.body(fontSize: 11.5, fontWeight: FontWeight.w700, color: Colors.white),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: PayPinkTheme.wine,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
              ] else if (!isCredit && !isReversed) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  decoration: BoxDecoration(
                    color: PayPinkTheme.pinkSubtle,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: PayPinkTheme.pink.withValues(alpha: 0.4)),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Request Reversal / Dispute',
                              style: PayPinkTheme.display(fontSize: 11, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '15-min instant window closed. Open a support dispute investigation.',
                              style: PayPinkTheme.body(fontSize: 9.5, color: PayPinkTheme.muted),
                            ),
                          ],
                        ),
                      ),
                      TextButton.icon(
                        onPressed: () {
                          Navigator.pop(ctx);
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              backgroundColor: PayPinkTheme.wine,
                              content: Text('Reversal ticket #REV-${refId.replaceAll(RegExp(r"\D"), "")} filed with PayPink 24/7 Support.'),
                            ),
                          );
                        },
                        icon: const Icon(Icons.support_agent_rounded, size: 15, color: PayPinkTheme.wine),
                        label: Text(
                          'Request',
                          style: PayPinkTheme.body(fontSize: 11, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
              ],

              // Real-Time Transaction Monitoring / Lifecycle Stepper
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: PayPinkTheme.paper,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: PayPinkTheme.line),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'TRANSACTION STATUS',
                      style: PayPinkTheme.mono(fontSize: 9.5, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                    ),
                    const SizedBox(height: 10),
                    _buildLifecycleStep(1, 'Request Submitted', 'Payment request processed securely', isDone: true),
                    _buildLifecycleStep(2, 'Security Cleared', 'Fraud check and MPIN authorization verified', isDone: true),
                    _buildLifecycleStep(3, 'Funds Transferred', 'Clearing and routing approved', isDone: true),
                    _buildLifecycleStep(
                      4,
                      isReversed ? 'Reversed & Refunded' : 'Settled & Completed',
                      isReversed ? 'Funds credited back to source account' : 'Ledger balances updated in real-time',
                      isDone: true,
                      isLast: true,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              _DetailRow(label: 'Reference ID', value: refId, isMono: true),
              _DetailRow(label: 'Date & Time', value: date),
              _DetailRow(label: 'Description', value: name),
              if (account != null && account.isNotEmpty)
                _DetailRow(label: 'Funding Account', value: account),
              if (counterparty != null && counterparty.isNotEmpty)
                _DetailRow(label: 'Recipient / Target', value: counterparty, isBold: true),
              const _DetailRow(label: 'Transfer Fee', value: '₱0.00 (Free)', valueColor: PayPinkTheme.green),
              _DetailRow(
                label: 'Status',
                value: isReversed ? 'Reversed & Refunded' : 'Settled & Verified',
                valueColor: isReversed ? PayPinkTheme.amber : PayPinkTheme.green,
                isBold: true,
              ),

              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        final receiptText = '''
========================================
         PAYPINK OFFICIAL RECEIPT
========================================
Status: ${isReversed ? 'REVERSED / REFUNDED' : 'COMPLETED'}
Reference ID: $refId
Date: $date
Description: $name
${account != null ? 'From: $account\n' : ''}${counterparty != null ? 'To: $counterparty\n' : ''}Amount: ${isCredit ? '+' : '-'}₱${amount.toStringAsFixed(2)}
Fee: ₱0.00
Status: ${isReversed ? 'Reversed' : 'Settled & Verified'}
========================================
Thank you for banking with PayPink!
''';
                        Clipboard.setData(ClipboardData(text: receiptText));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            backgroundColor: PayPinkTheme.wine,
                            content: Text('Receipt copied to clipboard for sharing & records'),
                          ),
                        );
                      },
                      icon: const Icon(Icons.share_rounded, size: 16, color: PayPinkTheme.wine),
                      label: Text(
                        'Share Receipt',
                        style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                      ),
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: PayPinkTheme.pink),
                        backgroundColor: PayPinkTheme.pinkSubtle,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        padding: const EdgeInsets.symmetric(vertical: 14),
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
                        textStyle: PayPinkTheme.body(fontWeight: FontWeight.w700),
                      ),
                      child: const Text('Close'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  static Widget _buildLifecycleStep(int stepNum, String title, String desc, {bool isDone = true, bool isLast = false}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Column(
          children: [
            Container(
              width: 18,
              height: 18,
              decoration: BoxDecoration(
                color: isDone ? PayPinkTheme.green : Colors.grey.shade300,
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Icon(Icons.check, size: 11, color: isDone ? Colors.white : Colors.transparent),
              ),
            ),
            if (!isLast)
              Container(
                width: 2,
                height: 18,
                color: isDone ? PayPinkTheme.green.withValues(alpha: 0.4) : Colors.grey.shade300,
              ),
          ],
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: PayPinkTheme.display(fontSize: 11, fontWeight: FontWeight.w700),
              ),
              Text(
                desc,
                style: PayPinkTheme.body(fontSize: 9, color: PayPinkTheme.muted),
              ),
              const SizedBox(height: 4),
            ],
          ),
        ),
      ],
    );
  }

  static void showNotificationsDrawer(
    BuildContext context, {
    required List<Map<String, dynamic>> notifications,
    required VoidCallback onMarkAllRead,
    required Function(int) onDismiss,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setSheetState) => _SheetContainer(
          title: 'In-App Notifications',
          trailing: TextButton(
            onPressed: () {
              onMarkAllRead();
              setSheetState(() {});
            },
            child: Text(
              'Mark all read',
              style: PayPinkTheme.body(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: PayPinkTheme.wine,
              ),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (notifications.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 30),
                  child: Text(
                    'No notifications at this time.',
                    style: PayPinkTheme.body(color: PayPinkTheme.muted, fontSize: 13),
                  ),
                )
              else
                ...notifications.map((n) {
                  final unread = n['unread'] == true;
                  return Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: unread ? PayPinkTheme.pinkSubtle : Colors.white,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: unread ? PayPinkTheme.pink : PayPinkTheme.line,
                      ),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(
                            color: unread ? PayPinkTheme.wine : Colors.grey.shade200,
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            Icons.notifications_rounded,
                            color: unread ? Colors.white : PayPinkTheme.muted,
                            size: 16,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                n['title'] ?? '',
                                style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                n['message'] ?? '',
                                style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                n['time'] ?? '',
                                style: PayPinkTheme.body(fontSize: 9, color: PayPinkTheme.muted),
                              ),
                            ],
                          ),
                        ),
                        GestureDetector(
                          onTap: () {
                            onDismiss(n['id']);
                            setSheetState(() {});
                          },
                          child: const Padding(
                            padding: EdgeInsets.all(4.0),
                            child: Icon(Icons.close, size: 16, color: PayPinkTheme.muted),
                          ),
                        ),
                      ],
                    ),
                  );
                }),
              const SizedBox(height: 14),
              SizedBox(
                width: double.infinity,
                height: 46,
                child: ElevatedButton(
                  onPressed: () => Navigator.pop(ctx),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: PayPinkTheme.wine,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: const Text('Close Notifications'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Hardware KeyStore & Customer 360 Security Health Inspector
  static void showHardwareVault(
    BuildContext context, {
    String? customerName,
    String hardwareKeyId = 'secp256r1-keychain-hardware-device',
    String circuitStatus = 'CLOSED (Healthy)',
    String gatewayRoute = '127.0.0.1:8080 (Reverse Proxy)',
    String jwtToken = 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiQ1VTVE9NRVIiLCJleHAiOjE3OTEwMTAwMDB9',
    VoidCallback? onLogout,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SheetContainer(
        title: 'Customer 360 & Vault',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: PayPinkTheme.greenBg,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: PayPinkTheme.green.withValues(alpha: 0.3)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.verified_user_rounded, color: PayPinkTheme.green, size: 28),
                  const SizedBox(width: 10),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${customerName?.isNotEmpty == true ? customerName : 'PayPink Client'} · KYC Level 3 Verified',
                        style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700, color: PayPinkTheme.green),
                      ),
                      Text(
                        'Daily Transfer Limit: ₱50,000.00 (Used: ₱0.00)',
                        style: PayPinkTheme.body(fontSize: 10, color: PayPinkTheme.ink),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            Text(
              'Your PayPink account is secured with end-to-end encryption, 6-digit MPIN authentication, and automated fraud monitoring.',
              style: PayPinkTheme.body(fontSize: 11.5, color: PayPinkTheme.muted, height: 1.4),
            ),
            const SizedBox(height: 14),
            const _DetailRow(label: 'Account Tier', value: 'Level 3 Fully Verified', valueColor: PayPinkTheme.green, isBold: true),
            const _DetailRow(label: 'Authentication Mode', value: 'PayPink 6-Digit MPIN'),
            const _DetailRow(label: 'Daily Outgoing Limit', value: '₱50,000.00'),
            const _DetailRow(label: 'Hardware Security', value: 'Active (Device KeyStore)'),
            const SizedBox(height: 14),
            SizedBox(
              width: double.infinity,
              height: 46,
              child: OutlinedButton.icon(
                onPressed: () {
                  Navigator.pop(ctx);
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const PinAuthScreen(mode: PinScreenMode.setup),
                    ),
                  );
                },
                icon: const Icon(Icons.dialpad_rounded, size: 18, color: PayPinkTheme.wine),
                label: Text(
                  'Setup / Change 6-Digit MPIN',
                  style: PayPinkTheme.body(fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                ),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: PayPinkTheme.pink, width: 1.2),
                  backgroundColor: PayPinkTheme.pinkSubtle,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
              ),
            ),
            if (kDebugMode) ...[
              const SizedBox(height: 10),
              Theme(
                data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                child: ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  title: Text(
                    'Developer Diagnostics (Debug Build Only)',
                    style: PayPinkTheme.mono(fontSize: 10, color: PayPinkTheme.muted),
                  ),
                  children: [
                    _DetailRow(label: 'Hardware Key ID', value: hardwareKeyId, isMono: true, isSmall: true),
                    _DetailRow(
                      label: 'Circuit Breaker State',
                      value: circuitStatus,
                      valueColor: circuitStatus.contains('OPEN') ? PayPinkTheme.red : PayPinkTheme.green,
                      isBold: true,
                    ),
                    _DetailRow(label: 'Edge Gateway Route', value: gatewayRoute, isMono: true),
                    _DetailRow(label: 'Encrypted JWT Token', value: jwtToken, isMono: true, isSmall: true),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 18),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: () => Navigator.pop(ctx),
                style: ElevatedButton.styleFrom(
                  backgroundColor: PayPinkTheme.wine,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  textStyle: PayPinkTheme.body(fontWeight: FontWeight.w700),
                ),
                child: const Text('Close Profile & Vault'),
              ),
            ),
            if (onLogout != null) ...[
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                height: 46,
                child: OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(ctx);
                    onLogout();
                  },
                  icon: const Icon(Icons.logout_rounded, color: PayPinkTheme.red, size: 18),
                  label: const Text('Log Out of PayPink', style: TextStyle(color: PayPinkTheme.red, fontWeight: FontWeight.w700)),
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: PayPinkTheme.red, width: 1.2),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  static void showReportModal(BuildContext context) {
    String selectedAccount = 'Everyday account · •••• 5046';
    String selectedPeriod = 'Current Month (October 2026)';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setSheetState) => _SheetContainer(
          title: 'Generate Transaction Report',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Generates an authenticated transaction statement PDF via Apache PDFBox for your records.',
                style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted, height: 1.4),
              ),
              const SizedBox(height: 16),
              Text('Select Account', style: PayPinkTheme.body(fontSize: 11, fontWeight: FontWeight.w600)),
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
                    value: selectedAccount,
                    isExpanded: true,
                    items: const [
                      DropdownMenuItem(
                        value: 'Everyday account · •••• 5046',
                        child: Text('Everyday account · •••• 5046'),
                      ),
                      DropdownMenuItem(
                        value: 'Savings account · 001 1 5968504 7',
                        child: Text('Savings account · 001 1 5968504 7'),
                      ),
                      DropdownMenuItem(
                        value: 'Personal Loan · •••• 9921',
                        child: Text('Personal Loan · •••• 9921'),
                      ),
                    ],
                    onChanged: (val) {
                      if (val != null) setSheetState(() => selectedAccount = val);
                    },
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Text('Period', style: PayPinkTheme.body(fontSize: 11, fontWeight: FontWeight.w600)),
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
                    value: selectedPeriod,
                    isExpanded: true,
                    items: const [
                      DropdownMenuItem(
                        value: 'Current Month (October 2026)',
                        child: Text('Current Month (October 2026)'),
                      ),
                      DropdownMenuItem(
                        value: 'Last 30 Days',
                        child: Text('Last 30 Days'),
                      ),
                      DropdownMenuItem(
                        value: 'Custom Range',
                        child: Text('Custom Range'),
                      ),
                    ],
                    onChanged: (val) {
                      if (val != null) setSheetState(() => selectedPeriod = val);
                    },
                  ),
                ),
              ),
              const SizedBox(height: 22),
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton.icon(
                  onPressed: () {
                    Navigator.pop(ctx);
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        backgroundColor: PayPinkTheme.wine,
                        content: Text('Generating signed PDF statement via Apache PDFBox... Downloaded!'),
                      ),
                    );
                  },
                  icon: const Icon(Icons.picture_as_pdf_rounded, size: 18),
                  label: const Text('Download PDF Statement'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: PayPinkTheme.wine,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    textStyle: PayPinkTheme.body(fontWeight: FontWeight.w700),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static void showRequestQr(
    BuildContext context, {
    String? customerName,
    String? accountNumber,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SheetContainer(
        title: 'Receive via QR Code',
        child: Column(
          children: [
            Text(
              'Share this QR code or payment link to receive instant PHP transfers.',
              textAlign: TextAlign.center,
              style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted),
            ),
            const SizedBox(height: 18),
            Container(
              width: 170,
              height: 170,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: PayPinkTheme.line, width: 2),
                boxShadow: [
                  BoxShadow(
                    color: PayPinkTheme.wine.withValues(alpha: 0.08),
                    blurRadius: 16,
                  ),
                ],
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.qr_code_2_rounded, size: 120, color: PayPinkTheme.wine),
                  Text(
                    'PAYPINK·PH·5046',
                    style: PayPinkTheme.mono(fontSize: 9, color: PayPinkTheme.muted),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Text(
              '${customerName?.isNotEmpty == true ? customerName : 'PayPink Client'} · Everyday account',
              style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.bold),
            ),
            Text(
              accountNumber?.isNotEmpty == true ? accountNumber! : '001 1 5046 8001',
              style: PayPinkTheme.mono(fontSize: 11, color: PayPinkTheme.muted),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton.icon(
                onPressed: () {
                  Clipboard.setData(const ClipboardData(text: 'paypink://pay?acc=001150468001'));
                  Navigator.pop(ctx);
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      backgroundColor: PayPinkTheme.wine,
                      content: Text('Payment link copied to clipboard'),
                    ),
                  );
                },
                icon: const Icon(Icons.link_rounded),
                label: const Text('Copy Payment Link'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: PayPinkTheme.wine,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  textStyle: PayPinkTheme.body(fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

}

class _SheetContainer extends StatelessWidget {
  final String title;
  final Widget child;
  final Widget? trailing;

  const _SheetContainer({
    required this.title,
    required this.child,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.88,
      ),
      padding: const EdgeInsets.only(top: 10, left: 20, right: 20, bottom: 28),
      decoration: BoxDecoration(
        color: isDark ? PayPinkTheme.darkPaper : Colors.white,
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
              color: isDark ? PayPinkTheme.darkLine : Colors.grey.shade300,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(height: 14),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                title,
                style: PayPinkTheme.display(fontSize: 17, fontWeight: FontWeight.w700, color: textInk),
              ),
              if (trailing != null)
                trailing!
              else
                IconButton(
                  icon: Icon(Icons.close, size: 20, color: textMuted),
                  onPressed: () => Navigator.pop(context),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
            ],
          ),
          const SizedBox(height: 14),
          Flexible(child: child),
        ],
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;
  final Color? valueColor;
  final bool isBold;
  final bool isMono;
  final bool isSmall;

  const _DetailRow({
    required this.label,
    required this.value,
    this.valueColor,
    this.isBold = false,
    this.isMono = false,
    this.isSmall = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: PayPinkTheme.body(fontSize: 12, color: textMuted),
          ),
          const SizedBox(width: 14),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: isMono
                  ? PayPinkTheme.mono(
                      fontSize: isSmall ? 9.5 : 12,
                      fontWeight: isBold ? FontWeight.w700 : FontWeight.w500,
                      color: valueColor ?? textInk,
                    )
                  : PayPinkTheme.body(
                      fontSize: isSmall ? 10.5 : 12.5,
                      fontWeight: isBold ? FontWeight.w700 : FontWeight.w500,
                      color: valueColor ?? textInk,
                    ),
            ),
          ),
        ],
      ),
    );
  }
}
