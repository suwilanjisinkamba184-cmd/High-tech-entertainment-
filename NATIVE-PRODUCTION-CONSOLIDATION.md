# High-Tech Entertainment — platform consolidation register

**Last reconciled:** 24 November 2026  
**Baseline:** repository `main` at commit `ef66e218` (baseline observed when this register was prepared).

## Current production architecture

The repository documentation and current build configuration identify the following production architecture:

- **Public website:** Astro application under `website/`, assembled by `scripts/build-website.sh` and deployed to GitHub Pages.
- **Member account:** `account/` pages and shared account behavior/styles under `assets/`.
- **Founder Studio:** `admin/` routes and shared Founder behavior in `assets/app.js`; access must be enforced by Supabase RLS/server-side checks.
- **Android application:** Kotlin + Jetpack Compose under `hte-universe-native/`, using AndroidX Media3.
- **Legacy Flutter:** `mobile-app/` is historical reference, not the production Android build. Shorebird does not update the Kotlin/Compose application.
- **Shared backend:** the existing Supabase project provides Auth, PostgreSQL, RLS, Storage, and approved Edge Functions.

GitHub Pages is the canonical public website host. Do not reintroduce Netlify as the production host or change the Supabase project as part of frontend consolidation.

## Verified native Android identity

Checked against `hte-universe-native/app/build.gradle.kts`:

| Setting | Value |
|---|---|
| Application ID | `com.hightechentertainment.app` |
| Version name | `4.3.1` |
| Version code | `55` |
| Minimum SDK | `26` |
| Compile SDK | `37` |
| Target SDK | `37` |
| JDK / JVM target | `17` |
| Android Gradle Plugin | `9.3.1` |
| Gradle | `9.6.0` |
| Kotlin | `2.4.21` |
| Compose BOM | `2026.09.00` |
| Media3 | `1.11.1` |
| Supabase Kotlin BOM | `3.9.0` |

The Gradle module is authoritative for version and SDK values. This register and the native README must be updated whenever those values change.

## Website source-of-truth boundaries

- The public homepage is `website/src/pages/index.astro`, not root `index.html`.
- Shared layout and Supabase bootstrap: `website/src/layouts/SiteLayout.astro`.
- Public styles: `website/src/styles/global.css`.
- Interactive catalogue: `website/src/components/PublicCatalogue.tsx`.
- Public booking and feedback: `website/src/components/PublicBookingForm.tsx` and `PublicFeedbackForm.tsx`.
- Member account UI: `account/index.html` and `assets/account-experience.css`.
- Account/authentication/Founder Studio behavior: `assets/app.js`.

A change to the legacy root homepage alone does not update the deployed Astro homepage. Shared assets must be cache-versioned consistently wherever they are loaded.

## Backend and security boundaries

1. Keep the existing Supabase project, user accounts, catalogue, storage objects, and production data intact.
2. Never ship a service-role key, secret key, database password, or signing material in browser/mobile code.
3. Treat RLS and trusted server-side authorization as the security boundary for Founder-only actions.
4. Verify table names, columns, bucket IDs, bucket privacy, MIME limits, and policies against the live project before changing application assumptions.
5. Use additive, reversible migrations. Do not reset schemas, recreate tables, or bulk-rewrite production records as part of UI repair.
6. Do not change deployed Edge Functions or Auth provider settings without a separately reviewed change and explicit owner approval.

## Documentation inconsistencies corrected by this register

The prior native README described target SDK 36 and Compose BOM 2026.06.01, while the Gradle module specifies target SDK 37 and Compose BOM 2026.09.00. Earlier consolidation/audit documents also described app version 3.1.0 / code 30 and 4.0.9 / code 43. Those are historical snapshots, not the current Gradle identity 4.3.1 / code 55.

The previous statement that Flutter remained the production fallback conflicts with the current root README and native release workflow, which identify Kotlin/Compose as the production Android application. This register now uses the repository's current architecture and explicitly identifies Flutter as legacy source.

## Validation and release gates

A green workflow is necessary but not sufficient. Record evidence against the exact commit for:

- Website Astro type/build checks and GitHub Pages assembly.
- JavaScript syntax, sitemap XML, canonical URLs, and consistent shared-asset cache versions.
- Android unit tests, lint, debug build, and signed release build.
- Android package/version/signing-certificate continuity and install-over behavior.
- Supabase authentication, RLS isolation, Storage permissions, booking workflows, and Founder authorization.
- Real browser playback and physical-device Android audio/video/offline behavior.
- Accessibility, responsive layout, Android Go memory pressure, and failure/retry states.

Do not merge to `main`, deploy the website, publish an APK, rotate credentials, or delete historical releases/artifacts as part of an unreviewed repair. A release requires a reviewed PR, passing checks, verified signing identity, checksum, device acceptance, and an approved rollback plan.
