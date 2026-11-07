import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_ui/main.dart';

const _startupChannel = MethodChannel('com.hightechentertainment.app/startup');
const _dataChannel = MethodChannel('com.hightechentertainment.app/data');

void main() {
  testWidgets('shows the branded startup experience', (tester) async {
    await tester.pumpWidget(const HighTechEntertainmentApp());
    await tester.pump();

    expect(find.text('HIGH-TECH'), findsOneWidget);
    expect(find.text('ENTERTAINMENT'), findsOneWidget);
    expect(find.text('PREPARING YOUR EXPERIENCE'), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('ends splash and stops audio at 15s even if native start hangs',
      (tester) async {
    var stopCalls = 0;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_startupChannel, (call) async {
      if (call.method == 'playStartup') {
        // Simulate a platform bridge that never responds within the splash.
        await Future<void>.delayed(const Duration(seconds: 30));
        return true;
      }
      if (call.method == 'stopStartup') {
        stopCalls++;
        return true;
      }
      return null;
    });
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_dataChannel, (call) async {
      if (call.method == 'getSession') {
        return <String, dynamic>{'authenticated': false};
      }
      return null;
    });

    await tester.pumpWidget(const HighTechEntertainmentApp());
    await tester.pump();
    expect(find.text('HIGH-TECH'), findsOneWidget);

    await tester.pump(const Duration(milliseconds: 14999));
    expect(find.text('HIGH-TECH'), findsOneWidget);
    expect(stopCalls, 0);

    await tester.pump(const Duration(milliseconds: 1));
    await tester.pump();
    expect(find.text('WELCOME TO HIGH-TECH'), findsOneWidget);
    expect(stopCalls, 1);

    await tester.pumpWidget(const SizedBox.shrink());
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_startupChannel, null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_dataChannel, null);
  });
}
