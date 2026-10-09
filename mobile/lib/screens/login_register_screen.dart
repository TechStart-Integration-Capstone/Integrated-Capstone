import 'dart:ui';
import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';
import '../services/auth_service.dart';
import '../widgets/paypink_logo.dart';

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
  bool _rememberMe = true;

  // Controllers
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _firstNameController = TextEditingController();
  final _lastNameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _emailController = TextEditingController();
  bool _obscureConfirmPassword = true;
  String _selectedAccountType = 'EVERYDAY';

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

  Future<void> _submit() async {
    final username = _usernameController.text.trim().toLowerCase();
    final password = _passwordController.text; // Do not trim password

    if (username.isEmpty || password.isEmpty) {
      _showToast('Please enter both username and password', Icons.warning_amber_rounded);
      return;
    }

    if (_isRegister) {
      final firstName = _firstNameController.text.trim();
      final lastName = _lastNameController.text.trim();
      final email = _emailController.text.trim();
      final phone = _phoneController.text.trim();
      final confirmPassword = _confirmPasswordController.text;

      if (firstName.isEmpty || lastName.isEmpty || email.isEmpty || phone.isEmpty) {
        _showToast('Please fill out all registration fields', Icons.warning_amber_rounded);
        return;
      }

      if (password != confirmPassword) {
        _showToast('Your passwords don’t match. Please enter them again.', Icons.warning_amber_rounded);
        return;
      }

      if (password.length < 8) {
        _showToast('Password must be at least 8 characters long.', Icons.warning_amber_rounded);
        return;
      }

      final userRegex = RegExp(r'^[a-zA-Z0-9_]{3,50}$');
      if (!userRegex.hasMatch(username)) {
        _showToast('Username must be 3–50 characters (letters, numbers, or underscores).', Icons.warning_amber_rounded);
        return;
      }

      setState(() => _isLoading = true);

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
          _showToast('Account created! Welcome to PayPink.', Icons.check_circle_rounded);
          widget.onLoginSuccess(
            res.username ?? username,
            res.fullName ?? '$firstName $lastName',
          );
        } else {
          _showToast(res.message, Icons.error_outline_rounded);
        }
      } catch (e) {
        if (!mounted) return;
        setState(() => _isLoading = false);
        _showToast('Registration failed: ${e.toString()}', Icons.error_outline_rounded);
      }
    } else {
      setState(() => _isLoading = true);
      try {
        final res = await AuthService.login(
          username: username,
          password: password,
        );

        if (!mounted) return;
        setState(() => _isLoading = false);

        if (res.success) {
          _showToast(res.message, Icons.verified_user_rounded);
          widget.onLoginSuccess(
            res.username ?? username,
            res.fullName ?? username,
          );
        } else {
          _showToast(res.message, Icons.error_outline_rounded);
        }
      } catch (e) {
        if (!mounted) return;
        setState(() => _isLoading = false);
        _showToast('Authentication failed: ${e.toString()}', Icons.error_outline_rounded);
      }
    }
  }

  void _showToast(String message, IconData icon) {
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: PayPinkTheme.wine,
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.all(18),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        content: Row(
          children: [
            Icon(icon, color: Colors.white, size: 20),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                message,
                style: PayPinkTheme.body(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = widget.isDarkMode;
    final cardBg = isDark ? PayPinkTheme.darkGlassCardBg : Colors.white.withValues(alpha: 0.92);
    final textColor = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final mutedColor = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final borderColor = isDark ? PayPinkTheme.darkGlassBorder : Colors.white;

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF121828) : const Color(0xFFEFE8EC),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Container(
            decoration: BoxDecoration(
              gradient: isDark ? PayPinkTheme.darkBgGradient : PayPinkTheme.lightBgGradient,
            ),
            child: Stack(
              children: [
                // Ambient Background Glow Orbs
                Positioned(
                  top: -60,
                  right: -40,
                  child: Container(
                    width: 240,
                    height: 240,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: RadialGradient(
                        colors: [
                          PayPinkTheme.wineLight.withValues(alpha: isDark ? 0.35 : 0.22),
                          Colors.transparent,
                        ],
                      ),
                    ),
                  ),
                ),
                Positioned(
                  bottom: 80,
                  left: -60,
                  child: Container(
                    width: 200,
                    height: 200,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: RadialGradient(
                        colors: [
                          PayPinkTheme.pink.withValues(alpha: isDark ? 0.15 : 0.25),
                          Colors.transparent,
                        ],
                      ),
                    ),
                  ),
                ),

                // Main Scrollable Content
                SafeArea(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // Top Bar: Theme Toggle
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                              decoration: BoxDecoration(
                                color: (isDark ? Colors.white : PayPinkTheme.wine).withValues(alpha: 0.08),
                                borderRadius: BorderRadius.circular(20),
                                border: Border.all(
                                  color: (isDark ? Colors.white : PayPinkTheme.wine).withValues(alpha: 0.15),
                                ),
                              ),
                              child: Row(
                                children: [
                                  Container(
                                    width: 7,
                                    height: 7,
                                    decoration: const BoxDecoration(
                                      color: PayPinkTheme.green,
                                      shape: BoxShape.circle,
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  Text(
                                    'Encrypted & Verified',
                                    style: PayPinkTheme.mono(
                                      fontSize: 10,
                                      fontWeight: FontWeight.w600,
                                      color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              onPressed: widget.onToggleTheme,
                              tooltip: isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode',
                              icon: Container(
                                padding: const EdgeInsets.all(8),
                                decoration: BoxDecoration(
                                  color: cardBg,
                                  shape: BoxShape.circle,
                                  border: Border.all(color: borderColor),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black.withValues(alpha: 0.05),
                                      blurRadius: 10,
                                    ),
                                  ],
                                ),
                                child: Icon(
                                  isDark ? Icons.light_mode_rounded : Icons.dark_mode_rounded,
                                  color: isDark ? const Color(0xFFFBBF24) : PayPinkTheme.wine,
                                  size: 18,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),

                        // PayPink Brand Header
                        Center(
                          child: PayPinkLogo.authHero(
                            isDark: isDark,
                          ),
                        ),
                        const SizedBox(height: 28),

                        // Glassmorphic Auth Card
                        ClipRRect(
                          borderRadius: BorderRadius.circular(24),
                          child: BackdropFilter(
                            filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                            child: Container(
                              padding: const EdgeInsets.all(22),
                              decoration: BoxDecoration(
                                color: cardBg,
                                borderRadius: BorderRadius.circular(24),
                                border: Border.all(color: borderColor, width: 1.4),
                                boxShadow: [
                                  BoxShadow(
                                    color: (isDark ? Colors.black : PayPinkTheme.wine).withValues(alpha: 0.08),
                                    blurRadius: 24,
                                    offset: const Offset(0, 12),
                                  ),
                                ],
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  // Tab Switcher: Sign In vs Register
                                  Container(
                                    padding: const EdgeInsets.all(4),
                                    decoration: BoxDecoration(
                                      color: (isDark ? Colors.black : PayPinkTheme.pinkSubtle).withValues(alpha: 0.6),
                                      borderRadius: BorderRadius.circular(16),
                                    ),
                                    child: Row(
                                      children: [
                                        Expanded(
                                          child: GestureDetector(
                                            onTap: () => setState(() => _isRegister = false),
                                            child: AnimatedContainer(
                                              duration: const Duration(milliseconds: 200),
                                              padding: const EdgeInsets.symmetric(vertical: 10),
                                              decoration: BoxDecoration(
                                                color: !_isRegister
                                                    ? (isDark ? PayPinkTheme.wine : Colors.white)
                                                    : Colors.transparent,
                                                borderRadius: BorderRadius.circular(12),
                                                boxShadow: !_isRegister
                                                    ? [
                                                        BoxShadow(
                                                          color: Colors.black.withValues(alpha: 0.08),
                                                          blurRadius: 8,
                                                        ),
                                                      ]
                                                    : [],
                                              ),
                                              child: Text(
                                                'Sign In',
                                                textAlign: TextAlign.center,
                                                style: PayPinkTheme.body(
                                                  fontWeight: FontWeight.w700,
                                                  fontSize: 12.5,
                                                  color: !_isRegister
                                                      ? (isDark ? Colors.white : PayPinkTheme.wine)
                                                      : mutedColor,
                                                ),
                                              ),
                                            ),
                                          ),
                                        ),
                                        Expanded(
                                          child: GestureDetector(
                                            onTap: () => setState(() => _isRegister = true),
                                            child: AnimatedContainer(
                                              duration: const Duration(milliseconds: 200),
                                              padding: const EdgeInsets.symmetric(vertical: 10),
                                              decoration: BoxDecoration(
                                                color: _isRegister
                                                    ? (isDark ? PayPinkTheme.wine : Colors.white)
                                                    : Colors.transparent,
                                                borderRadius: BorderRadius.circular(12),
                                                boxShadow: _isRegister
                                                    ? [
                                                        BoxShadow(
                                                          color: Colors.black.withValues(alpha: 0.08),
                                                          blurRadius: 8,
                                                        ),
                                                      ]
                                                    : [],
                                              ),
                                              child: Text(
                                                'Register',
                                                textAlign: TextAlign.center,
                                                style: PayPinkTheme.body(
                                                  fontWeight: FontWeight.w700,
                                                  fontSize: 12.5,
                                                  color: _isRegister
                                                      ? (isDark ? Colors.white : PayPinkTheme.wine)
                                                      : mutedColor,
                                                ),
                                              ),
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  const SizedBox(height: 20),

                                  // Registration Extra Fields
                                  if (_isRegister) ...[
                                    Row(
                                      children: [
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              _buildLabel('First Name', textColor),
                                              _buildTextField(
                                                controller: _firstNameController,
                                                hint: 'e.g. Maria',
                                                icon: Icons.badge_outlined,
                                                isDark: isDark,
                                              ),
                                            ],
                                          ),
                                        ),
                                        const SizedBox(width: 10),
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              _buildLabel('Last Name', textColor),
                                              _buildTextField(
                                                controller: _lastNameController,
                                                hint: 'e.g. Santos',
                                                icon: Icons.badge_outlined,
                                                isDark: isDark,
                                              ),
                                            ],
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 14),
                                    _buildLabel('Mobile Number', textColor),
                                    _buildTextField(
                                      controller: _phoneController,
                                      hint: '+63 917 123 4567',
                                      icon: Icons.phone_android_rounded,
                                      keyboardType: TextInputType.phone,
                                      isDark: isDark,
                                    ),
                                    const SizedBox(height: 14),
                                    _buildLabel('Email Address', textColor),
                                    _buildTextField(
                                      controller: _emailController,
                                      hint: 'you@example.com',
                                      icon: Icons.email_outlined,
                                      keyboardType: TextInputType.emailAddress,
                                      isDark: isDark,
                                    ),
                                    const SizedBox(height: 14),
                                  ],

                                  // Username / Account ID
                                  _buildLabel(_isRegister ? 'Desired Username' : 'Username or Account Number', textColor),
                                  _buildTextField(
                                    controller: _usernameController,
                                    hint: _isRegister ? 'e.g. mariasantos' : 'Enter username or account number',
                                    icon: Icons.person_outline_rounded,
                                    isDark: isDark,
                                  ),
                                  if (_isRegister) ...[
                                    Padding(
                                      padding: const EdgeInsets.only(top: 4, left: 4),
                                      child: Text(
                                        '3–50 letters, numbers, or underscores.',
                                        style: PayPinkTheme.body(fontSize: 10.5, color: mutedColor),
                                      ),
                                    ),
                                  ],
                                  const SizedBox(height: 14),

                                  // Password
                                  _buildLabel('Master Password', textColor),
                                  _buildTextField(
                                    controller: _passwordController,
                                    hint: '••••••••',
                                    icon: Icons.lock_outline_rounded,
                                    obscureText: _obscurePassword,
                                    isDark: isDark,
                                    suffixIcon: IconButton(
                                      icon: Icon(
                                        _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                                        size: 18,
                                        color: mutedColor,
                                      ),
                                      onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                                    ),
                                  ),
                                  if (_isRegister) ...[
                                    Padding(
                                      padding: const EdgeInsets.only(top: 4, left: 4),
                                      child: Text(
                                        'Use 8–64 characters.',
                                        style: PayPinkTheme.body(fontSize: 10.5, color: mutedColor),
                                      ),
                                    ),
                                    const SizedBox(height: 14),
                                    _buildLabel('Confirm Password', textColor),
                                    _buildTextField(
                                      controller: _confirmPasswordController,
                                      hint: '••••••••',
                                      icon: Icons.lock_outline_rounded,
                                      obscureText: _obscureConfirmPassword,
                                      isDark: isDark,
                                      suffixIcon: IconButton(
                                        icon: Icon(
                                          _obscureConfirmPassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                                          size: 18,
                                          color: mutedColor,
                                        ),
                                        onPressed: () => setState(() => _obscureConfirmPassword = !_obscureConfirmPassword),
                                      ),
                                    ),
                                  ],
                                  const SizedBox(height: 12),

                                  // Sign-in options: Remember me & Forgot Password
                                  if (!_isRegister) ...[
                                    Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                        Row(
                                          children: [
                                            SizedBox(
                                              width: 24,
                                              height: 24,
                                              child: Checkbox(
                                                value: _rememberMe,
                                                activeColor: PayPinkTheme.wine,
                                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                                                onChanged: (v) => setState(() => _rememberMe = v ?? true),
                                              ),
                                            ),
                                            const SizedBox(width: 6),
                                            Text(
                                              'Remember Me',
                                              style: PayPinkTheme.body(fontSize: 11.5, color: mutedColor),
                                            ),
                                          ],
                                        ),
                                        GestureDetector(
                                          onTap: () => _showToast('Password reset link sent to registered email.', Icons.info_outline),
                                          child: Text(
                                            'Forgot password?',
                                            style: PayPinkTheme.body(
                                              fontSize: 11.5,
                                              fontWeight: FontWeight.w600,
                                              color: PayPinkTheme.wine,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 18),
                                  ] else ...[
                                    _buildLabel('Account Type', textColor),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 12),
                                      decoration: BoxDecoration(
                                        color: (isDark ? Colors.black : Colors.white).withValues(alpha: 0.5),
                                        borderRadius: BorderRadius.circular(12),
                                        border: Border.all(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
                                      ),
                                      child: DropdownButtonHideUnderline(
                                        child: DropdownButton<String>(
                                          value: _selectedAccountType,
                                          isExpanded: true,
                                          dropdownColor: isDark ? PayPinkTheme.darkCard : Colors.white,
                                          items: const [
                                            DropdownMenuItem(value: 'EVERYDAY', child: Text('Everyday Checking (0.00% fee)')),
                                            DropdownMenuItem(value: 'SAVINGS', child: Text('High-Yield Savings (4.25% p.a.)')),
                                            DropdownMenuItem(value: 'BUSINESS', child: Text('PayPink Merchant Pro')),
                                          ],
                                          onChanged: (val) => setState(() => _selectedAccountType = val ?? 'EVERYDAY'),
                                          style: PayPinkTheme.body(fontSize: 12, color: textColor),
                                        ),
                                      ),
                                    ),
                                    const SizedBox(height: 20),
                                  ],

                                  // Primary Action Button
                                  SizedBox(
                                    height: 50,
                                    child: ElevatedButton(
                                      onPressed: _isLoading ? null : _submit,
                                      style: ElevatedButton.styleFrom(
                                        backgroundColor: PayPinkTheme.wine,
                                        foregroundColor: Colors.white,
                                        elevation: 4,
                                        shadowColor: PayPinkTheme.wine.withValues(alpha: 0.4),
                                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                                      ),
                                      child: _isLoading
                                          ? const SizedBox(
                                              width: 20,
                                              height: 20,
                                              child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                                            )
                                          : Text(
                                              _isRegister ? 'Create PayPink Account' : 'Sign In',
                                              style: PayPinkTheme.body(
                                                fontSize: 14,
                                                fontWeight: FontWeight.w700,
                                                color: Colors.white,
                                              ),
                                            ),
                                    ),
                                  ),
                                  const SizedBox(height: 14),

                                ],
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 24),

                        // Footer Security Badge
                        Center(
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.lock_rounded, size: 13, color: mutedColor),
                              const SizedBox(width: 6),
                              Flexible(
                                child: Text(
                                  'Hardware KeyStore 256-Bit Encrypted · BSP Regulated',
                                  style: PayPinkTheme.body(fontSize: 9.5, color: mutedColor),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLabel(String text, Color color) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Text(
        text,
        style: PayPinkTheme.body(
          fontSize: 11.5,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String hint,
    required IconData icon,
    required bool isDark,
    bool obscureText = false,
    Widget? suffixIcon,
    TextInputType? keyboardType,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: (isDark ? Colors.black : Colors.white).withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line,
          width: 1.1,
        ),
      ),
      child: TextField(
        controller: controller,
        obscureText: obscureText,
        keyboardType: keyboardType,
        style: PayPinkTheme.body(
          fontSize: 13,
          color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink,
        ),
        decoration: InputDecoration(
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          border: InputBorder.none,
          hintText: hint,
          hintStyle: PayPinkTheme.body(
            fontSize: 12.5,
            color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
          ),
            prefixIcon: Icon(
            icon,
            color: isDark ? PayPinkTheme.pink : PayPinkTheme.wine,
            size: 18,
          ),
          suffixIcon: suffixIcon,
        ),
      ),
    );
  }

}
