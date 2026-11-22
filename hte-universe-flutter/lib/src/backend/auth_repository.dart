import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

abstract final class HteAuthRepository {
  static SupabaseClient get _client => Supabase.instance.client;

  static User? get currentUser => _client.auth.currentUser;

  static Stream<AuthState> get authChanges => _client.auth.onAuthStateChange;

  static Future<void> signInWithPassword({
    required String email,
    required String password,
  }) async {
    if (email.trim().isEmpty || password.isEmpty) {
      throw const AuthException('Enter your email address and password.');
    }
    await _client.auth.signInWithPassword(
      email: email.trim(),
      password: password,
    );
  }

  static Future<void> signUp({
    required String email,
    required String password,
  }) async {
    if (email.trim().isEmpty || password.length < 8) {
      throw const AuthException('Enter an email and a password with at least 8 characters.');
    }
    await _client.auth.signUp(
      email: email.trim(),
      password: password,
    );
  }

  static Future<void> signInWithGoogle() async {
    final redirectTo = kIsWeb
        ? Uri.base.toString()
        : 'com.hightechentertainment.app://auth-callback';
    await _client.auth.signInWithOAuth(
      OAuthProvider.google,
      redirectTo: redirectTo,
    );
  }

  static Future<void> signOut() => _client.auth.signOut();
}
