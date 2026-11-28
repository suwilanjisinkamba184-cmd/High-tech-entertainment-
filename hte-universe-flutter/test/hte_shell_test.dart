import 'package:flutter_test/flutter_test.dart';
import 'package:hte_universe/src/hte_app.dart';
import 'package:material_ui/material_ui.dart';

void main() {
  group('HTE responsive shell', () {
    testWidgets('phone layout exposes bottom navigation', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(const HteApp(backendConfigured: false));

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('HTE UNIVERSE'), findsOneWidget);

      await tester.tap(find.text('Videos').last);
      await tester.pumpAndSettle();

      final videoSearch = tester.widget<TextField>(find.byType(TextField));
      expect(videoSearch.decoration?.hintText, 'Search official videos');
      expect(find.text('Music. Beats.\\nProduction. Beyond.'), findsNothing);
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
