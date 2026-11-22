import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hte_universe/src/hte_app.dart';

void main() {
  group('HTE responsive shell', () {
    testWidgets('phone layout exposes bottom navigation', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(const HteApp(backendConfigured: false));

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('Music. Beats.\nProduction. Beyond.'), findsOneWidget);

      await tester.tap(find.text('Videos').last);
      await tester.pumpAndSettle();

      expect(find.text('Official videos'), findsWidgets);
    });

    testWidgets('wide layout uses navigation rail', (tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(const HteApp(backendConfigured: true));

      expect(find.byType(NavigationRail), findsOneWidget);
      expect(find.byType(NavigationBar), findsNothing);
    });
  });
}
