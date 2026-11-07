import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/widgets.dart';
import 'package:high_tech_entertainment/responsive_layout.dart';

void main() {
  group('High-Tech responsive layout breakpoints', () {
    test('uses compact splash sizing on narrow and short phones', () {
      expect(HteResponsiveLayout.isCompact(const Size(320, 740)), isTrue);
      expect(HteResponsiveLayout.isCompact(const Size(390, 620)), isTrue);
      expect(HteResponsiveLayout.splashLogoSize(const Size(320, 740)), 164);
      expect(HteResponsiveLayout.splashLogoSize(const Size(390, 620)), 164);
    });

    test('uses full splash sizing on standard and large phones', () {
      expect(HteResponsiveLayout.isCompact(const Size(390, 844)), isFalse);
      expect(HteResponsiveLayout.splashLogoSize(const Size(390, 844)), 224);
    });

    test('scales hero heading to narrow viewport widths', () {
      expect(HteResponsiveLayout.heroTitleSize(280), 27);
      expect(HteResponsiveLayout.heroTitleSize(340), 31);
      expect(HteResponsiveLayout.heroTitleSize(400), 34);
      expect(HteResponsiveLayout.heroTitleSize(480), 36);
    });

    test('uses adaptive Founder Studio dashboard columns', () {
      expect(HteResponsiveLayout.adminGridColumns(320), 2);
      expect(HteResponsiveLayout.adminGridColumns(600), 3);
      expect(HteResponsiveLayout.adminGridColumns(960), 4);
    });

    test('caps wide layouts without changing phone width', () {
      expect(HteResponsiveLayout.contentMaxWidth, 860);
      expect(HteResponsiveLayout.horizontalPagePadding(320), 17.6);
      expect(HteResponsiveLayout.horizontalPagePadding(1200), 24);
    });
  });
}
