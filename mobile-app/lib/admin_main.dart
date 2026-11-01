import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:url_launcher/url_launcher.dart';
import 'main.dart' as hte;

const adminAuthRedirect = 'com.hightechentertainment.admin://login-callback';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Supabase.initialize(url: hte.supabaseUrl, publishableKey: hte.supabaseKey);
  runApp(const HteAdminApp());
}

class HteAdminApp extends StatelessWidget {
  const HteAdminApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'High-Tech Entertainment Admin',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: const Color(0xFF090909),
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFD4AF37), brightness: Brightness.dark),
    ),
    home: const AdminAuthGate(),
  );
}

class AdminAuthGate extends StatelessWidget {
  const AdminAuthGate({super.key});
  @override
  Widget build(BuildContext context) => StreamBuilder<AuthState>(
    stream: hte.db.auth.onAuthStateChange,
    builder: (context, snapshot) {
      final session = snapshot.hasData ? snapshot.data!.session : hte.db.auth.currentSession;
      return session == null ? const AdminLoginPage() : const AdminRoleGate();
    },
  );
}

class AdminRoleGate extends StatefulWidget {
  const AdminRoleGate({super.key});
  @override
  State<AdminRoleGate> createState() => _AdminRoleGateState();
}
class _AdminRoleGateState extends State<AdminRoleGate> {
  late Future<bool> check;
  @override
  void initState() { super.initState(); check = _check(); }
  Future<bool> _check() async {
    try { return await hte.db.rpc('hte_is_admin') == true; } catch (_) { return false; }
  }
  @override
  Widget build(BuildContext context) => FutureBuilder<bool>(
    future: check,
    builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) return const Scaffold(body: Center(child: CircularProgressIndicator(color: Color(0xFFD4AF37))));
      if (snapshot.data != true) return Scaffold(
        appBar: AppBar(title: const Text('Administrator access')),
        body: Center(child: Padding(padding: const EdgeInsets.all(28), child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.lock_outline, size: 64, color: Color(0xFFD4AF37)),
            const SizedBox(height: 18),
            const Text('This account is not authorised for HTE administration.', textAlign: TextAlign.center, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 10),
            const Text('Partner and music-promoter subscriptions do not grant administrator access.', textAlign: TextAlign.center, style: TextStyle(color: Colors.white60)),
            const SizedBox(height: 20),
            FilledButton(onPressed: () => hte.db.auth.signOut(), child: const Text('Sign out')),
          ],
        ))),
      );
      return const hte.AdminConsolePage();
    },
  );
}

class AdminLoginPage extends StatefulWidget {
  const AdminLoginPage({super.key});
  @override
  State<AdminLoginPage> createState() => _AdminLoginPageState();
}
class _AdminLoginPageState extends State<AdminLoginPage> {
  final email = TextEditingController();
  final password = TextEditingController();
  bool busy = false;
  String? error;
  @override
  void dispose() { email.dispose(); password.dispose(); super.dispose(); }
  Future<void> _signIn() async {
    setState(() { busy = true; error = null; });
    try {
      await hte.db.auth.signInWithPassword(email: email.text.trim(), password: password.text);
    } on AuthException catch (e) {
      if (mounted) setState(() => error = e.message);
    } catch (e) {
      if (mounted) setState(() => error = 'Sign-in failed. Check your connection and try again.');
    } finally { if (mounted) setState(() => busy = false); }
  }
  Future<void> _google() async {
    setState(() { busy = true; error = null; });
    try {
      await hte.db.auth.signInWithOAuth(
        OAuthProvider.google,
        redirectTo: adminAuthRedirect,
        authScreenLaunchMode: LaunchMode.externalApplication,
      );
    } on AuthException catch (e) {
      if (mounted) setState(() => error = e.message);
    } catch (_) {
      if (mounted) setState(() => error = 'Google sign-in could not be started.');
    } finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: Center(child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 460),
      child: ListView(shrinkWrap: true, padding: const EdgeInsets.all(26), children: [
        const SizedBox(height: 24),
        const Icon(Icons.admin_panel_settings_outlined, size: 82, color: Color(0xFFD4AF37)),
        const SizedBox(height: 18),
        const Text('HIGH-TECH ENTERTAINMENT', textAlign: TextAlign.center, style: TextStyle(fontWeight: FontWeight.w900, letterSpacing: 2, color: Color(0xFFD4AF37))),
        const SizedBox(height: 8),
        const Text('ADMINISTRATOR APPLICATION', textAlign: TextAlign.center, style: TextStyle(fontSize: 23, fontWeight: FontWeight.w900)),
        const SizedBox(height: 10),
        const Text('Private access for authorised HTE administrators only.', textAlign: TextAlign.center, style: TextStyle(color: Colors.white60)),
        const SizedBox(height: 28),
        OutlinedButton.icon(onPressed: busy ? null : _google, icon: const Icon(Icons.g_mobiledata, size: 28), label: const Text('Continue with Google'), style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(50), side: const BorderSide(color: Color(0xFFD4AF37)))),
        const Padding(padding: EdgeInsets.symmetric(vertical: 16), child: Row(children: [Expanded(child: Divider()), Padding(padding: EdgeInsets.symmetric(horizontal: 12), child: Text('OR USE EMAIL', style: TextStyle(color: Colors.white54, fontSize: 11))), Expanded(child: Divider())])),
        TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Administrator email', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder())),
        const SizedBox(height: 16),
        FilledButton(onPressed: busy ? null : _signIn, child: Text(busy ? 'Please wait…' : 'Sign in securely')),
        if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.redAccent))),
        const SizedBox(height: 18),
        const Text('A paid partner or music-promoter account cannot open this application unless an owner separately assigns administrator access.', textAlign: TextAlign.center, style: TextStyle(color: Colors.white54, fontSize: 12)),
      ]),
    ))),
  );
}
