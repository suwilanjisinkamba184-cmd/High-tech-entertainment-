import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_ui/studio_services_screen.dart';

void main() {
  testWidgets('shows the studio service catalogue and quote request flow', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: StudioServicesScreen()),
      ),
    );

    expect(find.text('HIGH-TECH STUDIO'), findsOneWidget);
    expect(find.text('Make your next release happen.'), findsOneWidget);
    expect(find.text('Music production'), findsOneWidget);
    expect(find.text('Beat licensing'), findsOneWidget);
    expect(find.text('Mixing & mastering'), findsOneWidget);
    expect(find.text('Music promotion'), findsOneWidget);

    await tester.tap(find.text('REQUEST A QUOTE').first);
    await tester.pumpAndSettle();

    expect(find.text('START YOUR PROJECT'), findsOneWidget);
    expect(find.text('Music production'), findsOneWidget);
    expect(find.text('SEND PROJECT REQUEST'), findsOneWidget);

    await tester.tap(find.text('SEND PROJECT REQUEST'));
    await tester.pump();

    expect(find.text('Enter your name.'), findsOneWidget);
    expect(find.text('Enter a valid email address.'), findsOneWidget);
    expect(find.text('Tell us about your project.'), findsOneWidget);
  });
}
