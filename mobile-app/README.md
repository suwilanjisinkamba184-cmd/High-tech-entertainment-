# High-Tech Entertainment Mobile App

This directory is reserved for the native Flutter application.

## Targets
- Android: APK for direct installation and AAB for Google Play.
- iOS: IPA build through a macOS GitHub Actions runner (Apple signing credentials required for distribution).
- Windows: Windows desktop package through a Windows GitHub Actions runner.

## Backend
The app will use the existing High-Tech Entertainment Supabase project. Before implementing data access, verify the live table schemas, row-level security policies, and Storage bucket names. Never put a Supabase service-role key in the client application.

## Build process
GitHub Actions will build platform artifacts and upload them as workflow artifacts. Store signing credentials only in GitHub Actions secrets.

The Flutter source and CI workflow will be added here after the Supabase schema and application requirements are verified.
