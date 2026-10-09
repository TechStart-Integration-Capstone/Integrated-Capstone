import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/paypink_theme.dart';
import '../services/account_service.dart';

/// Card scheme type for realistic card face branding
enum CardScheme {
  mastercard,
  visa,
}

/// Rich client card model that wraps a BankAccount with physical card properties
class PayPinkCardModel {
  final BankAccount account;
  final String cardHolder;
  final String maskedNumber;
  final String expiry;
  final CardScheme scheme;
  final bool isFrozen;

  PayPinkCardModel({
    required this.account,
    required this.cardHolder,
    required this.maskedNumber,
    required this.expiry,
    required this.scheme,
    this.isFrozen = false,
  });

  String get last4 => account.last4;
  String get productTitle {
    final type = account.accountType.toUpperCase();
    if (type.contains('SAVING')) return 'High-Yield Savings';
    if (type.contains('LOAN')) return 'Personal Loan';
    return 'Everyday Checking';
  }

  PayPinkCardModel copyWith({bool? isFrozen}) {
    return PayPinkCardModel(
      account: account,
      cardHolder: cardHolder,
      maskedNumber: maskedNumber,
      expiry: expiry,
      scheme: scheme,
      isFrozen: isFrozen ?? this.isFrozen,
    );
  }
}

/// The physical portrait card face.
///
/// Proportions: Aspect Ratio 0.66 (height = width / 0.66).
/// Features:
/// - Signature PayPink velvet multi-stop gradient fall (wine/plum/rose-gold)
/// - Top-left radial rose bloom and right-side ambient glow
/// - Dynamic specular highlight (sheen) that travels across the face during swipe
/// - Milled inner edge hairline
/// - Golden micro-chip with contact traces
/// - Stylized PayPink vector watermark
/// - Overlapping Mastercard circles or Visa plate
/// - Frozen frost-veil overlay with status badge
class PayPinkCardFace extends StatelessWidget {
  final PayPinkCardModel card;
  final double sheen;
  final double lift;
  final double radius;
  final bool hideBalance;

  const PayPinkCardFace({
    super.key,
    required this.card,
    this.sheen = 0.0,
    this.lift = 1.0,
    this.radius = 20.0,
    this.hideBalance = false,
  });

  static const double aspectRatio = 0.66;
  static double heightFor(double width) => width / aspectRatio;

  @override
  Widget build(BuildContext context) {
    final border = BorderRadius.circular(radius);
    final isSavings = card.account.accountType.toUpperCase().contains('SAVING');
    final isLoan = card.account.accountType.toUpperCase().contains('LOAN');

    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: border,
        boxShadow: [
          BoxShadow(
            color: isLoan
                ? const Color(0xFF8A2754).withValues(alpha: 0.35 * lift)
                : (isSavings
                    ? const Color(0xFF1E1428).withValues(alpha: 0.40 * lift)
                    : PayPinkTheme.wine.withValues(alpha: 0.38 * lift)),
            blurRadius: 30 * lift,
            spreadRadius: -2,
            offset: Offset(0, 16 * lift),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: border,
        child: CustomPaint(
          painter: _PayPinkCardFacePainter(
            sheen: sheen,
            isFrozen: card.isFrozen,
            accountType: card.account.accountType,
          ),
          child: Stack(
            fit: StackFit.passthrough,
            children: [
              // Milled hairline edge
              Positioned.fill(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    borderRadius: border,
                    border: Border.all(
                      color: Colors.white.withValues(alpha: 0.16),
                      width: 1.0,
                    ),
                  ),
                ),
              ),

              LayoutBuilder(
                builder: (context, constraints) {
                  final pad = constraints.maxWidth * 0.075;
                  return Padding(
                    padding: EdgeInsets.all(pad),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Top Header: Masked Last 4 & Status Tag
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Flexible(
                              child: Text(
                                '••••  ${card.last4}',
                                style: PayPinkTheme.mono(
                                  fontSize: 13,
                                  fontWeight: FontWeight.w700,
                                  color: Colors.white.withValues(alpha: 0.88),
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            const SizedBox(width: 8),
                            if (card.isFrozen)
                              const _FrozenBadge()
                            else
                              const _ContactlessIcon(),
                          ],
                        ),

                        const SizedBox(height: 12),

                        // Metallic Smart Chip
                        _SmartChip(width: constraints.maxWidth * 0.17),

                        // Clean Spacing without watermark letter P
                        const Spacer(),

                        // Card Holder Name & Balance Hint
                        Text(
                          card.cardHolder.toUpperCase(),
                          style: PayPinkTheme.display(
                            fontSize: 10.5,
                            fontWeight: FontWeight.w800,
                            color: Colors.white.withValues(alpha: 0.75),
                            letterSpacing: 1.2,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 12),

                        // Bottom Row: Network Scheme Mark & Product Label
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            _NetworkSchemeMark(
                              scheme: card.scheme,
                              width: (constraints.maxWidth * 0.22).clamp(28.0, 48.0),
                            ),
                            const SizedBox(width: 8),
                            Flexible(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.end,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    card.productTitle,
                                    style: PayPinkTheme.display(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w800,
                                      color: Colors.white,
                                      letterSpacing: 0.2,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    'EXP ${card.expiry}',
                                    style: PayPinkTheme.mono(
                                      fontSize: 9,
                                      fontWeight: FontWeight.w600,
                                      color: Colors.white.withValues(alpha: 0.60),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Card Face Painter with multi-stop gradients, light-bloom, and dynamic specular sheen
class _PayPinkCardFacePainter extends CustomPainter {
  final double sheen;
  final bool isFrozen;
  final String accountType;

  const _PayPinkCardFacePainter({
    required this.sheen,
    required this.isFrozen,
    required this.accountType,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final isSavings = accountType.toUpperCase().contains('SAVING');
    final isLoan = accountType.toUpperCase().contains('LOAN');

    // 1. Signature Multi-stop Gradient Fall
    final List<Color> gradientColors;
    if (isLoan) {
      // Rose Gold Platinum
      gradientColors = const [
        Color(0xFFFDE2E4),
        Color(0xFFE2829F),
        Color(0xFFB83262),
        Color(0xFF8A2754),
        Color(0xFF551633),
        Color(0xFF2B0E1D),
        Color(0xFF10050B),
      ];
    } else if (isSavings) {
      // Midnight Obsidian Plum Vault
      gradientColors = const [
        Color(0xFFFCE7F3),
        Color(0xFFBE185D),
        Color(0xFF6B1D48),
        Color(0xFF3B142B),
        Color(0xFF221122),
        Color(0xFF121622),
        Color(0xFF090B12),
      ];
    } else {
      // Everyday Velvet Wine (Signature PayPink)
      gradientColors = const [
        Color(0xFFFDE8F1),
        Color(0xFFE11D48),
        Color(0xFFBE185D),
        Color(0xFF8A2754),
        Color(0xFF651C3E),
        Color(0xFF47142C),
        Color(0xFF180611),
      ];
    }

    const stops = [0.0, 0.07, 0.18, 0.32, 0.48, 0.70, 1.0];

    canvas.drawRect(
      rect,
      Paint()
        ..shader = ui.Gradient.linear(
          rect.topCenter,
          rect.bottomCenter,
          gradientColors,
          stops,
        ),
    );

    // 2. Rose bloom off the top-left corner
    canvas.drawRect(
      rect,
      Paint()
        ..shader = ui.Gradient.radial(
          Offset(size.width * 0.15, -size.height * 0.05),
          size.width * 1.1,
          [
            Colors.white.withValues(alpha: 0.45),
            const Color(0xFFF7D6E3).withValues(alpha: 0.15),
            Colors.transparent,
          ],
          const [0.0, 0.45, 1.0],
        ),
    );

    // 3. Ambient warm glow entering from upper-right
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height * 0.55),
      Paint()
        ..shader = ui.Gradient.radial(
          Offset(size.width * 1.05, size.height * 0.15),
          size.width * 0.85,
          [
            const Color(0xFFBE185D).withValues(alpha: 0.40),
            const Color(0xFF651C3E).withValues(alpha: 0.15),
            Colors.transparent,
          ],
          const [0.0, 0.55, 1.0],
        ),
    );

    // 4. Bottom deep vignette to ground typography
    canvas.drawRect(
      rect,
      Paint()
        ..shader = ui.Gradient.linear(
          rect.center,
          rect.bottomCenter,
          [
            Colors.transparent,
            Colors.black.withValues(alpha: 0.65),
          ],
        ),
    );

    // 5. Specular highlight beam tracking swipe offset
    final x = sheen * 0.95;
    canvas.drawRect(
      rect,
      Paint()
        ..shader = ui.Gradient.linear(
          Offset(size.width * (x - 0.5), 0),
          Offset(size.width * (x + 1.2), size.height),
          [
            Colors.white.withValues(alpha: 0.0),
            Colors.white.withValues(alpha: 0.18),
            Colors.white.withValues(alpha: 0.0),
          ],
          const [0.32, 0.50, 0.68],
        ),
    );

    // 6. Frost veil overlay if card is frozen
    if (isFrozen) {
      canvas.drawRect(
        rect,
        Paint()..color = const Color(0xFFBFDBFE).withValues(alpha: 0.22),
      );
    }
  }

  @override
  bool shouldRepaint(covariant _PayPinkCardFacePainter oldDelegate) =>
      oldDelegate.sheen != sheen ||
      oldDelegate.isFrozen != isFrozen ||
      oldDelegate.accountType != accountType;
}

/// Metallic SIM chip with golden micro-traces
class _SmartChip extends StatelessWidget {
  final double width;
  const _SmartChip({required this.width});

  @override
  Widget build(BuildContext context) {
    final height = width * 0.78;
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: const Color(0xFFD4AF37),
        borderRadius: BorderRadius.circular(width * 0.18),
        gradient: const LinearGradient(
          colors: [Color(0xFFFFDF79), Color(0xFFB8860B), Color(0xFFD4AF37)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.25),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Stack(
        children: [
          Center(
            child: Container(
              width: width * 0.82,
              height: height * 0.75,
              decoration: BoxDecoration(
                border: Border.all(color: const Color(0xFF8B6508), width: 0.8),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          Positioned(
            left: width * 0.48,
            top: 0,
            bottom: 0,
            child: Container(width: 0.8, color: const Color(0xFF8B6508)),
          ),
        ],
      ),
    );
  }
}

/// Frozen Status Badge
class _FrozenBadge extends StatelessWidget {
  const _FrozenBadge();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
      decoration: BoxDecoration(
        color: const Color(0xFF38BDF8).withValues(alpha: 0.25),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF38BDF8).withValues(alpha: 0.6)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.ac_unit_rounded, size: 10, color: Color(0xFF38BDF8)),
          const SizedBox(width: 4),
          Text(
            'FROZEN',
            style: PayPinkTheme.mono(
              fontSize: 8.5,
              fontWeight: FontWeight.w800,
              color: Colors.white,
            ),
          ),
        ],
      ),
    );
  }
}

/// Contactless wave icon
class _ContactlessIcon extends StatelessWidget {
  const _ContactlessIcon();

  @override
  Widget build(BuildContext context) {
    return Icon(
      Icons.contactless_rounded,
      size: 20,
      color: Colors.white.withValues(alpha: 0.75),
    );
  }
}

/// Network Scheme Mark (Mastercard interlocking circles / Visa plate)
class _NetworkSchemeMark extends StatelessWidget {
  final CardScheme scheme;
  final double width;

  const _NetworkSchemeMark({
    required this.scheme,
    required this.width,
  });

  @override
  Widget build(BuildContext context) {
    final height = width / 1.55;
    if (scheme == CardScheme.visa) {
      return Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(width * 0.14),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.18),
              blurRadius: 3,
              offset: const Offset(0, 1),
            ),
          ],
        ),
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
        child: FittedBox(
          fit: BoxFit.contain,
          child: Text(
            'VISA',
            style: PayPinkTheme.display(
              fontSize: 12,
              fontWeight: FontWeight.w900,
              fontStyle: FontStyle.italic,
              color: const Color(0xFF1A1F71),
              letterSpacing: 0.8,
            ),
          ),
        ),
      );
    }

    // Mastercard overlapping circles
    final r = height / 2;
    return SizedBox(
      width: width,
      height: height,
      child: CustomPaint(
        painter: _MastercardPainter(radius: r),
      ),
    );
  }
}



class _MastercardPainter extends CustomPainter {
  final double radius;
  const _MastercardPainter({required this.radius});

  @override
  void paint(Canvas canvas, Size size) {
    final r = radius;
    final leftCircle = Rect.fromCircle(center: Offset(r, r), radius: r);
    final rightCircle = Rect.fromCircle(center: Offset(size.width - r, r), radius: r);

    // Left Red
    canvas.drawOval(leftCircle, Paint()..color = const Color(0xFFEB001B));
    // Right Yellow
    canvas.drawOval(rightCircle, Paint()..color = const Color(0xFFF79E1B));
    // Overlapping Center Orange
    final intersection = Path.combine(
      PathOperation.intersect,
      Path()..addOval(leftCircle),
      Path()..addOval(rightCircle),
    );
    canvas.drawPath(intersection, Paint()..color = const Color(0xFFFF5F00));
  }

  @override
  bool shouldRepaint(covariant _MastercardPainter oldDelegate) => false;
}

/// Dynamic 3D Card Carousel with real physical card depth, 3D turn tilt,
/// specular sheen, and synchronized hero card details below.
class DynamicCardDeck extends StatefulWidget {
  final List<BankAccount> accounts;
  final String cardHolder;
  final bool hideBalances;
  final VoidCallback onToggleHideBalances;
  final Function(BankAccount account)? onSelectAccount;
  final VoidCallback? onOpenTransfer;
  final VoidCallback? onOpenDetails;
  final bool isDark;

  const DynamicCardDeck({
    super.key,
    required this.accounts,
    required this.cardHolder,
    required this.hideBalances,
    required this.onToggleHideBalances,
    this.onSelectAccount,
    this.onOpenTransfer,
    this.onOpenDetails,
    this.isDark = false,
  });

  static const double viewportFraction = 0.68;
  static const double maxCardWidth = 240;

  @override
  State<DynamicCardDeck> createState() => _DynamicCardDeckState();
}

class _DynamicCardDeckState extends State<DynamicCardDeck> {
  late final PageController _controller;
  late final ValueNotifier<double> _pageNotifier;
  int _currentIndex = 0;
  final Set<int> _frozenAccountIds = {};

  @override
  void initState() {
    super.initState();
    _pageNotifier = ValueNotifier<double>(0.0);
    _controller = PageController(
      initialPage: 0,
      viewportFraction: DynamicCardDeck.viewportFraction,
    )..addListener(_onScroll);
  }

  @override
  void dispose() {
    _controller.removeListener(_onScroll);
    _controller.dispose();
    _pageNotifier.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_controller.hasClients) return;
    final val = _controller.page;
    if (val == null) return;
    _pageNotifier.value = val;

    final settled = val.round().clamp(0, _effectiveAccounts.length - 1);
    if (settled != _currentIndex) {
      HapticFeedback.selectionClick();
      setState(() => _currentIndex = settled);
      if (widget.onSelectAccount != null && settled < _effectiveAccounts.length) {
        widget.onSelectAccount!(_effectiveAccounts[settled]);
      }
    }
  }

  List<BankAccount> get _effectiveAccounts {
    if (widget.accounts.isNotEmpty) return widget.accounts;
    return [
      BankAccount(
        accountId: 1,
        accountNumber: '001396394080',
        accountType: 'EVERYDAY',
        currency: 'PHP',
        currentBalance: 74950.00,
        status: 'ACTIVE',
      ),
      BankAccount(
        accountId: 2,
        accountNumber: '001196394082',
        accountType: 'SAVINGS',
        currency: 'PHP',
        currentBalance: 125050.00,
        status: 'ACTIVE',
      ),
      BankAccount(
        accountId: 3,
        accountNumber: '001996394084',
        accountType: 'LOAN',
        currency: 'PHP',
        currentBalance: 25000.00,
        status: 'ACTIVE',
        outstandingDebt: 25000.00,
        minimumPayment: 2150.00,
      ),
    ];
  }

  void _toggleFreeze(int accountId) {
    HapticFeedback.heavyImpact();
    setState(() {
      if (_frozenAccountIds.contains(accountId)) {
        _frozenAccountIds.remove(accountId);
        _showToast('Card unfrozen and active for transactions.');
      } else {
        _frozenAccountIds.add(accountId);
        _showToast('Card locked and protected against unauthorized charges.');
      }
    });
  }

  void _showToast(String message) {
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: PayPinkTheme.wine,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        content: Text(
          message,
          style: PayPinkTheme.body(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 12),
        ),
      ),
    );
  }

  String _formatAmount(double amount) {
    final parts = amount.toStringAsFixed(2).split('.');
    final integerPart = parts[0];
    final decimalPart = parts[1];
    final reg = RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))');
    final formattedInt = integerPart.replaceAllMapped(reg, (Match m) => '${m[1]},');
    return '$formattedInt.$decimalPart';
  }

  @override
  Widget build(BuildContext context) {
    final accounts = _effectiveAccounts;
    final isDark = widget.isDark;
    final textInk = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;
    final textMuted = isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted;

    return LayoutBuilder(
      builder: (context, constraints) {
        final availWidth = constraints.maxWidth;
        final slotWidth = availWidth * DynamicCardDeck.viewportFraction;
        final cardWidth = (slotWidth - 14).clamp(160.0, DynamicCardDeck.maxCardWidth);
        final cardHeight = PayPinkCardFace.heightFor(cardWidth);

        final activeAccount = accounts[_currentIndex.clamp(0, accounts.length - 1)];
        final isFrozen = _frozenAccountIds.contains(activeAccount.accountId);

        return Column(
          children: [
            // 3D Swipeable Stack
            SizedBox(
              height: cardHeight + 28,
              child: PageView.builder(
                controller: _controller,
                itemCount: accounts.length,
                clipBehavior: Clip.none,
                padEnds: true,
                itemBuilder: (context, index) {
                  final acct = accounts[index];
                  final cardModel = PayPinkCardModel(
                    account: acct,
                    cardHolder: widget.cardHolder.isNotEmpty ? widget.cardHolder : 'PayPink Client',
                    maskedNumber: acct.accountNumber,
                    expiry: '10/29',
                    scheme: index % 2 == 0 ? CardScheme.mastercard : CardScheme.visa,
                    isFrozen: _frozenAccountIds.contains(acct.accountId),
                  );

                  return Center(
                    child: ValueListenableBuilder<double>(
                      valueListenable: _pageNotifier,
                      builder: (context, page, child) {
                        final delta = page - index;
                        final clampedDelta = delta.clamp(-1.5, 1.5);
                        final distance = clampedDelta.abs();
                        final fade = (1.0 - distance * 0.35).clamp(0.25, 1.0);

                        // 3D Matrix Turn Physics
                        final transform = Matrix4.identity()
                          ..setEntry(3, 2, 0.0013)
                          ..rotateY(clampedDelta * 0.88)
                          ..rotateZ(clampedDelta * 0.04);

                        final shrink = 1.0 - (distance * 0.08).clamp(0.0, 0.20);
                        transform.scaleByDouble(shrink, shrink, 1.0, 1.0);

                        return Opacity(
                          opacity: fade,
                          child: Transform.translate(
                            offset: Offset(clampedDelta * 20, distance * 10),
                            child: Transform(
                              alignment: Alignment.center,
                              transform: transform,
                              filterQuality: FilterQuality.medium,
                              child: child,
                            ),
                          ),
                        );
                      },
                      child: SizedBox(
                        width: cardWidth,
                        height: cardHeight,
                        child: ValueListenableBuilder<double>(
                          valueListenable: _pageNotifier,
                          builder: (context, page, _) {
                            final delta = (page - index).clamp(-1.0, 1.0);
                            return PayPinkCardFace(
                              card: cardModel,
                              sheen: delta,
                              lift: (1.0 - delta.abs() * 0.6).clamp(0.25, 1.0),
                              hideBalance: widget.hideBalances,
                            );
                          },
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),

            // Pagination Dots
            if (accounts.length > 1) ...[
              const SizedBox(height: 10),
              ValueListenableBuilder<double>(
                valueListenable: _pageNotifier,
                builder: (context, page, _) {
                  return Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(accounts.length, (i) {
                      final dist = (page - i).abs();
                      final isSelected = dist < 0.5;
                      return AnimatedContainer(
                        duration: const Duration(milliseconds: 200),
                        margin: const EdgeInsets.symmetric(horizontal: 3),
                        height: 5,
                        width: isSelected ? 22 : 6,
                        decoration: BoxDecoration(
                          color: isSelected
                              ? PayPinkTheme.wine
                              : (isDark ? Colors.white24 : Colors.black12),
                          borderRadius: BorderRadius.circular(4),
                        ),
                      );
                    }),
                  );
                },
              ),
            ],

            const SizedBox(height: 18),

            // Card Value Swap: Synchronized figures belonging to the front card
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 240),
              transitionBuilder: (child, anim) => FadeTransition(
                opacity: anim,
                child: SlideTransition(
                  position: Tween<Offset>(begin: const Offset(0, 0.1), end: Offset.zero).animate(anim),
                  child: child,
                ),
              ),
              child: Column(
                key: ValueKey('${activeAccount.accountId}_$isFrozen'),
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        '${activeAccount.accountType} •••• ${activeAccount.last4}',
                        style: PayPinkTheme.body(
                          fontSize: 12,
                          color: textMuted,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                        decoration: BoxDecoration(
                          color: isFrozen
                              ? const Color(0xFF38BDF8).withValues(alpha: 0.15)
                              : PayPinkTheme.greenBg,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 5,
                              height: 5,
                              decoration: BoxDecoration(
                                color: isFrozen ? const Color(0xFF0284C7) : PayPinkTheme.green,
                                shape: BoxShape.circle,
                              ),
                            ),
                            const SizedBox(width: 4),
                            Text(
                              isFrozen ? 'LOCKED' : 'ACTIVE',
                              style: PayPinkTheme.mono(
                                fontSize: 8.5,
                                fontWeight: FontWeight.w800,
                                color: isFrozen ? const Color(0xFF0284C7) : PayPinkTheme.green,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),

                  // Hero Balance Figure
                  GestureDetector(
                    onTap: widget.onToggleHideBalances,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          widget.hideBalances
                              ? '\u20B1 \u2022\u2022\u2022\u2022\u2022\u2022'
                              : '\u20B1${_formatAmount(activeAccount.currentBalance)}',
                          style: PayPinkTheme.display(
                            fontSize: 34,
                            fontWeight: FontWeight.w900,
                            color: textInk,
                            letterSpacing: -1.2,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Icon(
                          widget.hideBalances ? Icons.visibility_off_rounded : Icons.visibility_rounded,
                          size: 18,
                          color: textMuted,
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 18),

                  // Quick Card Action Strip (Details, Freeze/Unfreeze, Transfer)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 24),
                    child: Row(
                      children: [
                        Expanded(
                          child: _CardActionButton(
                            icon: Icons.tune_rounded,
                            label: 'Details',
                            isDark: isDark,
                            onTap: widget.onOpenDetails ?? () {},
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _CardActionButton(
                            icon: isFrozen ? Icons.lock_open_rounded : Icons.ac_unit_rounded,
                            label: isFrozen ? 'Unfreeze' : 'Freeze',
                            isHighlight: isFrozen,
                            isDark: isDark,
                            onTap: () => _toggleFreeze(activeAccount.accountId),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _CardActionButton(
                            icon: Icons.send_rounded,
                            label: 'Pay & Send',
                            isDark: isDark,
                            onTap: widget.onOpenTransfer ?? () {},
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

class _CardActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool isDark;
  final bool isHighlight;

  const _CardActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
    this.isDark = false,
    this.isHighlight = false,
  });

  @override
  Widget build(BuildContext context) {
    final bg = isHighlight
        ? (isDark ? const Color(0xFF1E3A8A) : const Color(0xFFE0F2FE))
        : (isDark ? PayPinkTheme.darkCard : Colors.white);
    final border = isHighlight
        ? const Color(0xFF38BDF8)
        : (isDark ? PayPinkTheme.darkGlassBorder : PayPinkTheme.line);
    final iconColor = isHighlight
        ? const Color(0xFF0284C7)
        : (isDark ? PayPinkTheme.pink : PayPinkTheme.wine);
    final textColor = isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.lightImpact();
          onTap();
        },
        borderRadius: BorderRadius.circular(14),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 11),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: border, width: 1.1),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.04),
                blurRadius: 8,
                offset: const Offset(0, 3),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 18, color: iconColor),
              const SizedBox(height: 5),
              Text(
                label,
                style: PayPinkTheme.body(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: textColor,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

