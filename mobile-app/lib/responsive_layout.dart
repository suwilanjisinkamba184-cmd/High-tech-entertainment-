import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// Shared layout breakpoints for the High-Tech Entertainment Android client.
///
/// Widths are measured in logical pixels after system insets. These helpers
/// keep compact phones usable, scale hero content, and avoid tablet stretching.
abstract final class HteResponsiveLayout {
  static const double contentMaxWidth = 860;
  static const double compactWidth = 350;
  static const double compactHeight = 680;

  static bool isCompact(Size size) =>
      size.width < compactWidth || size.height < compactHeight;

  static double splashLogoSize(Size size) =>
      isCompact(size) ? 164 : 224;

  static double heroTitleSize(double availableWidth) {
    if (availableWidth < 300) return 27;
    if (availableWidth < 360) return 31;
    if (availableWidth < 430) return 34;
    return 36;
  }

  static int adminGridColumns(double availableWidth) {
    if (availableWidth >= 900) return 4;
    if (availableWidth >= 560) return 3;
    return 2;
  }

  static double horizontalPagePadding(double availableWidth) =>
      math.min(availableWidth * 0.055, 24).clamp(12, 24).toDouble();
}
