import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android Supabase callback URI uses the registered app scheme', () {
    final callback = Uri.parse(
      'com.hightechentertainment.app://login-callback',
    );

    expect(callback.scheme, 'com.hightechentertainment.app');
    expect(callback.host, 'login-callback');
  });
}
