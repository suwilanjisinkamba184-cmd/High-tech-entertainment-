import 'package:flutter/material.dart';

abstract final class HteColors {
  static const obsidian = Color(0xFF090A0B);
  static const surface = Color(0xFF111214);
  static const surfaceRaised = Color(0xFF1B1D20);
  static const gold = Color(0xFFD4B36D);
  static const goldHighlight = Color(0xFFF5E7BF);
  static const silver = Color(0xFFB9B7B1);
  static const primaryText = Color(0xFFF3EFE6);
  static const secondaryText = Color(0xFFAAA69D);
  static const divider = Color(0xFF39352D);
  static const danger = Color(0xFFE58B83);
}

abstract final class HteTheme {
  static ThemeData get dark {
    final scheme = ColorScheme.fromSeed(
      seedColor: HteColors.gold,
      brightness: Brightness.dark,
      surface: HteColors.surface,
    ).copyWith(
      primary: HteColors.gold,
      onPrimary: HteColors.obsidian,
      secondary: HteColors.silver,
      onSecondary: HteColors.obsidian,
      surface: HteColors.surface,
      onSurface: HteColors.primaryText,
      outline: HteColors.divider,
      error: HteColors.danger,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: scheme,
      scaffoldBackgroundColor: HteColors.obsidian,
      dividerColor: HteColors.divider,
      appBarTheme: const AppBarTheme(
        backgroundColor: HteColors.obsidian,
        foregroundColor: HteColors.primaryText,
        elevation: 0,
        centerTitle: false,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: HteColors.surface,
        indicatorColor: HteColors.gold.withValues(alpha: 0.16),
        labelTextStyle: WidgetStateProperty.resolveWith(
          (states) => TextStyle(
            color: states.contains(WidgetState.selected)
                ? HteColors.goldHighlight
                : HteColors.secondaryText,
            fontWeight: FontWeight.w600,
            fontSize: 12,
          ),
        ),
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: HteColors.surface,
        indicatorColor: HteColors.gold.withValues(alpha: 0.16),
        selectedIconTheme: const IconThemeData(color: HteColors.gold),
        unselectedIconTheme: const IconThemeData(color: HteColors.secondaryText),
        selectedLabelTextStyle: const TextStyle(
          color: HteColors.goldHighlight,
          fontWeight: FontWeight.w600,
        ),
        unselectedLabelTextStyle: const TextStyle(
          color: HteColors.secondaryText,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: HteColors.surfaceRaised,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: HteColors.gold,
          foregroundColor: HteColors.obsidian,
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          color: HteColors.primaryText,
          fontSize: 36,
          height: 1.12,
          fontWeight: FontWeight.w700,
          letterSpacing: -1.1,
        ),
        headlineMedium: TextStyle(
          color: HteColors.primaryText,
          fontSize: 26,
          height: 1.2,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.5,
        ),
        titleLarge: TextStyle(
          color: HteColors.primaryText,
          fontSize: 20,
          fontWeight: FontWeight.w600,
        ),
        bodyLarge: TextStyle(
          color: HteColors.primaryText,
          fontSize: 16,
          height: 1.5,
        ),
        bodyMedium: TextStyle(
          color: HteColors.secondaryText,
          fontSize: 14,
          height: 1.5,
        ),
      ),
    );
  }
}
