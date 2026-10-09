import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';
import '../services/account_service.dart';
import '../services/secure_token_storage.dart';
import '../screens/pin_auth_screen.dart';
import '../widgets/pin_auth_sheet.dart';

class ProfileSheet extends StatefulWidget {
  final UserProfile? userProfile;
  final String currentUser;
  final bool isDarkMode;
  final VoidCallback onToggleTheme;
  final VoidCallback onLogout;
  final Function(String newName, String newEmail)? onUpdateProfile;

  const ProfileSheet({
    super.key,
    this.userProfile,
    required this.currentUser,
    required this.isDarkMode,
    required this.onToggleTheme,
    required this.onLogout,
    this.onUpdateProfile,
  });

  static void show(
    BuildContext context, {
    UserProfile? userProfile,
    required String currentUser,
    required bool isDarkMode,
    required VoidCallback onToggleTheme,
    required VoidCallback onLogout,
    Function(String newName, String newEmail)? onUpdateProfile,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => ProfileSheet(
        userProfile: userProfile,
        currentUser: currentUser,
        isDarkMode: Theme.of(context).brightness == Brightness.dark,
        onToggleTheme: onToggleTheme,
        onLogout: onLogout,
        onUpdateProfile: onUpdateProfile,
      ),
    );
  }

  @override
  State<ProfileSheet> createState() => _ProfileSheetState();
}

class _ProfileSheetState extends State<ProfileSheet> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _nameController;
  late TextEditingController _emailController;
  bool _isEditing = false;
  bool _isSaving = false;
  late bool _isDark;

  @override
  void initState() {
    super.initState();
    _isDark = widget.isDarkMode;
    final profile = widget.userProfile;
    final fallbackUser = widget.currentUser.isNotEmpty ? widget.currentUser : 'Customer';
    final initialName = profile != null && profile.fullName.isNotEmpty
        ? profile.fullName
        : fallbackUser;
    final initialEmail = profile != null && profile.email.isNotEmpty
        ? profile.email
        : (widget.currentUser.isNotEmpty
            ? '${widget.currentUser.toLowerCase().replaceAll(' ', '.')}@paypink.ph'
            : 'customer@paypink.ph');

    _nameController = TextEditingController(text: initialName);
    _emailController = TextEditingController(text: initialEmail);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _isDark = Theme.of(context).brightness == Brightness.dark;
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    super.dispose();
  }

  String get _initials {
    final name = _nameController.text.trim();
    if (name.isEmpty) return 'P';
    final parts = name.split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.length >= 2 && parts[0].isNotEmpty && parts[1].isNotEmpty) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return name[0].toUpperCase();
  }

  Future<void> _handleSaveProfile() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSaving = true);
    HapticFeedback.mediumImpact();

    final newName = _nameController.text.trim();
    final newEmail = _emailController.text.trim();

    final currentCustomerId = await SecureTokenStorage.getCustomerId() ?? 5;
    // Persist in secure storage session
    await SecureTokenStorage.saveUserSession(
      username: widget.currentUser,
      fullName: newName,
      customerId: currentCustomerId,
    );

    if (widget.onUpdateProfile != null) {
      widget.onUpdateProfile!(newName, newEmail);
    }

    setState(() {
      _isSaving = false;
      _isEditing = false;
    });

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: PayPinkTheme.wine,
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          content: const Row(
            children: [
              Icon(Icons.check_circle_rounded, color: PayPinkTheme.pink, size: 18),
              SizedBox(width: 8),
              Text('Personal information updated successfully.'),
            ],
          ),
        ),
      );
    }
  }

  Future<void> _handleChangePin() async {
    final hasPin = await SecureTokenStorage.hasPin();
    if (!mounted) return;

    if (hasPin) {
      // Step 1: Verify current PIN
      final verified = await PinAuthSheet.show(
        context,
        title: 'Verify Current MPIN',
        description: 'Enter your existing 6-digit MPIN to authorize changing your security credentials.',
      );

      if (!verified || !mounted) return;
    }

    // Step 2: Push PIN Setup screen to create and confirm new MPIN
    Navigator.pop(context); // Close profile sheet
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => PinAuthScreen(
          mode: PinScreenMode.setup,
          username: widget.currentUser,
          fullName: _nameController.text,
          isDarkMode: _isDark,
          onAuthSuccess: () {
            Navigator.pop(context);
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                backgroundColor: PayPinkTheme.wine,
                content: Row(
                  children: [
                    Icon(Icons.check_circle_rounded, color: PayPinkTheme.pink, size: 18),
                    SizedBox(width: 8),
                    Text('6-Digit MPIN updated successfully.'),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  void _confirmLogout() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: _isDark ? PayPinkTheme.darkCard : Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Row(
          children: [
            const Icon(Icons.logout_rounded, color: PayPinkTheme.wine, size: 22),
            const SizedBox(width: 10),
            Text(
              'Log Out',
              style: PayPinkTheme.display(
                fontSize: 18,
                fontWeight: FontWeight.w800,
                color: _isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink,
              ),
            ),
          ],
        ),
        content: Text(
          'Are you sure you want to end your secure PayPink banking session?',
          style: PayPinkTheme.body(
            fontSize: 13,
            color: _isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(
              'Cancel',
              style: PayPinkTheme.body(
                fontWeight: FontWeight.w600,
                color: _isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
              ),
            ),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pop(context); // Close sheet
              widget.onLogout();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: PayPinkTheme.wine,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: const Text('Log Out'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = _isDark;
    final bg = isDark ? PayPinkTheme.darkPaper : Colors.white;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final textLine = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final cardBg = isDark ? PayPinkTheme.darkCard : const Color(0xFFFAF7F9);

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.88,
      ),
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(22, 12, 22, 28),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              // Grab Handle
              Center(
                child: Container(
                  width: 40,
                  height: 4.5,
                  decoration: BoxDecoration(
                    color: textLine,
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
              ),
              const SizedBox(height: 18),

              // Customer Avatar
              Stack(
                alignment: Alignment.bottomRight,
                children: [
                  Container(
                    width: 76,
                    height: 76,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [Color(0xFF8A2754), Color(0xFF4A1528)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: PayPinkTheme.pink,
                        width: 2.5,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: PayPinkTheme.wine.withValues(alpha: 0.35),
                          blurRadius: 14,
                          offset: const Offset(0, 6),
                        ),
                      ],
                    ),
                    child: Center(
                      child: Text(
                        _initials,
                        style: PayPinkTheme.display(
                          fontSize: 26,
                          fontWeight: FontWeight.w800,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      color: PayPinkTheme.green,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.check, size: 12, color: Colors.white),
                  ),
                ],
              ),
              const SizedBox(height: 10),

              // Customer Name & Verified Status
              Text(
                _nameController.text,
                style: PayPinkTheme.display(
                  fontSize: 19,
                  fontWeight: FontWeight.w800,
                  color: textInk,
                ),
              ),
              const SizedBox(height: 4),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.verified_rounded, size: 12, color: PayPinkTheme.green),
                        const SizedBox(width: 4),
                        Text(
                          'VERIFIED CUSTOMER · TIER 1',
                          style: PayPinkTheme.eyebrow(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: PayPinkTheme.green,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Personal Information Section
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: cardBg,
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: textLine),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Personal Details',
                          style: PayPinkTheme.display(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: textInk,
                          ),
                        ),
                        InkWell(
                          onTap: () {
                            setState(() => _isEditing = !_isEditing);
                          },
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            child: Text(
                              _isEditing ? 'Cancel' : 'Edit',
                              style: PayPinkTheme.body(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    // Full Name Field
                    Text('Full Name', style: PayPinkTheme.body(fontSize: 11, color: textMuted)),
                    const SizedBox(height: 4),
                    TextFormField(
                      controller: _nameController,
                      enabled: _isEditing,
                      style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w600, color: textInk),
                      decoration: InputDecoration(
                        isDense: true,
                        filled: _isEditing,
                        fillColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: textLine)),
                      ),
                      validator: (val) {
                        if (val == null || val.trim().isEmpty) return 'Full name cannot be empty';
                        if (val.trim().length < 3) return 'Please enter a valid full name';
                        return null;
                      },
                    ),
                    const SizedBox(height: 10),

                    // Email Field
                    Text('Email Address', style: PayPinkTheme.body(fontSize: 11, color: textMuted)),
                    const SizedBox(height: 4),
                    TextFormField(
                      controller: _emailController,
                      enabled: _isEditing,
                      keyboardType: TextInputType.emailAddress,
                      style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w600, color: textInk),
                      decoration: InputDecoration(
                        isDense: true,
                        filled: _isEditing,
                        fillColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: textLine)),
                      ),
                      validator: (val) {
                        if (val == null || val.trim().isEmpty) return 'Email cannot be empty';
                        if (!val.contains('@') || !val.contains('.')) return 'Please enter a valid email address';
                        return null;
                      },
                    ),

                    if (_isEditing) ...[
                      const SizedBox(height: 14),
                      SizedBox(
                        width: double.infinity,
                        height: 40,
                        child: ElevatedButton(
                          onPressed: _isSaving ? null : _handleSaveProfile,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: PayPinkTheme.wine,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                          ),
                          child: _isSaving
                              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                              : const Text('Save Details', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12)),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Security & PIN Workflow
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: cardBg,
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: textLine),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Security & Credentials',
                      style: PayPinkTheme.display(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: textInk,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Material(
                      color: Colors.transparent,
                      child: ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Container(
                          width: 38,
                          height: 38,
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle,
                            shape: BoxShape.circle,
                          ),
                          child: Icon(Icons.dialpad_rounded, color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine, size: 20),
                        ),
                        title: Text(
                          'Change 6-Digit MPIN',
                          style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w700, color: textInk),
                        ),
                        subtitle: Text(
                          'Update the MPIN used for quick login and step-up transaction signoff.',
                          style: PayPinkTheme.body(fontSize: 10, color: textMuted),
                        ),
                        trailing: const Icon(Icons.chevron_right_rounded, size: 20),
                        onTap: _handleChangePin,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // App Appearance & Logout Action
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: cardBg,
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: textLine),
                ),
                child: Column(
                  children: [
                    Material(
                      color: Colors.transparent,
                      child: ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Container(
                          width: 38,
                          height: 38,
                          decoration: BoxDecoration(
                            color: isDark ? Colors.white10 : Colors.black.withValues(alpha: 0.04),
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            isDark ? Icons.light_mode_rounded : Icons.dark_mode_rounded,
                            color: isDark ? const Color(0xFFFBBF24) : PayPinkTheme.wine,
                            size: 19,
                          ),
                        ),
                        title: Text(
                          isDark ? 'Dark Mode Active' : 'Light Mode Active',
                          style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w700, color: textInk),
                        ),
                        subtitle: Text(
                          'Switch theme between luxury dark and clean pearl light.',
                          style: PayPinkTheme.body(fontSize: 10, color: textMuted),
                        ),
                        trailing: Switch(
                          value: isDark,
                          activeThumbColor: PayPinkTheme.pink,
                          activeTrackColor: PayPinkTheme.wine,
                          onChanged: (val) {
                            setState(() {
                              _isDark = val;
                            });
                            widget.onToggleTheme();
                          },
                        ),
                      ),
                    ),
                    Divider(color: textLine, height: 1),
                    Material(
                      color: Colors.transparent,
                      child: ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Container(
                          width: 38,
                          height: 38,
                          decoration: BoxDecoration(
                            color: PayPinkTheme.red.withValues(alpha: 0.12),
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(Icons.logout_rounded, color: PayPinkTheme.red, size: 19),
                        ),
                        title: Text(
                          'End Session / Log Out',
                          style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w700, color: PayPinkTheme.red),
                        ),
                        subtitle: Text(
                          'Securely clear local encryption keys and sign out.',
                          style: PayPinkTheme.body(fontSize: 10, color: textMuted),
                        ),
                        trailing: const Icon(Icons.chevron_right_rounded, size: 20),
                        onTap: _confirmLogout,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
