import 'package:flutter/material.dart';
import '../theme/paypink_theme.dart';

class PromoItem {
  final String id;
  final String tag;
  final String title;
  final String body;
  final String actionLabel;
  final String? imageAsset;
  final List<Color> lightGradient;
  final List<Color> darkGradient;
  final IconData icon;

  const PromoItem({
    required this.id,
    required this.tag,
    required this.title,
    required this.body,
    required this.actionLabel,
    this.imageAsset,
    required this.lightGradient,
    required this.darkGradient,
    required this.icon,
  });
}

class PromoBanner extends StatefulWidget {
  final bool isDark;
  final VoidCallback onAction;

  const PromoBanner({
    super.key,
    required this.isDark,
    required this.onAction,
  });

  @override
  State<PromoBanner> createState() => _PromoBannerState();
}

class _PromoBannerState extends State<PromoBanner> {
  bool _isVisible = true;
  int _activePromoIndex = 0;
  final PageController _pageController = PageController(viewportFraction: 0.92);

  static const List<PromoItem> _promos = [
    PromoItem(
      id: 'promo_premier_card',
      tag: 'NEW PREMIER CARD',
      title: 'PayPink Premier Card',
      body: 'Zero foreign exchange fees and up to 2.5% cashback on everyday spending.',
      actionLabel: 'Explore Card',
      imageAsset: 'assets/images/paypink_promo_banner.jpg',
      lightGradient: [Color(0xFF4A1028), Color(0xFF220512)],
      darkGradient: [Color(0xFF2A0817), Color(0xFF14020B)],
      icon: Icons.credit_card_rounded,
    ),
    PromoItem(
      id: 'promo_savings',
      tag: 'GROW WEALTH',
      title: 'High-Yield Savings · 6.50% p.a.',
      body: 'Earn daily interest on all deposits with instant withdrawal and zero lock-in.',
      actionLabel: 'See Rates',
      imageAsset: null,
      lightGradient: [Color(0xFFFFF0F5), Color(0xFFFBE4ED)],
      darkGradient: [Color(0xFF381023), Color(0xFF1F0813)],
      icon: Icons.trending_up_rounded,
    ),
    PromoItem(
      id: 'promo_remittance',
      tag: 'INSTANT TRANSFER',
      title: 'Send Locally with Zero Fees',
      body: 'Real-time Instapay and PESONet transfers directly to any Philippine bank or e-wallet.',
      actionLabel: 'Transfer Now',
      imageAsset: null,
      lightGradient: [Color(0xFFF4F7FB), Color(0xFFE9F0FA)],
      darkGradient: [Color(0xFF152238), Color(0xFF0D1422)],
      icon: Icons.bolt_rounded,
    ),
  ];

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_isVisible) return const SizedBox.shrink();

    final isDark = widget.isDark;

    return AnimatedSize(
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeInOut,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Section Header & Controls
            Padding(
              padding: const EdgeInsets.only(left: 4, right: 4, bottom: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 7,
                        height: 7,
                        decoration: const BoxDecoration(
                          color: PayPinkTheme.wine,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        'Offers & Highlights',
                        style: PayPinkTheme.display(
                          fontSize: 13,
                          fontWeight: FontWeight.w800,
                          color: isDark ? PayPinkTheme.darkInk : PayPinkTheme.ink,
                          letterSpacing: -0.2,
                        ),
                      ),
                    ],
                  ),
                  IconButton(
                    icon: Icon(
                      Icons.close_rounded,
                      size: 15,
                      color: isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted,
                    ),
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                    splashRadius: 14,
                    tooltip: 'Dismiss',
                    onPressed: () => setState(() => _isVisible = false),
                  ),
                ],
              ),
            ),

            // Banner Advertisement Carousel Card
            SizedBox(
              height: 160,
              child: PageView.builder(
                controller: _pageController,
                itemCount: _promos.length,
                onPageChanged: (i) => setState(() => _activePromoIndex = i),
                itemBuilder: (context, index) {
                  final promo = _promos[index];
                  final hasImage = promo.imageAsset != null;
                  final gradientColors = isDark ? promo.darkGradient : promo.lightGradient;

                  return Container(
                    margin: const EdgeInsets.symmetric(horizontal: 4),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(20),
                      gradient: LinearGradient(
                        colors: gradientColors,
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      border: Border.all(
                        color: isDark
                            ? PayPinkTheme.wineLight.withValues(alpha: 0.35)
                            : (hasImage
                                ? PayPinkTheme.wineLight.withValues(alpha: 0.3)
                                : PayPinkTheme.pink.withValues(alpha: 0.5)),
                        width: 1.2,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: PayPinkTheme.wine.withValues(alpha: isDark ? 0.22 : 0.08),
                          blurRadius: 14,
                          offset: const Offset(0, 6),
                        ),
                      ],
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(20),
                      child: Stack(
                        children: [
                          // Optional Reflective Campaign Background Image
                          if (hasImage) ...[
                            Positioned.fill(
                              child: Image.asset(
                                promo.imageAsset!,
                                fit: BoxFit.cover,
                                errorBuilder: (ctx, err, stack) => const SizedBox.shrink(),
                              ),
                            ),
                            // Luxury Scrim Gradient Overlay for Maximum Readability
                            Positioned.fill(
                              child: DecoratedBox(
                                decoration: BoxDecoration(
                                  gradient: LinearGradient(
                                    begin: Alignment.centerLeft,
                                    end: Alignment.centerRight,
                                    stops: const [0.0, 0.58, 1.0],
                                    colors: [
                                      (isDark ? const Color(0xFF14020B) : const Color(0xFF280616))
                                          .withValues(alpha: 0.94),
                                      (isDark ? const Color(0xFF1E0813) : const Color(0xFF38081F))
                                          .withValues(alpha: 0.78),
                                      Colors.transparent,
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          ],

                          // Ambient Decorative Glow
                          Positioned(
                            top: -24,
                            right: -24,
                            child: Container(
                              width: 90,
                              height: 90,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: (hasImage ? Colors.white : PayPinkTheme.wineLight)
                                    .withValues(alpha: 0.12),
                              ),
                            ),
                          ),

                          // Banner Content
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                // Badge & Benefit Tag
                                Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                                      decoration: BoxDecoration(
                                        color: (hasImage || isDark)
                                            ? Colors.white.withValues(alpha: 0.20)
                                            : PayPinkTheme.wine,
                                        borderRadius: BorderRadius.circular(6),
                                        border: Border.all(
                                          color: (hasImage || isDark)
                                              ? Colors.white.withValues(alpha: 0.35)
                                              : Colors.transparent,
                                          width: 0.8,
                                        ),
                                      ),
                                      child: Text(
                                        promo.tag,
                                        style: PayPinkTheme.eyebrow(
                                          fontSize: 10,
                                          fontWeight: FontWeight.w800,
                                          color: (hasImage || isDark) ? Colors.white : Colors.white,
                                          letterSpacing: 0.5,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 6),

                                // Title
                                Text(
                                  promo.title,
                                  style: PayPinkTheme.display(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w800,
                                    color: (hasImage || isDark) ? Colors.white : PayPinkTheme.ink,
                                    letterSpacing: -0.3,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                const SizedBox(height: 3),

                                // Body
                                Expanded(
                                  child: Text(
                                    promo.body,
                                    style: PayPinkTheme.body(
                                      fontSize: 11,
                                      color: (hasImage || isDark)
                                          ? Colors.white.withValues(alpha: 0.84)
                                          : (isDark ? PayPinkTheme.darkMuted : PayPinkTheme.muted),
                                      height: 1.35,
                                    ),
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),

                                // Interactive Pill CTA
                                GestureDetector(
                                  onTap: widget.onAction,
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                    decoration: BoxDecoration(
                                      color: (hasImage || isDark)
                                          ? Colors.white
                                          : PayPinkTheme.wine,
                                      borderRadius: BorderRadius.circular(20),
                                      boxShadow: [
                                        BoxShadow(
                                          color: Colors.black.withValues(alpha: 0.15),
                                          blurRadius: 4,
                                          offset: const Offset(0, 2),
                                        ),
                                      ],
                                    ),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        Text(
                                          promo.actionLabel,
                                          style: PayPinkTheme.display(
                                            fontSize: 11,
                                            fontWeight: FontWeight.w800,
                                            color: (hasImage || isDark)
                                                ? PayPinkTheme.wine
                                                : Colors.white,
                                          ),
                                        ),
                                        const SizedBox(width: 4),
                                        Icon(
                                          Icons.arrow_forward_rounded,
                                          size: 11,
                                          color: (hasImage || isDark)
                                              ? PayPinkTheme.wine
                                              : Colors.white,
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),

            // Pagination Dots
            const SizedBox(height: 6),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(_promos.length, (i) {
                final isSelected = _activePromoIndex == i;
                return AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  margin: const EdgeInsets.symmetric(horizontal: 2.5),
                  height: 4,
                  width: isSelected ? 16 : 5,
                  decoration: BoxDecoration(
                    color: isSelected
                        ? PayPinkTheme.wine
                        : (isDark ? Colors.white24 : Colors.black12),
                    borderRadius: BorderRadius.circular(3),
                  ),
                );
              }),
            ),
          ],
        ),
      ),
    );
  }
}
