import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../services/auth_service.dart';
import '../widgets/paypink_logo.dart';

/// Customer sign-in and registration.
/// Mirrors the web's `.auth-panel` (frontend/bank/bank.js `renderAuth`) at phone width:
/// wordmark, kicker, heading, labelled fields, full-width wine button, mode switch link, note.
class LoginRegisterScreen extends StatefulWidget {
  final void Function(String username, String fullName) onLoginSuccess;
  final bool isDarkMode;
  final VoidCallback onToggleTheme;

  const LoginRegisterScreen({
    super.key,
    required this.onLoginSuccess,
    required this.isDarkMode,
    required this.onToggleTheme,
  });

  @override
  State<LoginRegisterScreen> createState() => _LoginRegisterScreenState();
}

class _LoginRegisterScreenState extends State<LoginRegisterScreen> {
  bool _isRegister = false;
  bool _isLoading = false;
  bool _obscurePassword = true;
  String? _error;

  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _firstNameController = TextEditingController();
  final _lastNameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _emailController = TextEditingController();

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    _firstNameController.dispose();
    _lastNameController.dispose();
    _phoneController.dispose();
    _emailController.dispose();
    super.dispose();
  }

  void _setMode(bool register) {
    setState(() {
      _isRegister = register;
      _error = null;
    });
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final username = _usernameController.text.trim().toLowerCase();
    final password = _passwordController.text; // Do not trim password

    if (_isRegister) {
      final firstName = _firstNameController.text.trim();
      final lastName = _lastNameController.text.trim();
      final email = _emailController.text.trim();
      final phone = _phoneController.text.trim();

      if (firstName.isEmpty || lastName.isEmpty || email.isEmpty || phone.isEmpty || username.isEmpty || password.isEmpty) {
        setState(() => _error = 'Please fill in every field.');
        return;
      }
      if (!RegExp(r'^[a-zA-Z0-9_]{3,50}$').hasMatch(username)) {
        setState(() => _error = 'Username must be 3–50 letters, numbers, or underscores.');
        return;
      }
      if (password.length < 8 || password.length > 64) {
        setState(() => _error = 'Use 8–64 characters for your password.');
        return;
      }
      if (password != _confirmPasswordController.text) {
        setState(() => _error = 'Your passwords don’t match. Please enter them again.');
        return;
      }

      setState(() {
        _isLoading = true;
        _error = null;
      });
      try {
        final res = await AuthService.register(
          firstName: firstName,
          lastName: lastName,
          email: email,
          phone: phone,
          username: username,
          password: password,
        );
        if (!mounted) return;
        setState(() => _isLoading = false);
        if (res.success) {
          _showToast('Account created. Welcome to PayPink.');
          widget.onLoginSuccess(res.username ?? username, res.fullName ?? '$firstName $lastName');
        } else {
          setState(() => _error = res.message);
        }
      } catch (e) {
        if (!mounted) return;
        setState(() {
          _isLoading = false;
          _error = 'We couldn’t create your account right now. Please try again.';
        });
      }
      return;
    }

    if (username.isEmpty || password.isEmpty) {
      setState(() => _error = 'Please enter your username and password.');
      return;
    }

    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final res = await AuthService.login(username: username, password: password);
      if (!mounted) return;
      setState(() => _isLoading = false);
      if (res.success) {
        widget.onLoginSuccess(res.username ?? username, res.fullName ?? username);
      } else {
        setState(() => _error = res.message);
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _error = 'We couldn’t sign you in right now. Please try again.';
      });
    }
  }

  void _showToast(String message) {
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: PayPinkTheme.wineDark,
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.all(18),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        content: Text(message, style: PayPinkTheme.body(color: Colors.white, fontSize: 13)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = widget.isDarkMode;
    final surface = isDark ? PayPinkTheme.darkPaper : Colors.white;
    final ink = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final muted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final line = isDark ? PayPinkTheme.darkLine : PayPinkTheme.line;
    final accent = isDark ? PayPinkTheme.pink : PayPinkTheme.wine;

    return Scaffold(
      backgroundColor: surface,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(26, 24, 26, 32),
              child: AutofillGroup(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        PayPinkLogo(size: 36, showShadow: false, isDark: isDark),
                        IconButton(
                          onPressed: widget.onToggleTheme,
                          tooltip: isDark ? 'Switch to light mode' : 'Switch to dark mode',
                          icon: Icon(
                            isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined,
                            color: muted,
                            size: 20,
                          ),
                        ),
                      ],
                    ),
                    SizedBox(height: _isRegister ? 28 : 48),

                    // Kicker
                    Row(
                      children: [
                        Icon(Icons.verified_user_outlined, size: 16, color: accent),
                        const SizedBox(width: 8),
                        Text('PERSONAL BANKING', style: PayPinkTheme.eyebrow(fontSize: 11, color: accent)),
                      ],
                    ),
                    SizedBox(height: _isRegister ? 12 : 16),

                    Text(
                      _isRegister ? 'Hello, new beginnings.' : 'Welcome back.',
                      style: PayPinkTheme.display(fontSize: 30, fontWeight: FontWeight.w700, letterSpacing: -1, color: ink, height: 1.3),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      _isRegister
                          ? 'Savings for your plans. Everyday for your daily life. Get both when you join.'
                          : 'A little check-in. A clearer picture of your money.',
                      style: PayPinkTheme.body(fontSize: 13, color: muted, height: 1.6),
                    ),
                    SizedBox(height: _isRegister ? 20 : 32),

                    if (_error != null) ...[
                      Semantics(
                        liveRegion: true,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF3A1E22) : PayPinkTheme.errorBg,
                            border: Border.all(color: isDark ? const Color(0xFF5A2D33) : PayPinkTheme.errorBorder),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            _error!,
                            style: PayPinkTheme.body(fontSize: 12, color: isDark ? const Color(0xFFF2B8B5) : PayPinkTheme.errorText),
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],

                    if (_isRegister) ...[
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(
                            child: _field(
                              label: 'First name',
                              controller: _firstNameController,
                              hint: 'First name',
                              autofill: AutofillHints.givenName,
                              capitalization: TextCapitalization.words,
                            ),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: _field(
                              label: 'Last name',
                              controller: _lastNameController,
                              hint: 'Last name',
                              autofill: AutofillHints.familyName,
                              capitalization: TextCapitalization.words,
                            ),
                          ),
                        ],
                      ),
                      _field(
                        label: 'Email address',
                        controller: _emailController,
                        hint: 'you@example.com',
                        keyboardType: TextInputType.emailAddress,
                        autofill: AutofillHints.email,
                      ),
                      _field(
                        label: 'Mobile number',
                        controller: _phoneController,
                        hint: '+63 917 123 4567',
                        keyboardType: TextInputType.phone,
                        autofill: AutofillHints.telephoneNumber,
                      ),
                    ],

                    _field(
                      label: 'Username',
                      controller: _usernameController,
                      hint: _isRegister ? 'Choose a username' : 'Enter your username',
                      autofill: _isRegister ? AutofillHints.newUsername : AutofillHints.username,
                      helper: _isRegister ? '3–50 letters, numbers, or underscores.' : null,
                    ),
                    _field(
                      label: 'Password',
                      controller: _passwordController,
                      hint: _isRegister ? 'Create a password' : 'Enter your password',
                      obscure: _obscurePassword,
                      autofill: _isRegister ? AutofillHints.newPassword : AutofillHints.password,
                      helper: _isRegister ? 'Use 8–64 characters. Choose something only you know.' : null,
                      onSubmitted: _isRegister ? null : (_) => _submit(),
                      suffix: IconButton(
                        tooltip: _obscurePassword ? 'Show password' : 'Hide password',
                        icon: Icon(
                          _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                          size: 20,
                          color: muted,
                        ),
                        onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                      ),
                    ),
                    if (_isRegister)
                      _field(
                        label: 'Confirm password',
                        controller: _confirmPasswordController,
                        hint: 'Enter your password again',
                        obscure: true,
                        autofill: AutofillHints.newPassword,
                        onSubmitted: (_) => _submit(),
                      ),

                    const SizedBox(height: 8),
                    SizedBox(
                      height: 49,
                      child: FilledButton(
                        onPressed: _isLoading ? null : _submit,
                        style: FilledButton.styleFrom(
                          backgroundColor: PayPinkTheme.wine,
                          foregroundColor: Colors.white,
                          disabledBackgroundColor: PayPinkTheme.wine.withValues(alpha: 0.6),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm)),
                        ),
                        child: _isLoading
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                              )
                            : Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Text(
                                    _isRegister ? 'Create my account' : 'Log in',
                                    style: PayPinkTheme.body(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.white),
                                  ),
                                  const SizedBox(width: 10),
                                  const Icon(Icons.arrow_forward, size: 18),
                                ],
                              ),
                      ),
                    ),

                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          _isRegister ? 'Already part of PayPink?' : 'New around here?',
                          style: PayPinkTheme.body(fontSize: 13, color: muted),
                        ),
                        TextButton(
                          onPressed: _isLoading ? null : () => _setMode(!_isRegister),
                          style: TextButton.styleFrom(
                            foregroundColor: accent,
                            padding: const EdgeInsets.symmetric(horizontal: 6),
                            minimumSize: const Size(44, 44),
                          ),
                          child: Text(
                            _isRegister ? 'Log in' : 'Open an account',
                            style: PayPinkTheme.body(fontSize: 13, fontWeight: FontWeight.w700, color: accent),
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 22),
                    Container(
                      padding: const EdgeInsets.only(top: 22),
                      decoration: BoxDecoration(border: Border(top: BorderSide(color: line))),
                      child: Text(
                        _isRegister
                            ? 'Your Savings account starts at ₱0.00.\nEnjoy a ₱50 welcome gift in your new Everyday account.'
                            : 'Keep your password to yourself.\nAlways log out when using a shared device.',
                        textAlign: TextAlign.center,
                        style: PayPinkTheme.body(fontSize: 11, color: muted, height: 1.7),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _field({
    required String label,
    required TextEditingController controller,
    required String hint,
    String? autofill,
    String? helper,
    bool obscure = false,
    Widget? suffix,
    TextInputType? keyboardType,
    TextCapitalization capitalization = TextCapitalization.none,
    ValueChanged<String>? onSubmitted,
  }) {
    final isDark = widget.isDarkMode;
    final ink = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final muted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final border = isDark ? PayPinkTheme.darkLine : PayPinkTheme.inputBorder;
    OutlineInputBorder outline(Color c, [double w = 1]) => OutlineInputBorder(
          borderRadius: BorderRadius.circular(PayPinkTheme.radiusSm),
          borderSide: BorderSide(color: c, width: w),
        );

    return Padding(
      padding: EdgeInsets.only(bottom: _isRegister ? 12 : 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: PayPinkTheme.body(fontSize: 12, fontWeight: FontWeight.w600, color: ink)),
          const SizedBox(height: 7),
          TextField(
            controller: controller,
            obscureText: obscure,
            keyboardType: keyboardType,
            textCapitalization: capitalization,
            autocorrect: false,
            enableSuggestions: !obscure,
            autofillHints: autofill == null ? null : [autofill],
            textInputAction: onSubmitted == null ? TextInputAction.next : TextInputAction.done,
            onSubmitted: onSubmitted,
            style: PayPinkTheme.body(fontSize: 14, color: ink),
            decoration: InputDecoration(
              isDense: true,
              filled: true,
              fillColor: isDark ? PayPinkTheme.darkCard : Colors.white,
              contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
              hintText: hint,
              hintStyle: PayPinkTheme.body(fontSize: 14, color: isDark ? muted : PayPinkTheme.placeholder),
              enabledBorder: outline(border),
              focusedBorder: outline(PayPinkTheme.focusRing, 2),
              suffixIcon: suffix,
            ),
          ),
          if (helper != null) ...[
            const SizedBox(height: 6),
            Text(helper, style: PayPinkTheme.body(fontSize: 11, color: muted)),
          ],
        ],
      ),
    );
  }
}
