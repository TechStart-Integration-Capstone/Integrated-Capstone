import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'api_client.dart';
import 'api_config.dart';
import 'secure_token_storage.dart';

class ReportException implements Exception {
  final String message;
  const ReportException(this.message);
  @override
  String toString() => message;
}

/// Server-generated transaction report, the same PDF the web app downloads
/// (frontend/bank/reports.js): GET /api/v1/auth/banking/reports/transactions.pdf.
/// It includes every transaction in the period, including pending and failed entries.
class ReportService {
  static String isoDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  /// Today in Philippine time (UTC+8), which the server uses for the date range.
  static DateTime manilaToday() {
    final now = DateTime.now().toUtc().add(const Duration(hours: 8));
    return DateTime(now.year, now.month, now.day);
  }

  /// Returns null when the range is valid, otherwise a message to show the user.
  static String? validateRange(DateTime from, DateTime to) {
    final days = to.difference(from).inDays;
    if (days < 0 || days > 365) {
      return 'Choose a From date on or before the To date, with no more than 366 days.';
    }
    return null;
  }

  static Future<Uint8List> downloadTransactionsPdf({
    required int accountId,
    required DateTime from,
    required DateTime to,
  }) async {
    final rangeError = validateRange(from, to);
    if (rangeError != null) throw ReportException(rangeError);

    final token = await SecureTokenStorage.getToken() ?? '';
    final uri = Uri.parse('${ApiConfig.baseUrl}/auth/banking/reports/transactions.pdf').replace(
      queryParameters: {'accountId': '$accountId', 'from': isoDate(from), 'to': isoDate(to)},
    );
    final http.Response response;
    try {
      response = await http
          .get(uri, headers: {'Authorization': 'Bearer $token', 'Accept': 'application/pdf'})
          .timeout(const Duration(seconds: 60));
    } on Exception {
      throw const ReportException('The report is taking too long. Try a shorter date range.');
    }

    if (response.statusCode == 401) {
      ApiClient.triggerUnauthorized();
      throw const ReportException('Your session has ended. Please log in again.');
    }
    if (response.statusCode != 200) {
      String message = 'The report could not be generated. Please try again.';
      try {
        final data = jsonDecode(response.body);
        if (data is Map && (data['message'] ?? data['detail']) != null) {
          message = (data['message'] ?? data['detail']).toString();
        }
      } catch (_) {}
      throw ReportException(message);
    }
    if (!(response.headers['content-type'] ?? '').contains('application/pdf')) {
      throw const ReportException('The report could not be downloaded. Please try again.');
    }
    return response.bodyBytes;
  }
}
