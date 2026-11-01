# Native Google sign-in and password recovery

The Android app uses Google's native account chooser through Google Play Services and sends the resulting ID token to Supabase Auth. It does not use a Chrome OAuth redirect.

## Required Google Cloud setup

1. Open Google Cloud Console and create/select a project.
2. Configure Google Auth Platform branding and audience.
3. Create an OAuth client ID of type **Web application**. Its client ID is used as `GOOGLE_WEB_CLIENT_ID`.
4. Create an OAuth client ID of type **Android** with package name `com.hightechentertainment.app` and the SHA-1 signing-certificate fingerprint of the APK that will be distributed.
5. Add the Web client ID and Android client ID to the Google provider's Client IDs list in Supabase Authentication → Sign In / Providers → Google. Enable Google provider and save.
6. In Supabase Authentication → URL Configuration → Redirect URLs, add:
   `com.hightechentertainment.app://login-callback`

The app uses a Web client ID as the server client ID when initializing `google_sign_in`. The Android OAuth client must match the package name and certificate SHA-1 for the installed build.

## GitHub Actions build configuration

Add a repository Actions secret named `GOOGLE_WEB_CLIENT_ID` containing the Web OAuth client ID. The Android build workflow passes it to Flutter using the `GOOGLE_WEB_CLIENT_ID` Dart define. The client ID is not a private credential, but using a repository secret keeps the build configuration out of source control.

If the secret is absent, the app still builds and email/password sign-in and password recovery remain available; Google sign-in displays a configuration message.

## Password recovery

The app sends Supabase's password reset email with redirect URI `com.hightechentertainment.app://login-callback`. Supabase must allowlist that URI. The deep link opens the app's password-recovery screen, where the user sets a new password.
