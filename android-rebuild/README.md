# High-Tech Entertainment — clean Android rebuild

This is the isolated clean native Android replacement workstream. It does not replace or delete the published website, Flutter fallback, or existing native candidate until all acceptance gates pass.

## Product identity
- Application ID: com.hightechentertainment.app
- Kotlin + Jetpack Compose + Material 3
- AndroidX Media3 for audio/video playback
- Minimum Android 8.0 / API 26; target SDK 36
- Preserve the metallic gold/silver HTE brand and existing 15-second startup recording.

## Preservation rules
- Do not delete or overwrite mobile-app/, native-android/, production releases, Supabase data, storage objects, OAuth clients, signing keys, or the public website during this rebuild.
- Keep the existing package ID and signing identity for future in-place upgrade compatibility.
- Reuse the existing Supabase project, Auth users, RLS policies, tables, and Storage buckets. Never reset production data.
- Founder Studio requires server-side owner/admin authorization.
- Sponsored audio remains server-controlled and separate from the startup jingle and user music queue.

## Rebuild sequence
1. Clean compiling native shell and automated CI.
2. Prove bundled startup audio on API 26+, Android Go and Appetize.
3. Build design system/navigation, then catalogue, auth, player, videos, library, bookings, profile and Founder Studio.
4. Integrate Supabase through typed repositories and test RLS with listener/founder accounts.
5. Add Media3 background playback, video, offline downloads and sponsored audio with isolated tests.
6. Run accessibility, low-memory, lifecycle, offline, network-failure and security tests.
7. Compare signer/version metadata; install over current app and verify user data.
8. Promote only after physical-device acceptance and explicit release approval.

## Quality gates
Every data-driven screen needs loading, empty, error, offline and retry states. Never show mock data as live content. Never expose raw backend exceptions or secrets. A successful compile is not proof a feature works or a production release is ready.
