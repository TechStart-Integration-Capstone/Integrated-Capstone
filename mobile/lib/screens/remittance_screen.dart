import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import '../theme/paypink_theme.dart';
import '../widgets/glass_card.dart';
import '../widgets/pin_auth_sheet.dart';
import '../widgets/paypink_logo.dart';
import '../services/api_config.dart';
import '../services/secure_token_storage.dart';
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
  int _selectedModeIndex = 0; // 0: My Accounts, 1: Another PayPink, 2: Outside
  String _sourceAccount = '';
  String _ownTargetAccount = '';

  final TextEditingController _amountController = TextEditingController(text: '');
  final TextEditingController _recipientController = TextEditingController();

  String? _verifiedName;
  String? _recipientError;
  bool _isLookingUpRecipient = false;
  bool _isRecipientValid = false;
  Timer? _lookupDebounce;

  String _transferRail = 'InstaPay';
  // Partner-bank directory (server) and the entry matching the typed account number.
  List<ExternalRecipient> _externalRecipients = const [];
  ExternalRecipient? _externalMatch;

  List<Map<String, String>> _favorites = [];
  bool _isLoadingFavorites = false;

  // Saga state variables
  bool _isSagaPending = false;
  String? _pendingReferenceNo;
  String? _sagaErrorMessage;
  Timer? _statusPollTimer;

  @override
  @override
  void initState() {
    super.initState();
    _initDefaultAccounts();
    _updateCalculations();
    _loadFavorites();
    _loadExternalRecipients();
  }

  Future<void> _loadExternalRecipients() async {
    final list = await RemittanceService.fetchExternalRecipients();
    if (!mounted) return;
    setState(() {
      _externalRecipients = list;
      _updateCalculations();
    });
  }

  @override
  void dispose() {
    _statusPollTimer?.cancel();
    _lookupDebounce?.cancel();
    _amountController.dispose();
    _recipientController.dispose();
    super.dispose();
  }

  String _getAvatar(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.length >= 2 && parts[0].isNotEmpty && parts[1].isNotEmpty) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    } else if (parts.isNotEmpty && parts[0].isNotEmpty) {
      final len = parts[0].length >= 2 ? 2 : parts[0].length;
      return parts[0].substring(0, len).toUpperCase();
    }
    return 'PP';
  }

  Future<void> _loadFavorites() async {
    if (!mounted) return;
    setState(() => _isLoadingFavorites = true);
    try {
      final token = await SecureTokenStorage.getToken();
      final headers = {
        'Accept': 'application/json',
        if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
      };

      // Query GET /api/v1/accounts/favorites
      http.Response response = await http.get(
        Uri.parse('${ApiConfig.baseUrl}/accounts/favorites'),
        headers: headers,
      ).timeout(const Duration(seconds: 5));

      // Fallback to /api/v1/accounts/recipients if /favorites returns 404/405
      if (response.statusCode == 404 || response.statusCode == 405) {
        response = await http.get(
          Uri.parse('${ApiConfig.baseUrl}/accounts/recipients'),
          headers: headers,
        ).timeout(const Duration(seconds: 5));
      }

      if (response.statusCode == 200) {
        final dynamic decoded = jsonDecode(response.body);
        List<dynamic> items = [];
        if (decoded is List) {
          items = decoded;
        } else if (decoded is Map && decoded['favorites'] is List) {
          items = decoded['favorites'] as List;
        }

        final List<Map<String, String>> loaded = [];
        for (final item in items) {
          if (item is Map) {
            final name = item['fullName']?.toString() ?? item['name']?.toString() ?? 'Favorite';
            final num = item['accountNumber']?.toString() ?? item['number']?.toString() ?? '';
            if (num.isNotEmpty) {
              loaded.add({
                'name': name,
                'number': num,
                'avatar': _getAvatar(name),
                'bank': 'PayPink',
              });
            }
          }
        }

        if (mounted) {
          setState(() {
            _favorites = loaded;
            _isLoadingFavorites = false;
          });
        }
        return;
      }
    } catch (e) {
      debugPrint('[RemittanceScreen] Error loading favorites: $e');
    }

    if (mounted) {
      setState(() => _isLoadingFavorites = false);
    }
  }

  Future<Map<String, dynamic>?> _lookupPayPinkAccount(String rawNumber) async {
    final clean = rawNumber.replaceAll(RegExp(r'[\s-]'), '');
    if (!RegExp(r'^\d{12}$').hasMatch(clean)) {
      return null;
    }

    try {
      final token = await SecureTokenStorage.getToken();
      final headers = {
        'Accept': 'application/json',
        if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
      };

      final response = await http.get(
        Uri.parse('${ApiConfig.baseUrl}/accounts/recipients/lookup?accountNumber=$clean'),
        headers: headers,
      ).timeout(const Duration(seconds: 5));

      if (response.statusCode == 200) {
        final decoded = jsonDecode(response.body);
        if (decoded is Map<String, dynamic>) {
          return decoded;
        }
      }
    } catch (e) {
      debugPrint('[RemittanceScreen] Recipient lookup error: $e');
    }
    return null;
  }

  void _debounceServerLookup(String cleanNumber) {
    _lookupDebounce?.cancel();
    _lookupDebounce = Timer(const Duration(milliseconds: 350), () {
      _performServerLookup(cleanNumber);
    });
  }

  Future<bool> _performServerLookup(String cleanNumber) async {
    if (!mounted) return false;
    setState(() => _isLookingUpRecipient = true);

    final recipient = await _lookupPayPinkAccount(cleanNumber);
    if (!mounted) return false;

    setState(() {
      _isLookingUpRecipient = false;
      if (recipient != null) {
        _verifiedName = recipient['fullName']?.toString() ?? 'Verified PayPink Recipient';
        _recipientError = null;
        _isRecipientValid = true;
      } else {
        _verifiedName = null;
        _recipientError = 'PayPink account not found. Please verify the account number.';
        _isRecipientValid = false;
      }
    });

    return recipient != null;
  }

  Future<bool> _saveFavorite(String rawNumber) async {
    final clean = rawNumber.replaceAll(RegExp(r'[\s-]'), '');
    // 1. Client-Side Format Validation: exactly 12 numeric digits
    if (!RegExp(r'^\d{12}$').hasMatch(clean)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Invalid account number. PayPink account numbers must be 12 digits.'),
        ),
      );
      return false;
    }

    // 2. Server-side lookup verification
    final recipientData = await _lookupPayPinkAccount(clean);
    if (!mounted) return false;
    if (recipientData == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('PayPink account not found. Please verify the account number.'),
        ),
      );
      return false;
    }

    final fullName = recipientData['fullName']?.toString() ?? 'PayPink Recipient';

    // 3. POST /api/v1/accounts/favorites
    try {
      final token = await SecureTokenStorage.getToken();
      final headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
      };

      final response = await http.post(
        Uri.parse('${ApiConfig.baseUrl}/accounts/favorites'),
        headers: headers,
        body: jsonEncode({'accountNumber': clean}),
      ).timeout(const Duration(seconds: 5));

      if (response.statusCode == 200 || response.statusCode == 201) {
        setState(() {
          _favorites.removeWhere((f) => f['number'] == clean);
          _favorites.insert(0, {
            'name': fullName,
            'number': clean,
            'avatar': _getAvatar(fullName),
            'bank': 'PayPink',
          });
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              backgroundColor: PayPinkTheme.green,
              content: Text('Saved $fullName to Favorites'),
            ),
          );
        }
        return true;
      }
    } catch (e) {
      debugPrint('[RemittanceScreen] Error saving favorite: $e');
    }

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Failed to save favorite. Please try again.'),
        ),
      );
    }
    return false;
  }

  Future<void> _deleteFavorite(String rawNumber, int index) async {
    final clean = rawNumber.replaceAll(RegExp(r'[\s-]'), '');
    try {
      final token = await SecureTokenStorage.getToken();
      final headers = {
        if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
      };

      final response = await http.delete(
        Uri.parse('${ApiConfig.baseUrl}/accounts/favorites/$clean'),
        headers: headers,
      ).timeout(const Duration(seconds: 5));

      if (response.statusCode == 200 || response.statusCode == 204) {
        setState(() {
          if (index < _favorites.length && _favorites[index]['number'] == clean) {
            _favorites.removeAt(index);
          } else {
            _favorites.removeWhere((f) => f['number'] == clean);
          }
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              backgroundColor: PayPinkTheme.wine,
              content: Text('Favorite removed'),
            ),
          );
        }
        return;
      }
    } catch (e) {
      debugPrint('[RemittanceScreen] Error removing favorite: $e');
    }

    setState(() {
      if (index < _favorites.length) {
        _favorites.removeAt(index);
      }
    });
  }

  void _showFavoritesManagerModal(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final sheetBg = isDark ? PayPinkTheme.darkPaper : Colors.white;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setSheetState) {
          return Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(context).size.height * 0.85,
            ),
            padding: const EdgeInsets.fromLTRB(22, 12, 22, 28),
            decoration: BoxDecoration(
              color: sheetBg,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x33000000),
                  blurRadius: 24,
                  offset: Offset(0, -4),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 38,
                    height: 4,
                    decoration: BoxDecoration(
                      color: isDark ? textLine : Colors.grey.shade300,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Saved Favorites',
                      style: PayPinkTheme.display(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        color: textInk,
                      ),
                    ),
                    IconButton(
                      icon: Icon(Icons.close, size: 20, color: textMuted),
                      onPressed: () => Navigator.pop(ctx),
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  'Manage your saved PayPink transfer favorites for 1-tap remittances.',
                  style: PayPinkTheme.body(fontSize: 12, color: textMuted),
                ),
                const SizedBox(height: 14),

                // "+ Add New Favorite" Button
                SizedBox(
                  width: double.infinity,
                  height: 44,
                  child: ElevatedButton.icon(
                    onPressed: () {
                      _showAddFavoriteModal(context, onAdded: () {
                        setSheetState(() {});
                      });
                    },
                    icon: const Icon(Icons.person_add_rounded, size: 17),
                    label: const Text('Add New Favorite'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: PayPinkTheme.wine,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      textStyle: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Favorites List
                Expanded(
                  child: _favorites.isEmpty
                      ? Center(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 24),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.star_outline_rounded, size: 40, color: textMuted.withValues(alpha: 0.5)),
                                const SizedBox(height: 8),
                                Text(
                                  'No saved favorites yet',
                                  style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700, color: textInk),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  'Tap "+ Add New Favorite" above to save frequent payees.',
                                  style: PayPinkTheme.body(fontSize: 11, color: textMuted),
                                ),
                              ],
                            ),
                          ),
                        )
                      : ListView.separated(
                          shrinkWrap: true,
                          itemCount: _favorites.length,
                          separatorBuilder: (_, __) => const SizedBox(height: 8),
                          itemBuilder: (context, idx) {
                            final fav = _favorites[idx];
                            final name = fav['name'] ?? 'Recipient';
                            final number = fav['number'] ?? '';
                            final avatar = fav['avatar'] ?? _getAvatar(name);
                            final bank = fav['bank'] ?? 'PayPink';

                            return InkWell(
                              onTap: () {
                                _recipientController.text = number;
                                _updateCalculations();
                                Navigator.pop(ctx);
                              },
                              borderRadius: BorderRadius.circular(14),
                              child: Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: isDark ? PayPinkTheme.darkCard : PayPinkTheme.pinkSubtle.withValues(alpha: 0.4),
                                  borderRadius: BorderRadius.circular(14),
                                  border: Border.all(color: textLine),
                                ),
                                child: Row(
                                  children: [
                                    Container(
                                      width: 40,
                                      height: 40,
                                      decoration: BoxDecoration(
                                        color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.3) : PayPinkTheme.pinkSubtle,
                                        shape: BoxShape.circle,
                                        border: Border.all(color: isDark ? PayPinkTheme.wine : PayPinkTheme.pink),
                                      ),
                                      child: Center(
                                        child: Text(
                                          avatar,
                                          style: PayPinkTheme.display(
                                            fontSize: 13,
                                            fontWeight: FontWeight.w800,
                                            color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
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
                                            name,
                                            style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700, color: textInk),
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                          const SizedBox(height: 2),
                                          Text(
                                            '$bank · $number',
                                            style: PayPinkTheme.mono(fontSize: 11, color: textMuted),
                                          ),
                                        ],
                                      ),
                                    ),
                                    IconButton(
                                      icon: const Icon(Icons.delete_outline_rounded, size: 19, color: PayPinkTheme.red),
                                      tooltip: 'Remove Favorite',
                                      onPressed: () async {
                                        await _deleteFavorite(number, idx);
                                        setSheetState(() {});
                                      },
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  void _showAddFavoriteModal(BuildContext context, {VoidCallback? onAdded}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final dialogBg = isDark ? PayPinkTheme.darkPaper : Colors.white;
    final cardBg = isDark ? PayPinkTheme.darkCard : PayPinkTheme.paper;

    final nameController = TextEditingController();
    final numberController = TextEditingController();
    String selectedBank = 'PayPink';
    String? modalError;
    bool isSaving = false;

    showDialog(
      context: context,
      barrierDismissible: !isSaving,
      builder: (dialogCtx) => StatefulBuilder(
        builder: (context, setModalState) {
          return Dialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(PayPinkTheme.radiusXl)),
            backgroundColor: dialogBg,
            elevation: 20,
            insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(22, 22, 22, 20),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Header with Icon
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Container(
                              width: 32,
                              height: 32,
                              decoration: BoxDecoration(
                                color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.3) : PayPinkTheme.pinkSubtle,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.star_rounded, size: 18, color: PayPinkTheme.wine),
                            ),
                            const SizedBox(width: 10),
                            Text(
                              'Add New Favorite',
                              style: PayPinkTheme.display(
                                fontSize: 17,
                                fontWeight: FontWeight.w700,
                                color: textInk,
                              ),
                            ),
                          ],
                        ),
                        IconButton(
                          icon: Icon(Icons.close, size: 20, color: textMuted),
                          onPressed: isSaving ? null : () => Navigator.pop(dialogCtx),
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Save an account for instant 1-tap remittances.',
                      style: PayPinkTheme.body(fontSize: 11, color: textMuted),
                    ),
                    const SizedBox(height: 16),

                    // Prominent Inline Error Banner inside window
                    if (modalError != null) ...[
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        decoration: BoxDecoration(
                          color: PayPinkTheme.red.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: PayPinkTheme.red.withValues(alpha: 0.45)),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(Icons.error_outline_rounded, color: PayPinkTheme.red, size: 18),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                modalError!,
                                style: PayPinkTheme.body(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                  color: PayPinkTheme.red,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 14),
                    ],

                    // Field 1: Recipient Full Name
                    Text('Recipient Full Name', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: textInk)),
                    const SizedBox(height: 6),
                    TextField(
                      controller: nameController,
                      style: PayPinkTheme.body(fontSize: 13, color: textInk),
                      decoration: InputDecoration(
                        hintText: 'e.g. Carlos Mendoza (optional)',
                        hintStyle: PayPinkTheme.body(fontSize: 13, color: textMuted),
                        filled: true,
                        fillColor: cardBg,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: textLine)),
                        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: textLine)),
                        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: PayPinkTheme.wine)),
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Field 2: 12-Digit PayPink Account Number
                    Text('12-Digit PayPink Account Number', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: textInk)),
                    const SizedBox(height: 6),
                    TextField(
                      controller: numberController,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      onChanged: (_) {
                        if (modalError != null) {
                          setModalState(() => modalError = null);
                        }
                      },
                      style: PayPinkTheme.body(fontSize: 13, color: textInk),
                      decoration: InputDecoration(
                        hintText: 'e.g. 001381233467',
                        hintStyle: PayPinkTheme.body(fontSize: 13, color: textMuted),
                        filled: true,
                        fillColor: cardBg,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: textLine)),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: modalError != null ? PayPinkTheme.red : textLine),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: modalError != null ? PayPinkTheme.red : PayPinkTheme.wine),
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Field 3: Destination Bank
                    Text('Destination Bank', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: textInk)),
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
                          value: selectedBank,
                          isExpanded: true,
                          dropdownColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
                          style: PayPinkTheme.body(fontSize: 13, color: textInk),
                          items: const [
                            DropdownMenuItem(value: 'PayPink', child: Text('PayPink Digital Bank')),
                            DropdownMenuItem(value: 'BDO', child: Text('BDO Unibank')),
                            DropdownMenuItem(value: 'BPI', child: Text('Bank of the Philippine Islands (BPI)')),
                            DropdownMenuItem(value: 'UnionBank', child: Text('UnionBank of the Philippines')),
                            DropdownMenuItem(value: 'GCash', child: Text('GCash')),
                            DropdownMenuItem(value: 'Maya', child: Text('Maya')),
                          ],
                          onChanged: (val) {
                            if (val != null) setModalState(() => selectedBank = val);
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 22),

                    // Action Buttons (Cancel / Save Favorite)
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: isSaving ? null : () => Navigator.pop(dialogCtx),
                            style: OutlinedButton.styleFrom(
                              side: BorderSide(color: textLine),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                              padding: const EdgeInsets.symmetric(vertical: 12),
                            ),
                            child: Text(
                              'Cancel',
                              style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w600, color: textInk),
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: ElevatedButton(
                            onPressed: isSaving
                                ? null
                                : () async {
                                    final navigator = Navigator.of(dialogCtx);
                                    final messenger = ScaffoldMessenger.of(context);
                                    final rawNumber = numberController.text.trim();
                                    final clean = rawNumber.replaceAll(RegExp(r'[\s-]'), '');

                                    // 1. Client-Side Format Validation
                                    if (!RegExp(r'^\d{12}$').hasMatch(clean)) {
                                      setModalState(() {
                                        modalError = 'Invalid account number. PayPink account numbers must be 12 digits.';
                                      });
                                      return;
                                    }

                                    setModalState(() {
                                      isSaving = true;
                                      modalError = null;
                                    });

                                    // 2. Server-Side Lookup Verification
                                    final recipientData = await _lookupPayPinkAccount(clean);
                                    if (recipientData == null) {
                                      setModalState(() {
                                        isSaving = false;
                                        modalError = 'PayPink account not found. Please verify the account number.';
                                      });
                                      return;
                                    }

                                    final enteredName = nameController.text.trim();
                                    final resolvedName = enteredName.isNotEmpty
                                        ? enteredName
                                        : (recipientData['fullName']?.toString() ?? 'PayPink Recipient');

                                    // 3. POST /api/v1/accounts/favorites
                                    try {
                                      final token = await SecureTokenStorage.getToken();
                                      final headers = {
                                        'Content-Type': 'application/json',
                                        'Accept': 'application/json',
                                        if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
                                      };

                                      final response = await http.post(
                                        Uri.parse('${ApiConfig.baseUrl}/accounts/favorites'),
                                        headers: headers,
                                        body: jsonEncode({'accountNumber': clean}),
                                      ).timeout(const Duration(seconds: 5));

                                      if (response.statusCode == 200 || response.statusCode == 201) {
                                        if (mounted) {
                                          setState(() {
                                            _favorites.removeWhere((f) => f['number'] == clean);
                                            _favorites.insert(0, {
                                              'name': resolvedName,
                                              'number': clean,
                                              'avatar': _getAvatar(resolvedName),
                                              'bank': selectedBank,
                                            });
                                          });
                                        }
                                        onAdded?.call();
                                        navigator.pop();
                                        messenger.showSnackBar(
                                          SnackBar(
                                            backgroundColor: PayPinkTheme.green,
                                            content: Text('Saved $resolvedName to Favorites'),
                                          ),
                                        );
                                        return;
                                      }
                                    } catch (e) {
                                      debugPrint('[RemittanceScreen] Modal save error: $e');
                                    }

                                    setModalState(() {
                                      isSaving = false;
                                      modalError = 'Failed to save favorite. Please try again.';
                                    });
                                  },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: PayPinkTheme.wine,
                              foregroundColor: Colors.white,
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                              padding: const EdgeInsets.symmetric(vertical: 12),
                            ),
                            child: isSaving
                                ? const SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                  )
                                : Text(
                                    'Save Favorite',
                                    style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w700, color: Colors.white),
                                  ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
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
      if (_selectedModeIndex == 1) {
        final cleanRec = _recipientController.text.replaceAll(RegExp(r'[\s-]'), '');
        if (cleanRec.isEmpty) {
          _recipientError = null;
          _verifiedName = null;
          _isRecipientValid = false;
        } else if (!RegExp(r'^\d{12}$').hasMatch(cleanRec)) {
          _recipientError = 'Invalid account number. PayPink account numbers must be 12 digits.';
          _verifiedName = null;
          _isRecipientValid = false;
        } else {
          _recipientError = null;
          _debounceServerLookup(cleanRec);
        }
      } else {
        _recipientError = null;
      }
      if (_selectedModeIndex == 2) {
        final number = _recipientController.text.replaceAll(RegExp(r'\s'), '');
        _externalMatch = _externalRecipients.where((r) => r.number == number).firstOrNull;
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
                                style: PayPinkTheme.eyebrow(fontSize: 10, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                fromName,
                                style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700, color: textInk),
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
                        style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w800, color: textInk),
                      ),
                      Text('Available', style: PayPinkTheme.body(fontSize: 10, color: textMuted)),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Account Swap Arrow Button
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
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: isDark ? PayPinkTheme.wine : PayPinkTheme.pinkSubtle,
                      shape: BoxShape.circle,
                      border: Border.all(color: textLine),
                      boxShadow: [
                        BoxShadow(
                          color: PayPinkTheme.wine.withValues(alpha: 0.15),
                          blurRadius: 6,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Icon(
                      Icons.swap_vert_rounded,
                      color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                      size: 18,
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
                                style: PayPinkTheme.eyebrow(fontSize: 10, fontWeight: FontWeight.w700, color: PayPinkTheme.green),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                toName,
                                style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700, color: textInk),
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
                        style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w800, color: textInk),
                      ),
                      Text('Balance', style: PayPinkTheme.body(fontSize: 10, color: textMuted)),
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
    if (index >= _favorites.length) return;
    final fav = _favorites[index];
    final num = fav['number'] ?? '';
    _recipientController.text = num;
    _verifiedName = fav['name'];
    _recipientError = null;
    _isRecipientValid = true;
    _updateCalculations();
  }

  void _addQuickAmount(double delta) {
    final current = double.tryParse(_amountController.text) ?? 0.0;
    _amountController.text = (current + delta).toStringAsFixed(2);
    _updateCalculations();
  }

  void _handleReviewTransfer() async {
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

    // Strict validation for PayPink P2P transfers
    if (_selectedModeIndex == 1) {
      final cleanRec = _recipientController.text.replaceAll(RegExp(r'[\s-]'), '');
      if (!RegExp(r'^\d{12}$').hasMatch(cleanRec)) {
        setState(() {
          _recipientError = 'Invalid account number. PayPink account numbers must be 12 digits.';
          _isRecipientValid = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: PayPinkTheme.red,
            content: Text('Invalid account number. PayPink account numbers must be 12 digits.'),
          ),
        );
        return;
      }

      if (!_isRecipientValid || _verifiedName == null) {
        final verified = await _performServerLookup(cleanRec);
        if (!verified) {
          setState(() {
            _recipientError = 'PayPink account not found. Please verify the account number.';
            _isRecipientValid = false;
          });
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                backgroundColor: PayPinkTheme.red,
                content: Text('PayPink account not found. Please verify the account number.'),
              ),
            );
          }
          return;
        }
      }
    }
    // Bank transfers: the recipient must be in the partner directory, and InstaPay caps at ₱50,000.
    if (_selectedModeIndex == 2) {
      String? error;
      if (_externalMatch == null) {
        error = 'Account not found. Check the account number.';
      } else if (_transferRail == 'InstaPay' && amt > 50000) {
        error = 'InstaPay allows up to ₱50,000 per transfer. Choose PESONet for a larger amount.';
      } else if (_getAccount(_sourceAccount) == null) {
        error = 'Choose one of your accounts to send from.';
      }
      if (error != null) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(backgroundColor: PayPinkTheme.red, content: Text(error)),
        );
        return;
      }
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
      final match = _externalMatch;
      toAccountName = match != null
          ? '${match.name} · ${match.bank} (•••• $recLast4)'
          : 'Bank account (•••• $recLast4)';
    }

    if (mounted) {
      _showConfirmationBottomSheet(amt, fromAccountName, toAccountName);
    }
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
                onPressed: () async {
                  Navigator.pop(ctx);
                  final pinVerified = await PinAuthSheet.show(
                    context,
                    title: 'Authorize Remittance',
                    description: 'Confirm transfer of ₱${amt.toStringAsFixed(2)} to $toAcc',
                    amount: amt,
                  );
                  if (pinVerified) {
                    _executeTransfer(amt, fromAcc, toAcc);
                  }
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

  /// Submits the transfer. The caller has already verified the MPIN once on the
  /// confirmation sheet, so this must not prompt again.
  void _executeTransfer(double amt, String fromAcc, String toAcc) async {
    final cleanDest = _selectedModeIndex == 0
        ? _ownTargetAccount
        : _recipientController.text.replaceAll(' ', '');

    // Bank transfers go over InstaPay/PESONet, exactly like the web app.
    final source = _getAccount(_sourceAccount);
    final result = _selectedModeIndex == 2 && source != null
        ? await RemittanceService.submitExternalTransfer(
            sourceAccountId: source.accountId,
            destinationAccountNumber: cleanDest,
            amount: amt,
            rail: _transferRail,
          )
        : await RemittanceService.submitRemittance(
            sourceAccountId: _sourceAccount,
            destinationAccountNumber: cleanDest,
            amount: amt,
          );

    if (!mounted) return;

    if (result.success && result.status == 'PENDING') {
      // PESONet settles in the next clearing batch; the money is already debited.
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(backgroundColor: PayPinkTheme.wineDark, content: Text(result.message)),
      );
      _showScreenshotReceiptDialog(amt, result.referenceId ?? '', fromAcc, toAcc);
    } else if (result.success && result.status == 'PROCESSING') {
      // 202: core banking is still posting. Poll until it settles; there is no cancel window.
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
          : 'TXN-${DateTime.now().year}-${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}';
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

  // ── 202 PROCESSING: follow the transfer until core banking settles it ──────

  void _startStatusPolling(String ref, double amt, String fromAcc, String toAcc) {
    _statusPollTimer?.cancel();
    _statusPollTimer = Timer.periodic(const Duration(seconds: 2), (timer) async {
      final status = await RemittanceService.getStatus(ref);
      if (!mounted) { timer.cancel(); return; }
      if (status == 'COMPLETED' || status == 'POSTED' || status == 'SUCCESS') {
        timer.cancel();
        setState(() {
          _isSagaPending = false;
          _pendingReferenceNo = null;
        });
        _showScreenshotReceiptDialog(amt, ref, fromAcc, toAcc);
      } else if (status == 'FAILED' || status == 'CANCELLED' || status == 'REVERSED' || status == 'REJECTED') {
        timer.cancel();
        setState(() {
          _isSagaPending = false;
          _pendingReferenceNo = null;
          _sagaErrorMessage = null;
        });
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          backgroundColor: PayPinkTheme.red,
          content: Text('Transfer failed and was reversed. The held amount is back in your account.'),
        ));
      }
    });
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
        : (_selectedModeIndex == 1 ? 'PayPink P2P Transfer' : '$_transferRail Bank Transfer');

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(PayPinkTheme.radiusXl)),
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
                  // Brand Header
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      PayPinkLogo.markOnly(size: 26, isDark: isDark),
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
                    style: PayPinkTheme.eyebrow(fontSize: 10, fontWeight: FontWeight.w600, color: textMuted),
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
========================================
Thank you for banking with PayPink!
''';
                            Clipboard.setData(ClipboardData(text: receiptText));
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                backgroundColor: PayPinkTheme.wine,
                                content: Text('Receipt details copied to clipboard.'),
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
                            style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w700, color: Colors.white),
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
        Text(label, style: PayPinkTheme.body(fontSize: 12, color: textMuted)),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: isMono
                ? PayPinkTheme.mono(
                    fontSize: isSmall ? 10 : 12,
                    fontWeight: FontWeight.w600,
                    color: valColor ?? textInk,
                  )
                : PayPinkTheme.body(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: valColor ?? textInk,
                  ),
          ),
        ),
      ],
    );
  }

  // ── Processing screen (no cancel window) ──────────────────────────────────
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
            'Processing your transfer',
            style: PayPinkTheme.display(
              fontSize: 22,
              fontWeight: FontWeight.w800,
              color: textInk,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            'Core banking is taking a little longer than usual.\nYour receipt will appear here as soon as it posts.',
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

    // Show the processing screen while core banking finishes a 202 PROCESSING transfer
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
            style: PayPinkTheme.body(fontSize: 13, color: textMuted),
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
                          fontSize: 10,
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
                      _buildTabBtn('My Accounts', 0, isDark: isDark),
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
                  Text('Transfer from', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: textInk)),
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
                      Text('Favorites', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w700, color: textInk)),
                      GestureDetector(
                        onTap: () => _showFavoritesManagerModal(context),
                        child: Text(
                          'Favorites →',
                          style: PayPinkTheme.body(fontSize: 11, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),

                  // Quick Favorites Row
                  if (_isLoadingFavorites) ...[
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 12),
                      child: Center(
                        child: SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2, color: PayPinkTheme.wine),
                        ),
                      ),
                    ),
                  ] else if (_favorites.isEmpty) ...[
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Center(
                        child: Text(
                          'No saved favorites yet.',
                          style: PayPinkTheme.body(fontSize: 11, color: textMuted),
                        ),
                      ),
                    ),
                  ] else ...[
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
                                    fav['avatar'] ?? 'PP',
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
                                (fav['name'] ?? 'Recipient').split(' ')[0],
                                style: PayPinkTheme.body(fontSize: 10, fontWeight: FontWeight.w600, color: textInk),
                              ),
                            ],
                          ),
                        );
                      }),
                    ),
                  ],
                  const SizedBox(height: 12),
                  if (_selectedModeIndex == 2) ...[
                    Text('Choose how you want to transfer', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: textInk)),
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
                                  Text('Real-time · Up to ₱50k', style: PayPinkTheme.body(fontSize: 10, color: textMuted)),
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
                                  Text('Next clearing batch · Larger amounts', style: PayPinkTheme.body(fontSize: 10, color: textMuted)),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                  ],
                  Text('Recipient Account Number', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: textInk)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _recipientController,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    onChanged: (_) => _updateCalculations(),
                    style: PayPinkTheme.body(fontSize: 13, color: textInk),
                    decoration: InputDecoration(
                      hintText: 'e.g. 001381233467 (12 digits)',
                      hintStyle: PayPinkTheme.body(fontSize: 13, color: textMuted),
                      filled: true,
                      fillColor: cardBg,
                      errorText: (_selectedModeIndex == 1 && _recipientController.text.isNotEmpty) ? _recipientError : null,
                      errorStyle: PayPinkTheme.body(fontSize: 11, color: PayPinkTheme.red),
                      errorMaxLines: 2,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(color: textLine),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(
                          color: (_selectedModeIndex == 1 && _recipientError != null && _recipientController.text.isNotEmpty)
                              ? PayPinkTheme.red
                              : textLine,
                        ),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(
                          color: (_selectedModeIndex == 1 && _recipientError != null && _recipientController.text.isNotEmpty)
                              ? PayPinkTheme.red
                              : PayPinkTheme.wine,
                        ),
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                    ),
                  ),
                  if (_isLookingUpRecipient) ...[
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        const SizedBox(
                          width: 12,
                          height: 12,
                          child: CircularProgressIndicator(strokeWidth: 2, color: PayPinkTheme.wine),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Verifying PayPink account...',
                          style: PayPinkTheme.body(fontSize: 11, color: textMuted),
                        ),
                      ],
                    ),
                  ],
                  // Bank transfers: name and bank come from the partner directory (web: external.js lookup)
                  if (_selectedModeIndex == 2) ...[
                    const SizedBox(height: 8),
                    Builder(builder: (_) {
                      final number = _recipientController.text.replaceAll(RegExp(r'\s'), '');
                      final match = _externalMatch;
                      final String message;
                      final Color color;
                      if (match != null) {
                        message = 'Account found: ${match.name} · ${match.bank}';
                        color = PayPinkTheme.green;
                      } else if (number.length >= 12) {
                        message = 'Account not found. Check the account number.';
                        color = PayPinkTheme.red;
                      } else {
                        message = 'Enter the full 12-digit account number to find the recipient.';
                        color = textMuted;
                      }
                      return Semantics(
                        liveRegion: true,
                        child: Text(message, style: PayPinkTheme.body(fontSize: 12, color: color, fontWeight: match != null ? FontWeight.w600 : FontWeight.w400)),
                      );
                    }),
                  ],
                  if (_selectedModeIndex == 1 && _verifiedName != null && _recipientError == null) ...[
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
                                style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w700, color: PayPinkTheme.green),
                              ),
                            ],
                          ),
                          Row(
                            children: [
                              Text('Match', style: PayPinkTheme.body(fontSize: 10, color: PayPinkTheme.green)),
                              const SizedBox(width: 8),
                              GestureDetector(
                                onTap: () => _saveFavorite(_recipientController.text),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: isDark ? PayPinkTheme.wine.withValues(alpha: 0.3) : PayPinkTheme.pinkSubtle,
                                    borderRadius: BorderRadius.circular(6),
                                    border: Border.all(color: PayPinkTheme.wine),
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      const Icon(Icons.star_rounded, size: 12, color: PayPinkTheme.wine),
                                      const SizedBox(width: 2),
                                      Text(
                                        'Favorite',
                                        style: PayPinkTheme.eyebrow(fontSize: 10, fontWeight: FontWeight.w700, color: PayPinkTheme.wine),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
                const SizedBox(height: 16),

                // Amount Field
                Text('Amount', style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: textInk)),
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
                    Text('Transfer fee', style: PayPinkTheme.body(fontSize: 12, color: textMuted)),
                    Text('₱0.00', style: PayPinkTheme.display(fontSize: 13, fontWeight: FontWeight.w700, color: textInk)),
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
                      textStyle: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700),
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
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: isSelected ? Colors.white : textMuted,
            ),
          ),
        ),
      ),
    );
  }
}
