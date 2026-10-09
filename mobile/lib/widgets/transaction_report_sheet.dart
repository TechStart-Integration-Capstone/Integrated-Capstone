import 'package:flutter/material.dart';
import 'package:printing/printing.dart';
import '../services/account_service.dart';
import '../services/report_service.dart';
import '../theme/paypink_theme.dart';

/// "Generate transaction report": choose an account and date range, then save or share
/// the server-generated PDF. Mirrors the web dialog in frontend/bank/reports.js.
class TransactionReportSheet extends StatefulWidget {
  final List<BankAccount> accounts;
  const TransactionReportSheet({super.key, required this.accounts});

  static Future<void> show(BuildContext context, {required List<BankAccount> accounts}) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => TransactionReportSheet(accounts: accounts),
    );
  }

  @override
  State<TransactionReportSheet> createState() => _TransactionReportSheetState();
}

class _TransactionReportSheetState extends State<TransactionReportSheet> {
  late int? _accountId = widget.accounts.isNotEmpty ? widget.accounts.first.accountId : null;
  late DateTime _to = ReportService.manilaToday();
  late DateTime _from = DateTime(_to.year, _to.month, 1);
  bool _busy = false;
  String? _error;

  Future<void> _pickDate({required bool isFrom}) async {
    final today = ReportService.manilaToday();
    final picked = await showDatePicker(
      context: context,
      initialDate: isFrom ? _from : _to,
      firstDate: DateTime(today.year - 5),
      lastDate: today,
    );
    if (picked == null || !mounted) return;
    setState(() {
      if (isFrom) {
        _from = picked;
      } else {
        _to = picked;
      }
      _error = null;
    });
  }

  Future<void> _download() async {
    final accountId = _accountId;
    if (accountId == null) return;
    final rangeError = ReportService.validateRange(_from, _to);
    if (rangeError != null) {
      setState(() => _error = rangeError);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final bytes = await ReportService.downloadTransactionsPdf(accountId: accountId, from: _from, to: _to);
      final name = 'PayPink-Transactions-${ReportService.isoDate(_from)}-to-${ReportService.isoDate(_to)}.pdf';
      await Printing.sharePdf(bytes: bytes, filename: name);
      if (mounted) Navigator.of(context).pop();
    } on ReportException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'The report could not be downloaded. Please try again.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final ink = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final muted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final line = isDark ? PayPinkTheme.darkLine : PayPinkTheme.inputBorder;
    final surface = isDark ? PayPinkTheme.darkPaper : Colors.white;
    final field = isDark ? PayPinkTheme.darkCard : Colors.white;

    InputDecoration deco(String label) => InputDecoration(
          labelText: label,
          labelStyle: PayPinkTheme.body(fontSize: 12, color: muted),
          filled: true,
          fillColor: field,
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm),
            borderSide: BorderSide(color: line),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm),
            borderSide: const BorderSide(color: PayPinkTheme.focusRing, width: 2),
          ),
        );

    Widget dateField(String label, DateTime value, bool isFrom) => InkWell(
          onTap: _busy ? null : () => _pickDate(isFrom: isFrom),
          borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm),
          child: InputDecorator(
            decoration: deco(label),
            child: Text(BankAccount.formatDueDate(ReportService.isoDate(value)) ?? '',
                style: PayPinkTheme.body(fontSize: 14, color: ink)),
          ),
        );

    return Container(
      padding: EdgeInsets.fromLTRB(22, 18, 22, 24 + MediaQuery.of(context).viewInsets.bottom),
      decoration: BoxDecoration(
        color: surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(PayPinkTheme.radiusXl)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text('Generate transaction report',
                      style: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w700, color: ink)),
                ),
                IconButton(
                  tooltip: 'Close',
                  icon: Icon(Icons.close, color: muted),
                  onPressed: _busy ? null : () => Navigator.of(context).pop(),
                ),
              ],
            ),
            Text('Choose an account and date range to download your transactions as a PDF.',
                style: PayPinkTheme.body(fontSize: 13, color: muted, height: 1.5)),
            const SizedBox(height: 18),
            DropdownButtonFormField<int>(
              initialValue: _accountId,
              isExpanded: true,
              decoration: deco('Account'),
              dropdownColor: field,
              style: PayPinkTheme.body(fontSize: 14, color: ink),
              items: widget.accounts
                  .map((a) => DropdownMenuItem(value: a.accountId, child: Text('${a.displayName} · ${a.maskedNumber}')))
                  .toList(),
              onChanged: _busy ? null : (v) => setState(() => _accountId = v),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(child: dateField('From date', _from, true)),
                const SizedBox(width: 12),
                Expanded(child: dateField('To date', _to, false)),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              'Both dates are included, using Philippine time. Choose up to 366 days. '
              'The report includes all transactions in that period, including pending and failed entries.',
              style: PayPinkTheme.body(fontSize: 11, color: muted, height: 1.6),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Semantics(
                liveRegion: true,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
                  decoration: BoxDecoration(
                    color: PayPinkTheme.errorBg,
                    border: Border.all(color: PayPinkTheme.errorBorder),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(_error!, style: PayPinkTheme.body(fontSize: 12, color: PayPinkTheme.errorText)),
                ),
              ),
            ],
            const SizedBox(height: 18),
            SizedBox(
              height: 49,
              child: FilledButton(
                onPressed: (_busy || _accountId == null) ? null : _download,
                style: FilledButton.styleFrom(
                  backgroundColor: PayPinkTheme.wine,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm)),
                ),
                child: Text(
                  _busy ? 'Generating PDF…' : 'Download PDF',
                  style: PayPinkTheme.body(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.white),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
