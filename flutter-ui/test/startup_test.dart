import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_ui/main.dart';

void main() {
  testWidgets('shows the branded 15-second startup experience', (tester) async {
    await tester.pumpWidget(const HighTechEntertainmentApp());
    await tester.pump();

    expect(find.text('HIGH-TECH'), findsOneWidget);
    expect(find.text('ENTERTAINMENT'), findsOneWidget);
    expect(find.text('PREPARING YOUR EXPERIENCE'), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
  });
}
