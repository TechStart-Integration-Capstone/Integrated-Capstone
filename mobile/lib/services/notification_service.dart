import '../screens/transactions_screen.dart';
import 'account_service.dart';
import 'secure_token_storage.dart';

/// In-app notifications derived from the customer's server-side transaction history,
/// using the same rules as the web app (frontend/bank/notifications.js), which mirror
/// notification-service's debit/credit alerts. Nothing here is invented on the device.
class NotificationService {
  static const _types = {'TRANSFER_IN', 'TRANSFER_OUT', 'LOAN_DISBURSEMENT', 'LOAN_REPAYMENT'};

  /// Builds the inbox (newest first) from server transactions.
  /// Read and dismissed IDs are applied so the UI can show unread state.
  static List<Map<String, dynamic>> fromTransactions(
    List<TransactionItem> transactions, {
    Set<String> read = const {},
    Set<String> hidden = const {},
  }) {
    final items = <Map<String, dynamic>>[];
    for (final tx in transactions) {
      final type = tx.transactionType.toUpperCase();
      if (!_types.contains(type) && !type.startsWith('EXT_')) continue;
      final note = _describe(tx, type);
      final id = '${tx.id}:${tx.status.toUpperCase()}';
      if (hidden.contains(id)) continue;
      items.add({
        'id': id,
        'title': note.$1,
        'message': note.$2,
        'time': tx.date,
        'unread': !read.contains(id),
      });
    }
    return items;
  }

  static (String, String) _describe(TransactionItem tx, String type) {
    final amount = formatPeso(tx.amount);
    final account = tx.account;
    if (type == 'LOAN_DISBURSEMENT') {
      return ('Loan approved and received', 'Your loan was approved. $amount was credited to your account $account.');
    }
    if (type == 'LOAN_REPAYMENT') {
      return ('Loan payment received', '$amount was paid toward your loan from your account $account.');
    }
    final status = tx.status.toUpperCase();
    final person = (tx.counterparty?.trim().isNotEmpty ?? false) ? tx.counterparty!.trim() : 'another account';
    if (status == 'PENDING' || status == 'PROCESSING') {
      return ('Transfer pending', '$amount to $person is waiting to be processed. Funds are on hold.');
    }
    if (status == 'CANCELLED') {
      return ('Transfer cancelled', 'Your $amount transfer to $person was cancelled. Held funds were restored to your balance.');
    }
    if (status == 'FAILED' || status == 'REVERSED') {
      return ('Transfer reversed', 'Your $amount transfer to $person could not be completed and was reversed. No funds were lost.');
    }
    if (type == 'TRANSFER_IN') {
      return ('Money received', '$amount from $person was credited to your account $account.');
    }
    return ('Money sent', '$amount was sent to $person from your account $account.');
  }

  static String _key(String username, String kind) => 'paypink_notifications_${kind}_${username.toLowerCase()}';

  static Future<Set<String>> loadIds(String username, String kind) async {
    final raw = await SecureTokenStorage.readValue(_key(username, kind));
    if (raw == null || raw.isEmpty) return <String>{};
    return raw.split('\n').where((s) => s.isNotEmpty).toSet();
  }

  /// Persists only IDs still present in the inbox, so storage stays bounded.
  static Future<void> saveIds(String username, String kind, Set<String> ids, Iterable<String> current) async {
    final keep = ids.where(current.toSet().contains).join('\n');
    await SecureTokenStorage.writeValue(_key(username, kind), keep);
  }
}
