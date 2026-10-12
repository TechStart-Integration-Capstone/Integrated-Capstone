import 'package:flutter/material.dart';
import '../services/account_service.dart';
import '../services/savings_service.dart';
import '../theme/paypink_theme.dart';

/// Full Savings Hub for Mobile, mirroring frontend/bank/savings-live.js:
/// - Personal Savings Goals with categories, progress, and automatic contribution schedules
/// - PinkCircles (shared group goals) with members, invitation handling, and privacy controls
/// - Real-time funding and available balance checks before setting aside money
class SavingsHubScreen extends StatefulWidget {
  final List<BankAccount> initialAccounts;
  final VoidCallback? onChanged;

  const SavingsHubScreen({
    super.key,
    required this.initialAccounts,
    this.onChanged,
  });

  static Future<void> open(
    BuildContext context, {
    required List<BankAccount> accounts,
    VoidCallback? onChanged,
  }) {
    return Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => SavingsHubScreen(
        initialAccounts: accounts,
        onChanged: onChanged,
      ),
    ));
  }

  @override
  State<SavingsHubScreen> createState() => _SavingsHubScreenState();
}

class _SavingsHubScreenState extends State<SavingsHubScreen> {
  late List<BankAccount> _accounts = widget.initialAccounts;
  SavingsOverview? _overview;
  List<PinkCircleItem> _circles = [];
  bool _loading = true;
  String? _error;
  String? _notice;
  String _activeTab = 'personal'; // 'personal' or 'circles'
  String _activeSummary = 'personal'; // 'personal' or 'groups'

  static const Map<String, (IconData, String)> _categories = {
    'EMERGENCY': (Icons.shield_outlined, 'Emergency'),
    'HOLIDAY': (Icons.card_giftcard_rounded, 'Holiday / Gifts'),
    'TRAVEL': (Icons.flight_takeoff_rounded, 'Travel'),
    'LIFESTYLE': (Icons.confirmation_number_outlined, 'Lifestyle / Concert'),
    'OTHER': (Icons.star_outline_rounded, 'Other'),
  };

  List<BankAccount> get _savingsAccounts =>
      _accounts.where((a) => a.isSavings && a.status.toUpperCase() == 'ACTIVE').toList();

  @override
  void initState() {
    super.initState();
    _reload();
  }

  Future<void> _reload() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final overviewFuture = SavingsService.fetchOverview();
      final circlesFuture = SavingsService.fetchCircles();
      final results = await Future.wait([overviewFuture, circlesFuture]);

      if (!mounted) return;
      setState(() {
        _overview = results[0] as SavingsOverview?;
        _circles = (results[1] as List<PinkCircleItem>?) ?? [];
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = 'Unable to load Savings Hub. Please try again.';
        _loading = false;
      });
    }
  }

  void _showNotice(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        backgroundColor: PayPinkTheme.wine,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  String _formatCurrency(double amount) {
    final parts = amount.toStringAsFixed(2).split('.');
    final intPart = parts[0].replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
      (Match m) => '${m[1]},',
    );
    return '₱$intPart.${parts[1]}';
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final ink = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final muted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;
    final bg = isDark ? PayPinkTheme.darkBg : PayPinkTheme.paper;

    return Container(
      color: bg,
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Scaffold(
            backgroundColor: Colors.transparent,
            appBar: AppBar(
              backgroundColor: Colors.transparent,
              surfaceTintColor: Colors.transparent,
              foregroundColor: ink,
              title: Text(
                'Savings Hub',
                style: PayPinkTheme.display(fontSize: 20, fontWeight: FontWeight.w700, color: ink),
              ),
              actions: [
                IconButton(
                  icon: const Icon(Icons.refresh_rounded),
                  tooltip: 'Refresh',
                  onPressed: _loading ? null : _reload,
                ),
              ],
            ),
            body: SafeArea(
              top: false,
              child: RefreshIndicator(
                color: PayPinkTheme.wine,
                onRefresh: _reload,
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(18, 8, 18, 36),
                  children: [
                    Text('Your goals, your pace. A little today, a little closer.',
                        style: PayPinkTheme.body(fontSize: 13, color: muted)),
                    const SizedBox(height: 16),
                    if (_notice != null) ...[
                      _buildNoticeBanner(_notice!, isDark),
                      const SizedBox(height: 12),
                    ],
                    if (_error != null) ...[
                      _buildErrorCard(_error!, isDark),
                      const SizedBox(height: 16),
                    ],
                    _buildOverviewCard(isDark, ink, muted),
                    const SizedBox(height: 18),
                    _buildSegmentTabs(isDark, ink),
                    const SizedBox(height: 18),
                    if (_activeTab == 'personal')
                      _buildPersonalTab(isDark, ink, muted)
                    else
                      _buildCirclesTab(isDark, ink, muted),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNoticeBanner(String message, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: PayPinkTheme.pinkSubtle,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: PayPinkTheme.pink),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline_rounded, color: PayPinkTheme.wine, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: PayPinkTheme.body(fontSize: 12, color: PayPinkTheme.wine, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorCard(String error, bool isDark) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF2E161C) : const Color(0xFFFFF1F2),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFF43F5E).withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.warning_amber_rounded, color: Color(0xFFE11D48), size: 20),
              const SizedBox(width: 8),
              Text(
                'Savings Hub Unavailable',
                style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700, color: const Color(0xFFE11D48)),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(error, style: PayPinkTheme.body(fontSize: 12, color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink)),
          const SizedBox(height: 10),
          ElevatedButton(
            onPressed: _reload,
            style: ElevatedButton.styleFrom(
              backgroundColor: PayPinkTheme.wine,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            ),
            child: const Text('Try again', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }

  Widget _buildOverviewCard(bool isDark, Color ink, Color muted) {
    final isGroup = _activeSummary == 'groups';
    final total = isGroup ? (_overview?.groupTotal ?? 0.0) : (_overview?.personalTotal ?? 0.0);
    final count = isGroup
        ? _circles.where((c) => c.isActive).length
        : (_overview?.goals.where((g) => g.circleId == null).length ?? 0);

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isDark
              ? [const Color(0xFF421226), const Color(0xFF260A16)]
              : [const Color(0xFF7A2048), const Color(0xFF4C102A)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF7A2048).withValues(alpha: 0.25),
            blurRadius: 16,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('YOUR SAVINGS',
                  style: PayPinkTheme.display(
                      fontSize: 11, fontWeight: FontWeight.w800, color: const Color(0xFFF9D8E6), letterSpacing: 1.0)),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(20),
                ),
                padding: const EdgeInsets.all(2),
                child: Row(
                  children: [
                    _toggleBtn('Personal', !isGroup, () => setState(() => _activeSummary = 'personal')),
                    _toggleBtn('Group', isGroup, () => setState(() => _activeSummary = 'groups')),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            isGroup ? 'My total group savings' : 'Total personal savings',
            style: PayPinkTheme.body(fontSize: 12, color: const Color(0xFFF3C4D8)),
          ),
          const SizedBox(height: 4),
          Text(
            _formatCurrency(total),
            style: PayPinkTheme.display(fontSize: 32, fontWeight: FontWeight.w800, color: Colors.white),
          ),
          const SizedBox(height: 6),
          Text(
            'Across $count ${isGroup ? 'PinkCircles' : 'savings goals'}',
            style: PayPinkTheme.body(fontSize: 12, color: const Color(0xFFE5A9C5)),
          ),
          const SizedBox(height: 14),
          Divider(color: Colors.white.withValues(alpha: 0.15), height: 1),
          const SizedBox(height: 12),
          Row(
            children: [
              const Icon(Icons.stars_rounded, color: Color(0xFFFBBF24), size: 16),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  isGroup
                      ? 'Your contributions only. Every member keeps their own savings.'
                      : 'Small habits compound. Your money stays in your account.',
                  style: PayPinkTheme.body(fontSize: 11, color: const Color(0xFFF7D1E2)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _toggleBtn(String label, bool active, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: active ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: active ? const Color(0xFF7A2048) : Colors.white,
          ),
        ),
      ),
    );
  }

  Widget _buildSegmentTabs(bool isDark, Color ink) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: isDark ? PayPinkTheme.darkCard : const Color(0xFFF3E7ED),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          Expanded(
            child: _tabButton('My Savings', _activeTab == 'personal', () => setState(() => _activeTab = 'personal'), isDark),
          ),
          Expanded(
            child: _tabButton('PinkCircles', _activeTab == 'circles', () => setState(() => _activeTab = 'circles'), isDark),
          ),
        ],
      ),
    );
  }

  Widget _tabButton(String label, bool active, VoidCallback onTap, bool isDark) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: active ? (isDark ? PayPinkTheme.darkPaper : Colors.white) : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
          boxShadow: active
              ? [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.05),
                    blurRadius: 4,
                    offset: const Offset(0, 2),
                  )
                ]
              : null,
        ),
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: active ? FontWeight.w700 : FontWeight.w500,
              color: active ? PayPinkTheme.wine : (isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted),
            ),
          ),
        ),
      ),
    );
  }

  // =========================================================================
  // TAB 1: PERSONAL SAVINGS
  // =========================================================================
  Widget _buildPersonalTab(bool isDark, Color ink, Color muted) {
    final personalGoals = (_overview?.goals ?? []).where((g) => g.circleId == null).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              '${personalGoals.length} Savings Goal${personalGoals.length == 1 ? '' : 's'}',
              style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w700, color: ink),
            ),
            ElevatedButton.icon(
              onPressed: _savingsAccounts.isEmpty ? null : _openCreateGoalDialog,
              icon: const Icon(Icons.add_rounded, size: 16),
              label: const Text('New Goal', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
              style: ElevatedButton.styleFrom(
                backgroundColor: PayPinkTheme.wine,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (personalGoals.isEmpty)
          _buildEmptyCard('Make room for your first goal', 'Choose what you are saving for. You can start at ₱0.', isDark)
        else
          ...personalGoals.map((g) => _buildGoalCard(g, isDark, ink, muted)),
        const SizedBox(height: 20),
        _buildBadgesSection(isDark, ink, muted),
      ],
    );
  }

  Widget _buildGoalCard(SavingsGoalItem goal, bool isDark, Color ink, Color muted) {
    final catInfo = _categories[goal.category] ?? (Icons.star_outline_rounded, 'Other');
    final percent = goal.progressPercent;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? PayPinkTheme.darkCard : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(catInfo.$1, size: 20, color: PayPinkTheme.wine),
                  const SizedBox(width: 8),
                  Text(
                    goal.name,
                    style: PayPinkTheme.display(fontSize: 15, fontWeight: FontWeight.w700, color: ink),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: goal.isCompleted
                      ? (isDark ? const Color(0xFF143823) : const Color(0xFFDCFCE7))
                      : PayPinkTheme.pinkSubtle,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  goal.isCompleted ? 'Completed' : '$percent%',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    color: goal.isCompleted ? const Color(0xFF16A34A) : PayPinkTheme.wine,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _formatCurrency(goal.savedAmount),
                style: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w800, color: ink),
              ),
              Text(
                'of ${_formatCurrency(goal.targetAmount)}',
                style: PayPinkTheme.body(fontSize: 13, color: muted),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: goal.progressRatio,
              minHeight: 8,
              backgroundColor: isDark ? Colors.white.withValues(alpha: 0.1) : const Color(0xFFF3E7ED),
              valueColor: const AlwaysStoppedAnimation<Color>(PayPinkTheme.wineLight),
            ),
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Target: ${goal.targetDate.isNotEmpty ? goal.targetDate.split('T')[0] : 'Open'}',
                style: PayPinkTheme.body(fontSize: 11, color: muted),
              ),
              if (goal.streak > 0)
                Text(
                  '${goal.streak} streak',
                  style: PayPinkTheme.body(fontSize: 11, color: const Color(0xFFEA580C), fontWeight: FontWeight.w700),
                ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => _openFundingModal(goal, 'ALLOCATE'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: PayPinkTheme.wine,
                    side: BorderSide(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(vertical: 8),
                  ),
                  child: const Text('＋ Add money', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                ),
              ),
              const SizedBox(width: 8),
              if (goal.savedAmount > 0) ...[
                OutlinedButton(
                  onPressed: () => _openFundingModal(goal, 'RELEASE'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: muted,
                    side: BorderSide(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  ),
                  child: const Text('Release', style: TextStyle(fontSize: 12)),
                ),
                const SizedBox(width: 8),
              ],
              IconButton(
                onPressed: () => _openScheduleModal(goal),
                tooltip: 'Savings Plan',
                icon: const Icon(Icons.schedule_rounded, size: 20),
                color: PayPinkTheme.wine,
              ),
              IconButton(
                onPressed: () => _openGoalActivityModal(goal),
                tooltip: 'Goal Activity',
                icon: const Icon(Icons.history_rounded, size: 20),
                color: PayPinkTheme.wine,
              ),
            ],
          ),
        ],
      ),
    );
  }

  // =========================================================================
  // TAB 2: PINKCIRCLES
  // =========================================================================
  Widget _buildCirclesTab(bool isDark, Color ink, Color muted) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              '${_circles.length} PinkCircles',
              style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w700, color: ink),
            ),
            ElevatedButton.icon(
              onPressed: _savingsAccounts.isEmpty ? null : _openCreateCircleDialog,
              icon: const Icon(Icons.group_add_rounded, size: 16),
              label: const Text('Create Circle', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
              style: ElevatedButton.styleFrom(
                backgroundColor: PayPinkTheme.wine,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (_circles.isEmpty)
          _buildEmptyCard('No circles yet', 'Save with friends and family. Create a circle and invite another PayPink user.', isDark)
        else
          ..._circles.map((c) => _buildCircleCard(c, isDark, ink, muted)),
      ],
    );
  }

  Widget _buildCircleCard(PinkCircleItem circle, bool isDark, Color ink, Color muted) {
    final activeMembers = circle.members.where((m) => m.status == 'ACTIVE').toList();

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? PayPinkTheme.darkCard : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Text(
                  circle.name,
                  style: PayPinkTheme.display(fontSize: 16, fontWeight: FontWeight.w700, color: ink),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: circle.isInvited ? const Color(0xFFFEF3C7) : PayPinkTheme.pinkSubtle,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  circle.isInvited ? 'Invitation' : '${activeMembers.length} members',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    color: circle.isInvited ? const Color(0xFFD97706) : PayPinkTheme.wine,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (circle.isInvited) ...[
            Text('You have been invited to save together in this circle.', style: PayPinkTheme.body(fontSize: 12, color: muted)),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: () => _acceptCircleInvitation(circle),
              style: ElevatedButton.styleFrom(
                backgroundColor: PayPinkTheme.wine,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              child: const Text('Accept invitation', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
            ),
          ] else ...[
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  _formatCurrency(circle.savedAmount),
                  style: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w800, color: ink),
                ),
                Text(
                  'of ${_formatCurrency(circle.targetAmount)}',
                  style: PayPinkTheme.body(fontSize: 13, color: muted),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: circle.progressRatio,
                minHeight: 8,
                backgroundColor: isDark ? Colors.white.withValues(alpha: 0.1) : const Color(0xFFF3E7ED),
                valueColor: const AlwaysStoppedAnimation<Color>(PayPinkTheme.wineLight),
              ),
            ),
            const SizedBox(height: 12),
            Text('Members:', style: PayPinkTheme.display(fontSize: 12, fontWeight: FontWeight.w700, color: muted)),
            const SizedBox(height: 6),
            ...circle.members.map((m) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${m.fullName}${m.isYou ? ' (You)' : ''}',
                        style: PayPinkTheme.body(fontSize: 12, color: ink, fontWeight: m.isYou ? FontWeight.w700 : FontWeight.w500),
                      ),
                      Text(
                        m.savedAmount != null ? _formatCurrency(m.savedAmount!) : (m.status == 'INVITED' ? 'Invited' : 'Hidden'),
                        style: PayPinkTheme.body(fontSize: 12, color: muted),
                      ),
                    ],
                  ),
                )),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => _openInviteModal(circle),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: PayPinkTheme.wine,
                      side: BorderSide(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                      padding: const EdgeInsets.symmetric(vertical: 8),
                    ),
                    child: const Text('＋ Invite member', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildEmptyCard(String title, String subtitle, bool isDark) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: isDark ? PayPinkTheme.darkCard : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
      ),
      child: Center(
        child: Column(
          children: [
            const Icon(Icons.savings_outlined, color: PayPinkTheme.wineLight, size: 36),
            const SizedBox(height: 10),
            Text(title, style: PayPinkTheme.display(fontSize: 15, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink)),
            const SizedBox(height: 4),
            Text(subtitle, style: PayPinkTheme.body(fontSize: 12, color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted), textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }

  Widget _buildBadgesSection(bool isDark, Color ink, Color muted) {
    final personalTotal = _overview?.personalTotal ?? 0.0;
    final total = personalTotal + (_overview?.groupTotal ?? 0.0);
    final has1k = total >= 1000;
    final hasEmergency = (_overview?.goals ?? []).any((g) => g.category == 'EMERGENCY' && g.savedAmount >= 100000);
    final hasStreak = (_overview?.goals ?? []).any((g) => g.streak >= 4);
    final hasMillion = personalTotal >= 1000000;

    final badges = [
      (Icons.savings_outlined, 'First ₱1K', has1k, 'Save your first ₱1,000 across your goals.'),
      (Icons.shield_outlined, 'Emergency Era', hasEmergency, 'Build a ₱100,000 emergency cushion.'),
      (Icons.verified_outlined, 'Consistency Queen', hasStreak, 'Complete 4 scheduled contributions in a row.'),
      (Icons.diamond_outlined, 'Million Club', hasMillion, 'Reach ₱1,000,000 in personal savings.'),
    ];

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? PayPinkTheme.darkCard : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Hall of Fame', style: PayPinkTheme.display(fontSize: 14, fontWeight: FontWeight.w700, color: ink)),
            ],
          ),
          const SizedBox(height: 4),
          Text('Good habits deserve celebration.', style: PayPinkTheme.body(fontSize: 11, color: muted)),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: badges.map((b) {
              return Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: b.$3 ? PayPinkTheme.pinkSubtle : (isDark ? Colors.white.withValues(alpha: 0.05) : Colors.grey.shade100),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: b.$3 ? PayPinkTheme.pink : Colors.transparent),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(b.$1, size: 14, color: b.$3 ? PayPinkTheme.wine : muted),
                    const SizedBox(width: 4),
                    Text(
                      b.$2,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: b.$3 ? PayPinkTheme.wine : muted,
                      ),
                    ),
                    if (b.$3) ...[
                      const SizedBox(width: 4),
                      const Icon(Icons.check_rounded, size: 12, color: Color(0xFF16A34A)),
                    ],
                  ],
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }

  // =========================================================================
  // MODALS & DIALOGS
  // =========================================================================
  Future<void> _openFundingModal(SavingsGoalItem goal, String type) async {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isAllocate = type == 'ALLOCATE';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (sheetCtx) {
        return FutureBuilder<SavingsFundingDetails?>(
          future: isAllocate ? SavingsService.checkFunding(goal.goalId) : null,
          builder: (context, snapshot) {
            if (isAllocate && snapshot.connectionState == ConnectionState.waiting) {
              return const SizedBox(
                height: 250,
                child: Center(child: CircularProgressIndicator(color: PayPinkTheme.wine)),
              );
            }

            final funding = snapshot.data;
            final maxAmount = isAllocate
                ? (funding?.maximumContribution ?? 0.0)
                : goal.savedAmount;

            final amountCtrl = TextEditingController();

            return Padding(
              padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(sheetCtx).viewInsets.bottom + 20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    isAllocate ? 'Set Aside Money' : 'Release Money',
                    style: PayPinkTheme.display(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(goal.name, style: PayPinkTheme.body(fontSize: 12, color: PayPinkTheme.muted)),
                  const SizedBox(height: 14),
                  if (isAllocate && funding != null) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: isDark ? PayPinkTheme.darkCard : const Color(0xFFFDF2F8),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Column(
                        children: [
                          _statRow('Account Available', _formatCurrency(funding.availableBalance), isDark),
                          const SizedBox(height: 4),
                          _statRow('Remaining Goal Target', _formatCurrency(funding.remainingTarget), isDark),
                          const SizedBox(height: 4),
                          _statRow('Max you can add', _formatCurrency(funding.maximumContribution), isDark, highlight: true),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                  ],
                  TextField(
                    controller: amountCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      labelText: 'Amount (₱)',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      suffixIcon: TextButton(
                        onPressed: () => amountCtrl.text = maxAmount.toStringAsFixed(2),
                        child: const Text('MAX', style: TextStyle(fontWeight: FontWeight.bold, color: PayPinkTheme.wine)),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: ElevatedButton(
                      onPressed: () async {
                        final val = double.tryParse(amountCtrl.text) ?? 0.0;
                        if (val <= 0 || val > maxAmount) {
                          _showNotice('Please enter a valid amount up to ${_formatCurrency(maxAmount)}');
                          return;
                        }
                        Navigator.pop(sheetCtx);
                        final res = await SavingsService.executeOperation(
                          accountId: goal.accountId,
                          goalId: goal.goalId,
                          amount: val,
                          type: type,
                        );
                        _showNotice(res.message);
                        if (res.success) {
                          widget.onChanged?.call();
                          _reload();
                        }
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: PayPinkTheme.wine,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      child: Text(isAllocate ? 'Confirm and set aside' : 'Confirm release',
                          style: const TextStyle(fontWeight: FontWeight.w700)),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _statRow(String label, String value, bool isDark, {bool highlight = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: TextStyle(fontSize: 12, color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted)),
        Text(
          value,
          style: TextStyle(
            fontSize: 12,
            fontWeight: highlight ? FontWeight.w800 : FontWeight.w600,
            color: highlight ? PayPinkTheme.wine : (isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink),
          ),
        ),
      ],
    );
  }

  void _openCreateGoalDialog() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final nameCtrl = TextEditingController();
    final targetCtrl = TextEditingController();
    final initialCtrl = TextEditingController();
    String selectedCategory = 'EMERGENCY';
    BankAccount selectedAccount = _savingsAccounts.first;
    DateTime selectedDate = DateTime.now().add(const Duration(days: 90));

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (sheetCtx) {
        return StatefulBuilder(
          builder: (ctx, setModalState) {
            return Padding(
              padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(sheetCtx).viewInsets.bottom + 20),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('New Savings Goal',
                        style: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink)),
                    const SizedBox(height: 12),
                    TextField(
                      controller: nameCtrl,
                      decoration: InputDecoration(
                        labelText: 'Goal Name',
                        hintText: 'e.g. Travel to Japan',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<String>(
                      value: selectedCategory,
                      decoration: InputDecoration(
                        labelText: 'Category',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      items: _categories.entries.map((e) {
                        return DropdownMenuItem(
                          value: e.key,
                          child: Row(
                            children: [
                              Icon(e.value.$1, size: 18, color: PayPinkTheme.wine),
                              const SizedBox(width: 8),
                              Text(e.value.$2),
                            ],
                          ),
                        );
                      }).toList(),
                      onChanged: (v) => setModalState(() => selectedCategory = v ?? 'OTHER'),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: targetCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: InputDecoration(
                        labelText: 'Target Amount (₱)',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: initialCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: InputDecoration(
                        labelText: 'Initial Amount to set aside (₱)',
                        hintText: 'Optional (e.g. 500.00)',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                    const SizedBox(height: 16),
                    SizedBox(
                      width: double.infinity,
                      height: 46,
                      child: ElevatedButton(
                        onPressed: () async {
                          final name = nameCtrl.text.trim();
                          final target = double.tryParse(targetCtrl.text) ?? 0.0;
                          final initial = double.tryParse(initialCtrl.text) ?? 0.0;
                          if (name.isEmpty || target <= 0) {
                            _showNotice('Please enter a goal name and target amount.');
                            return;
                          }
                          Navigator.pop(sheetCtx);
                          final res = await SavingsService.createGoal(
                            accountId: selectedAccount.accountId,
                            name: name,
                            category: selectedCategory,
                            target: target,
                            targetDate: selectedDate.toIso8601String().split('T')[0],
                            initialAmount: initial,
                          );
                          _showNotice(res.message);
                          if (res.success) {
                            widget.onChanged?.call();
                            _reload();
                          }
                        },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: PayPinkTheme.wine,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                        child: const Text('Create savings goal', style: TextStyle(fontWeight: FontWeight.w700)),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  void _openCreateCircleDialog() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final nameCtrl = TextEditingController();
    final targetCtrl = TextEditingController();
    final myTargetCtrl = TextEditingController();
    BankAccount selectedAccount = _savingsAccounts.first;
    DateTime selectedDate = DateTime.now().add(const Duration(days: 180));

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (sheetCtx) {
        return Padding(
          padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(sheetCtx).viewInsets.bottom + 20),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Create PinkCircle',
                    style: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink)),
                const SizedBox(height: 12),
                TextField(
                  controller: nameCtrl,
                  decoration: InputDecoration(
                    labelText: 'Circle Name',
                    hintText: 'e.g. Boracay Squad Trip',
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: targetCtrl,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: InputDecoration(
                    labelText: 'Group Target Amount (₱)',
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: myTargetCtrl,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: InputDecoration(
                    labelText: 'Your Personal Target (₱)',
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton(
                    onPressed: () async {
                      final name = nameCtrl.text.trim();
                      final target = double.tryParse(targetCtrl.text) ?? 0.0;
                      final myTarget = double.tryParse(myTargetCtrl.text) ?? 0.0;
                      if (name.isEmpty || target <= 0 || myTarget <= 0) {
                        _showNotice('Please fill in all target amounts.');
                        return;
                      }
                      Navigator.pop(sheetCtx);
                      final res = await SavingsService.createCircle(
                        accountId: selectedAccount.accountId,
                        name: name,
                        target: target,
                        targetDate: selectedDate.toIso8601String().split('T')[0],
                        myTarget: myTarget,
                      );
                      _showNotice(res.message);
                      if (res.success) {
                        widget.onChanged?.call();
                        _reload();
                      }
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: PayPinkTheme.wine,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: const Text('Create PinkCircle', style: TextStyle(fontWeight: FontWeight.w700)),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _openInviteModal(PinkCircleItem circle) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final usernameCtrl = TextEditingController();
    final targetCtrl = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (sheetCtx) {
        return Padding(
          padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(sheetCtx).viewInsets.bottom + 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Invite to PinkCircle',
                  style: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink)),
              const SizedBox(height: 12),
              TextField(
                controller: usernameCtrl,
                decoration: InputDecoration(
                  labelText: 'PayPink Username',
                  hintText: 'e.g. arosales',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: targetCtrl,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: 'Suggested Member Target (₱)',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                height: 46,
                child: ElevatedButton(
                  onPressed: () async {
                    final username = usernameCtrl.text.trim();
                    final target = double.tryParse(targetCtrl.text) ?? 0.0;
                    if (username.isEmpty || target <= 0) {
                      _showNotice('Please fill in username and target.');
                      return;
                    }
                    Navigator.pop(sheetCtx);
                    final res = await SavingsService.inviteMember(
                      circleId: circle.circleId,
                      username: username,
                      target: target,
                    );
                    _showNotice(res.message);
                    if (res.success) _reload();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: PayPinkTheme.wine,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  child: const Text('Send invitation', style: TextStyle(fontWeight: FontWeight.w700)),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _acceptCircleInvitation(PinkCircleItem circle) async {
    if (_savingsAccounts.isEmpty) {
      _showNotice('An active PHP savings account is needed to join a PinkCircle.');
      return;
    }
    final res = await SavingsService.acceptInvitation(
      circleId: circle.circleId,
      accountId: _savingsAccounts.first.accountId,
    );
    _showNotice(res.message);
    if (res.success) {
      widget.onChanged?.call();
      _reload();
    }
  }

  void _openScheduleModal(SavingsGoalItem goal) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final sched = goal.schedule;
    final amountCtrl = TextEditingController(text: sched?.amount.toStringAsFixed(2) ?? '500.00');
    bool enabled = sched?.enabled ?? true;
    String frequency = sched?.frequency ?? 'MONTHLY';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (sheetCtx) {
        return StatefulBuilder(
          builder: (ctx, setModalState) {
            return Padding(
              padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(sheetCtx).viewInsets.bottom + 20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Savings Plan',
                      style: PayPinkTheme.display(fontSize: 18, fontWeight: FontWeight.w700, color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink)),
                  const SizedBox(height: 12),
                  SwitchListTile(
                    value: enabled,
                    onChanged: (v) => setModalState(() => enabled = v),
                    title: const Text('Enable automatic contributions', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                    activeColor: PayPinkTheme.wineLight,
                    contentPadding: EdgeInsets.zero,
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: amountCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      labelText: 'Contribution Amount (₱)',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    value: frequency,
                    decoration: InputDecoration(
                      labelText: 'Frequency',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'WEEKLY', child: Text('Weekly')),
                      DropdownMenuItem(value: 'MONTHLY', child: Text('Monthly')),
                      DropdownMenuItem(value: 'PAYDAY', child: Text('Payday (15th & Month-End)')),
                    ],
                    onChanged: (v) => setModalState(() => frequency = v ?? 'MONTHLY'),
                  ),
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: ElevatedButton(
                      onPressed: () async {
                        final amt = double.tryParse(amountCtrl.text) ?? 0.0;
                        if (amt <= 0) {
                          _showNotice('Please enter a valid amount.');
                          return;
                        }
                        Navigator.pop(sheetCtx);
                        final res = await SavingsService.updateSchedule(
                          goalId: goal.goalId,
                          amount: amt,
                          frequency: frequency,
                          nextDue: DateTime.now().add(const Duration(days: 7)).toIso8601String().split('T')[0],
                          enabled: enabled,
                        );
                        _showNotice(res.message);
                        if (res.success) _reload();
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: PayPinkTheme.wine,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      child: const Text('Save plan', style: TextStyle(fontWeight: FontWeight.w700)),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  void _openGoalActivityModal(SavingsGoalItem goal) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: isDark ? PayPinkTheme.darkPaper : Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (sheetCtx) {
        return FutureBuilder<List<SavingsActivityItem>>(
          future: SavingsService.fetchActivity(goalId: goal.goalId),
          builder: (ctx, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const SizedBox(
                height: 250,
                child: Center(child: CircularProgressIndicator(color: PayPinkTheme.wine)),
              );
            }
            final list = snapshot.data ?? [];

            return Container(
              constraints: BoxConstraints(maxHeight: MediaQuery.of(sheetCtx).size.height * 0.7),
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Savings Activity',
                              style: PayPinkTheme.display(
                                fontSize: 18,
                                fontWeight: FontWeight.w700,
                                color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              goal.name,
                              style: PayPinkTheme.body(
                                fontSize: 12,
                                color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        onPressed: () => Navigator.pop(sheetCtx),
                        icon: const Icon(Icons.close_rounded),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'From your latest 100 savings operations. Times shown in Manila (PHT).',
                    style: PayPinkTheme.body(
                      fontSize: 11,
                      color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (list.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 36),
                      child: Center(
                        child: Text(
                          'No recent activity for this goal.',
                          style: PayPinkTheme.body(
                            fontSize: 13,
                            color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                          ),
                        ),
                      ),
                    )
                  else
                    Flexible(
                      child: ListView.separated(
                        shrinkWrap: true,
                        itemCount: list.length,
                        separatorBuilder: (_, __) => Divider(
                          color: isDark ? PayPinkTheme.darkLine : PayPinkTheme.line,
                          height: 1,
                        ),
                        itemBuilder: (context, idx) {
                          final item = list[idx];
                          final isAllocate = item.isAllocate;
                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            child: Row(
                              children: [
                                Container(
                                  width: 36,
                                  height: 36,
                                  decoration: BoxDecoration(
                                    color: isAllocate
                                        ? (isDark ? const Color(0xFF381525) : PayPinkTheme.pinkSubtle)
                                        : (isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg),
                                    shape: BoxShape.circle,
                                  ),
                                  child: Icon(
                                    isAllocate ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded,
                                    color: isAllocate
                                        ? (isDark ? const Color(0xFFF6A4C0) : PayPinkTheme.wine)
                                        : (isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green),
                                    size: 16,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        isAllocate ? 'Set aside for Goal' : 'Released from Goal',
                                        style: TextStyle(
                                          fontSize: 13,
                                          fontWeight: FontWeight.w700,
                                          color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink,
                                        ),
                                      ),
                                      const SizedBox(height: 2),
                                      Text(
                                        _formatActivityTime(item.createdAt),
                                        style: TextStyle(
                                          fontSize: 11,
                                          color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                                        ),
                                      ),
                                      Text(
                                        'Ref: ${item.operationId}',
                                        style: TextStyle(
                                          fontSize: 9,
                                          color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                                          fontFamily: 'monospace',
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    Text(
                                      '${isAllocate ? '-' : '+'}₱${item.amount.toStringAsFixed(2)}',
                                      style: TextStyle(
                                        fontSize: 14,
                                        fontWeight: FontWeight.w800,
                                        color: isAllocate
                                            ? (isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink)
                                            : (isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green),
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: item.status == 'CONFIRMED'
                                            ? (isDark ? const Color(0xFF143823) : PayPinkTheme.greenBg)
                                            : PayPinkTheme.pinkSubtle,
                                        borderRadius: BorderRadius.circular(4),
                                      ),
                                      child: Text(
                                        item.status,
                                        style: TextStyle(
                                          fontSize: 9,
                                          fontWeight: FontWeight.w700,
                                          color: item.status == 'CONFIRMED'
                                              ? (isDark ? const Color(0xFF4ADE80) : PayPinkTheme.green)
                                              : PayPinkTheme.wine,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  String _formatActivityTime(DateTime dt) {
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final hour = dt.hour > 12 ? dt.hour - 12 : (dt.hour == 0 ? 12 : dt.hour);
    final ampm = dt.hour >= 12 ? 'PM' : 'AM';
    final timeStr = '${hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')} $ampm';
    return '${months[dt.month - 1]} ${dt.day}, ${dt.year} · $timeStr PHT';
  }
}
