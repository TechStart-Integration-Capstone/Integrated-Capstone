import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';

class PayPinkBottomSheets {
  static void showAccountDetails(
    BuildContext context, {
    required String name,
    required String fullNumber,
    required double balance,
    required String type,
    required String status,
    required String ledgerId,
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
            const _DetailRow(label: 'Holder', value: 'Trixie Samson'),
            _DetailRow(label: 'Account Type', value: type, isMono: true),
            _DetailRow(
              label: 'Status',
              value: status,
              valueColor: PayPinkTheme.green,
              isBold: true,
            ),
            _DetailRow(label: 'Ledger Identifier', value: ledgerId, isMono: true),
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

  static void showTransactionDetails(
    BuildContext context, {
    required String name,
    required String refId,
    required String date,
    required double amount,
    required bool isCredit,
    required String ofscore,
    required String auditHash,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SheetContainer(
        title: 'Transaction Details',
        child: Column(
          children: [
            const SizedBox(height: 6),
            Text(
              '${isCredit ? '+' : '-'}₱${amount.toStringAsFixed(2)}',
              style: PayPinkTheme.display(
                fontSize: 32,
                fontWeight: FontWeight.w800,
                color: isCredit ? PayPinkTheme.green : PayPinkTheme.ink,
              ),
            ),
            const SizedBox(height: 4),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: PayPinkTheme.greenBg,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                'Completed',
                style: PayPinkTheme.body(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: PayPinkTheme.green,
                ),
              ),
            ),
            const SizedBox(height: 18),
            _DetailRow(label: 'Reference ID', value: refId, isMono: true),
            _DetailRow(label: 'Date', value: date),
            _DetailRow(label: 'Temenos OFSCore Record', value: ofscore, isMono: true, isSmall: true),
            _DetailRow(label: 'PostgreSQL Immutable Hash', value: auditHash, isMono: true, isSmall: true),
            const SizedBox(height: 20),
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
                child: const Text('Close'),
              ),
            ),
          ],
        ),
      ),
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

  static void showHardwareVault(
    BuildContext context, {
    required String hardwareKeyId,
    required String circuitStatus,
    required String gatewayRoute,
    required String jwtToken,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SheetContainer(
        title: 'Hardware KeyStore & Vault',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Capstone 2 Mobile Layer: Credentials and JWT session keys secured with native OS hardware encryption layers (iOS Keychain / Android KeyStore).',
              style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted, height: 1.4),
            ),
            const SizedBox(height: 16),
            _DetailRow(label: 'Hardware Key ID', value: hardwareKeyId, isMono: true, isSmall: true),
            _DetailRow(
              label: 'Circuit Breaker State',
              value: circuitStatus,
              valueColor: circuitStatus.contains('OPEN') ? PayPinkTheme.red : PayPinkTheme.green,
              isBold: true,
            ),
            _DetailRow(label: 'Edge Gateway Route', value: gatewayRoute, isMono: true),
            _DetailRow(label: 'Encrypted JWT Token', value: jwtToken, isMono: true, isSmall: true),
            const SizedBox(height: 20),
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
                child: const Text('Close Vault Inspector'),
              ),
            ),
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

  static void showRequestQr(BuildContext context) {
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
              'Trixie Samson · Everyday account',
              style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.bold),
            ),
            Text(
              '001 1 5046 8001',
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

  static void showScanQr(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SheetContainer(
        title: 'Scan PayPink QR',
        child: Column(
          children: [
            Text(
              'Point camera at recipient PayPink QR code or EMVCo-compliant QRPH',
              textAlign: TextAlign.center,
              style: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.muted),
            ),
            const SizedBox(height: 18),
            Container(
              width: 220,
              height: 220,
              decoration: BoxDecoration(
                color: PayPinkTheme.ink,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Stack(
                alignment: Alignment.center,
                children: [
                  Container(
                    width: 150,
                    height: 150,
                    decoration: BoxDecoration(
                      border: Border.all(color: PayPinkTheme.pink, width: 2),
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  const Positioned(
                    bottom: 20,
                    child: Text(
                      'Align QR inside the box',
                      style: TextStyle(color: Colors.white70, fontSize: 10),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: OutlinedButton(
                onPressed: () => Navigator.pop(ctx),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: PayPinkTheme.wine),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
                child: Text(
                  'Cancel',
                  style: PayPinkTheme.body(fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
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
    return Container(
      padding: const EdgeInsets.only(top: 10, left: 20, right: 20, bottom: 28),
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
                title,
                style: PayPinkTheme.display(fontSize: 17, fontWeight: FontWeight.w700),
              ),
              if (trailing != null)
                trailing!
              else
                IconButton(
                  icon: const Icon(Icons.close, size: 20, color: PayPinkTheme.muted),
                  onPressed: () => Navigator.pop(context),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
            ],
          ),
          const SizedBox(height: 14),
          child,
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
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: PayPinkTheme.body(fontSize: 12, color: PayPinkTheme.muted),
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
                      color: valueColor ?? PayPinkTheme.ink,
                    )
                  : PayPinkTheme.body(
                      fontSize: isSmall ? 10.5 : 12.5,
                      fontWeight: isBold ? FontWeight.w700 : FontWeight.w500,
                      color: valueColor ?? PayPinkTheme.ink,
                    ),
            ),
          ),
        ],
      ),
    );
  }
}
