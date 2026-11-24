import 'package:flutter_test/flutter_test.dart';
import 'package:hte_universe/src/hte_app.dart';
import 'package:material_ui/material_ui.dart';

void main() {
  group('HTE responsive shell', () {
    testWidgets('phone layout exposes bottom navigation and switches pages', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(const HteApp(backendConfigured: false));

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('HTE UNIVERSE'), findsOneWidget);

      await tester.tap(find.text('Videos').last);
      await tester.pumpAndSettle();

      // This status is exclusive to the Videos destination when the backend is
      // intentionally disabled; the Home page uses a different message.
      expect(find.text('Catalogue unavailable'), findsOneWidget);
      expect(find.text('Official videos'), findsOneWidget);
    });

    testWidgets('wide layout uses navigation rail', (tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(const HteApp(backendConfigured: false));

      expect(find.byType(NavigationRail), findsOneWidget);
      expect(find.byType(NavigationBar), findsNothing);
    });
  });
}
