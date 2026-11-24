# HTE platform source of truth

**Reviewed:** 24 November 2026  
**Repository:** `suwilanjisinkamba184-cmd/High-tech-entertainment-`

## Canonical surfaces

| Surface | Canonical source | Production role |
|---|---|---|
| Public website | `website/` (Astro) | Public discovery, service information, booking and contact |
| Member account | `account/` plus shared `assets/` | Member profile, authentication and account workflows |
| Founder Studio | `admin/` plus shared `assets/app.js` | Founder-only content and operations |
| Android app | `hte-universe-native/` (Kotlin + Jetpack Compose) | Production Android client |
| Legacy Flutter | `mobile-app/` | Historical reference; not the production Android build |
| Backend | Existing Supabase project | Auth, PostgreSQL, RLS, Storage and approved Edge Functions |
| Website host | GitHub Pages | Canonical public website deployment |
| Android distribution | GitHub Releases | Signed APK distribution |

The Astro homepage is `website/src/pages/index.astro`. Root `index.html` is not the deployed homepage. Do not treat changes to the legacy root page as website releases.

## Current Android build identity

The source of truth is `hte-universe-native/app/build.gradle.kts`:

- Application ID: `com.hightechentertainment.app`
- Version: `4.3.1` (version code `55`)
- Minimum SDK: `26`
- Compile SDK / target SDK: `37` / `37`
- JDK/JVM: `17`
- AGP / Gradle: `9.3.1` / `9.6.0`
- Kotlin: `2.4.21`
- Compose BOM: `2026.09.00`
- Media3: `1.11.1`
- Supabase Kotlin BOM: `3.9.0`

Update documentation from Gradle after a reviewed build change; never edit Gradle merely to match an old document.

## Cross-surface contracts

1. All production clients use the same approved Supabase project and publishable client configuration.
2. Supabase publication fields and RLS define public catalogue visibility. Draft data must not leak into public website or ordinary member experiences.
3. Founder-only actions require server-side authorization and RLS. UI-only gating is insufficient.
4. Account and Founder Studio scripts/styles are shared assets. Cache-busting versions must be synchronized across all pages that load the same asset.
5. Website canonical URLs, sitemap, GitHub Pages base path, app download URL and Android release metadata must agree.
6. Direct audio uses HTTPS media URLs and the native/browser media stack appropriate to the client. Spotify URLs use Spotify's official embed/player flow, not raw audio extraction.
7. Network failures should produce clear retry guidance; raw SQL, provider responses, tokens and stack traces must not appear in public UI.
8. Preserve existing users, catalogue rows, storage objects, signing identity and backend configuration during frontend/client repairs.

## Security and change control

- Never commit Supabase service-role/secret keys, signing passwords, keystores, database passwords or private provider credentials.
- Never reset or recreate the production database as a UI repair.
- Database changes must be additive, reviewed, reversible where practical, and tested against RLS.
- Do not switch the official website host away from GitHub Pages without explicit owner approval.
- Do not merge to `main`, publish the website, publish an APK, change Auth providers, or modify production Edge Functions without explicit owner approval.

## Release acceptance

A passing workflow is not full production acceptance. Record evidence for website build/deploy validation, Android tests/lint/build, package/version/signer continuity, Supabase Auth/RLS/Storage checks, browser playback, physical-device playback/offline behavior, accessibility, and rollback. A release candidate must be install-tested over the currently distributed app without losing user data.

## Supporting documents

- `README.md` — repository overview and operational setup.
- `website/README.md` — Astro website source and build boundaries.
- `hte-universe-native/README.md` — Android toolchain, build, backend and release guide.
- `NATIVE-PRODUCTION-CONSOLIDATION.md` — platform consolidation and release gates.
- `hte-universe-native/HTE-ANDROID-QUALITY-AND-UI-AUDIT.md` — Android and website consistency audit.
