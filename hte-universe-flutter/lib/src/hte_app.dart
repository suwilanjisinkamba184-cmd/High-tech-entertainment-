import 'package:flutter/material.dart';

import 'hte_shell.dart';
import 'hte_theme.dart';

class HteApp extends StatelessWidget {
  const HteApp({required this.backendConfigured, super.key});

  final bool backendConfigured;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HTE Universe',
      debugShowCheckedModeBanner: false,
      theme: HteTheme.dark,
      home: HteShell(backendConfigured: backendConfigured),
    );
  }
}
