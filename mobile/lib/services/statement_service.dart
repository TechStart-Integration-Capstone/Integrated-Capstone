import 'package:flutter/material.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:printing/printing.dart';
import '../screens/transactions_screen.dart';

class StatementService {
  /// Generates and triggers the native OS Share / Print sheet for a branded PayPink statement
  static Future<void> generateAndExportStatement({
    required BuildContext context,
    required String customerName,
    required String accountNumber,
    required String accountType,
    required double currentBalance,
    required List<TransactionItem> transactions,
  }) async {
    final pdf = pw.Document();

    final now = DateTime.now();
    final months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    final statementMonth = months[now.month - 1];
    final statementYear = now.year;
    final dateRangeStr = '$statementMonth 01, $statementYear - $statementMonth ${now.day.toString().padLeft(2, '0')}, $statementYear';

    final totalInflow = transactions
        .where((t) => t.isCredit)
        .fold<double>(0.0, (sum, t) => sum + t.amount);
    final totalOutflow = transactions
        .where((t) => !t.isCredit && t.status != 'REVERSED')
        .fold<double>(0.0, (sum, t) => sum + t.amount);

    final fontRegular = await PdfGoogleFonts.interRegular();
    final fontBold = await PdfGoogleFonts.interBold();

    final wineColor = PdfColor.fromHex('#4A1528');
    final pinkColor = PdfColor.fromHex('#F6A4C0');
    final darkBg = PdfColor.fromHex('#1E0B14');
    final greyText = PdfColor.fromHex('#666666');
    final lightGreyBg = PdfColor.fromHex('#F9F6F7');

    pdf.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(36),
        theme: pw.ThemeData.withFont(base: fontRegular, bold: fontBold),
        build: (pw.Context ctx) {
          return [
            // Header Banner
            pw.Container(
              padding: const pw.EdgeInsets.all(20),
              decoration: pw.BoxDecoration(
                color: wineColor,
                borderRadius: pw.BorderRadius.circular(12),
              ),
              child: pw.Row(
                mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
                crossAxisAlignment: pw.CrossAxisAlignment.center,
                children: [
                  pw.Column(
                    crossAxisAlignment: pw.CrossAxisAlignment.start,
                    children: [
                      pw.Row(
                        children: [
                          pw.Container(
                            width: 28,
                            height: 28,
                            decoration: pw.BoxDecoration(
                              color: pinkColor,
                              borderRadius: pw.BorderRadius.circular(6),
                            ),
                            child: pw.Center(
                              child: pw.Text('p', style: pw.TextStyle(color: wineColor, fontSize: 18, fontWeight: pw.FontWeight.bold)),
                            ),
                          ),
                          pw.SizedBox(width: 8),
                          pw.Text('PayPink®', style: const pw.TextStyle(color: PdfColors.white, fontSize: 22, fontWeight: pw.FontWeight.bold)),
                        ],
                      ),
                      pw.SizedBox(height: 4),
                      pw.Text('Official Statement of Account', style: pw.TextStyle(color: pinkColor, fontSize: 11)),
                    ],
                  ),
                  pw.Column(
                    crossAxisAlignment: pw.CrossAxisAlignment.end,
                    children: [
                      pw.Text('STATEMENT PERIOD', style: pw.TextStyle(color: pinkColor, fontSize: 8, fontWeight: pw.FontWeight.bold)),
                      pw.Text(dateRangeStr, style: const pw.TextStyle(color: PdfColors.white, fontSize: 9, fontWeight: pw.FontWeight.bold)),
                      pw.SizedBox(height: 4),
                      pw.Text('ACCOUNT NUMBER: $accountNumber', style: const pw.TextStyle(color: PdfColors.white, fontSize: 9)),
                    ],
                  ),
                ],
              ),
            ),

            pw.SizedBox(height: 20),

            // Customer & Account Snapshot
            pw.Row(
              crossAxisAlignment: pw.CrossAxisAlignment.start,
              children: [
                pw.Expanded(
                  flex: 3,
                  child: pw.Container(
                    padding: const pw.EdgeInsets.all(14),
                    decoration: pw.BoxDecoration(
                      color: lightGreyBg,
                      borderRadius: pw.BorderRadius.circular(10),
                      border: pw.Border.all(color: PdfColor.fromHex('#E8DFE3')),
                    ),
                    child: pw.Column(
                      crossAxisAlignment: pw.CrossAxisAlignment.start,
                      children: [
                        pw.Text('ACCOUNT HOLDER', style: pw.TextStyle(fontSize: 8, color: greyText, fontWeight: pw.FontWeight.bold)),
                        pw.SizedBox(height: 2),
                        pw.Text(customerName, style: pw.TextStyle(fontSize: 13, fontWeight: pw.FontWeight.bold, color: darkBg)),
                        pw.SizedBox(height: 6),
                        pw.Text('Account Type: $accountType', style: pw.TextStyle(fontSize: 10, color: darkBg)),
                        pw.Text('Currency: Philippine Peso (PHP)', style: pw.TextStyle(fontSize: 10, color: darkBg)),
                        pw.Text('Issuing Branch: Digital Head Office, BGC Manila', style: pw.TextStyle(fontSize: 9, color: greyText)),
                      ],
                    ),
                  ),
                ),
                pw.SizedBox(width: 14),
                pw.Expanded(
                  flex: 2,
                  child: pw.Container(
                    padding: const pw.EdgeInsets.all(14),
                    decoration: pw.BoxDecoration(
                      color: lightGreyBg,
                      borderRadius: pw.BorderRadius.circular(10),
                      border: pw.Border.all(color: PdfColor.fromHex('#E8DFE3')),
                    ),
                    child: pw.Column(
                      crossAxisAlignment: pw.CrossAxisAlignment.start,
                      children: [
                        pw.Text('CURRENT BALANCE', style: pw.TextStyle(fontSize: 8, color: greyText, fontWeight: pw.FontWeight.bold)),
                        pw.SizedBox(height: 2),
                        pw.Text('PHP ${currentBalance.toStringAsFixed(2)}', style: pw.TextStyle(fontSize: 15, fontWeight: pw.FontWeight.bold, color: wineColor)),
                        pw.SizedBox(height: 6),
                        pw.Text('Total Inflow: +PHP ${totalInflow.toStringAsFixed(2)}', style: pw.TextStyle(fontSize: 9, color: PdfColor.fromHex('#15803D'))),
                        pw.Text('Total Outflow: -PHP ${totalOutflow.toStringAsFixed(2)}', style: pw.TextStyle(fontSize: 9, color: wineColor)),
                      ],
                    ),
                  ),
                ),
              ],
            ),

            pw.SizedBox(height: 20),

            pw.Text('TRANSACTION SUMMARY', style: pw.TextStyle(fontSize: 11, fontWeight: pw.FontWeight.bold, color: darkBg)),
            pw.SizedBox(height: 8),

            // Table of Transactions
            pw.Table(
              border: pw.TableBorder(
                horizontalInside: pw.BorderSide(color: PdfColor.fromHex('#E8DFE3'), width: 0.5),
                bottom: pw.BorderSide(color: wineColor, width: 1),
              ),
              children: [
                pw.TableRow(
                  decoration: pw.BoxDecoration(color: wineColor),
                  children: [
                    pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text('Date', style: const pw.TextStyle(color: PdfColors.white, fontSize: 8.5, fontWeight: pw.FontWeight.bold))),
                    pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text('Reference ID', style: const pw.TextStyle(color: PdfColors.white, fontSize: 8.5, fontWeight: pw.FontWeight.bold))),
                    pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text('Description', style: const pw.TextStyle(color: PdfColors.white, fontSize: 8.5, fontWeight: pw.FontWeight.bold))),
                    pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text('Status', style: const pw.TextStyle(color: PdfColors.white, fontSize: 8.5, fontWeight: pw.FontWeight.bold))),
                    pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text('Amount (PHP)', textAlign: pw.TextAlign.right, style: const pw.TextStyle(color: PdfColors.white, fontSize: 8.5, fontWeight: pw.FontWeight.bold))),
                  ],
                ),
                ...transactions.take(25).map((t) {
                  final isCr = t.isCredit;
                  return pw.TableRow(
                    children: [
                      pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text(t.date, style: const pw.TextStyle(fontSize: 8))),
                      pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text(t.id, style: const pw.TextStyle(fontSize: 7.5, color: PdfColors.grey700))),
                      pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text(t.title, style: const pw.TextStyle(fontSize: 8, fontWeight: pw.FontWeight.bold))),
                      pw.Padding(padding: const pw.EdgeInsets.all(6), child: pw.Text(t.status, style: const pw.TextStyle(fontSize: 7.5))),
                      pw.Padding(
                        padding: const pw.EdgeInsets.all(6),
                        child: pw.Text(
                          '${isCr ? '+' : '-'} ${t.amount.toStringAsFixed(2)}',
                          textAlign: pw.TextAlign.right,
                          style: pw.TextStyle(fontSize: 8, fontWeight: pw.FontWeight.bold, color: isCr ? PdfColor.fromHex('#15803D') : darkBg),
                        ),
                      ),
                    ],
                  );
                }),
              ],
            ),

            pw.SizedBox(height: 25),

            // Footer Regulatory Notice
            pw.Divider(color: PdfColor.fromHex('#E8DFE3')),
            pw.SizedBox(height: 6),
            pw.Row(
              mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
              children: [
                pw.Text(
                  'PayPink Digital Bank (Philippines) Inc. · Supervised by Bangko Sentral ng Pilipinas (BSP) · Member: PDIC',
                  style: pw.TextStyle(fontSize: 7, color: greyText),
                ),
                pw.Text(
                  'Verified Digital Copy',
                  style: pw.TextStyle(fontSize: 7, fontWeight: pw.FontWeight.bold, color: wineColor),
                ),
              ],
            ),
          ];
        },
      ),
    );

    await Printing.layoutPdf(
      onLayout: (PdfPageFormat format) async => pdf.save(),
      name: 'PayPink_Statement_${accountNumber.replaceAll(' ', '')}_$statementMonth.pdf',
    );
  }
}
