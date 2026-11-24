# HTE Universe — Android and website consistency audit

**Audit date:** 24 November 2026  
**Repository baseline:** `main` at `ef66e218` when inspected  
**Scope:** repository documentation, top-level architecture, website package metadata, and native Android Gradle identity.

## Executive finding

The project has a coherent current architecture, but several checked-in documents describe different generations of the app. This creates a misleading picture of which Android implementation is production, which SDK/dependency versions are active, and which release identity is current.

The current repository source identifies the Kotlin/Jetpack Compose project in `hte-universe-native/` as the production Android app. The legacy Flutter tree is historical reference. The public website is an Astro build in `website/` and GitHub Pages is its canonical production host. Supabase remains the shared backend.

## Verified current Android configuration

Values below were read directly from `hte-universe-native/app/build.gradle.kts`:

- Application ID: `com.hightechentertainment.app`
- Version name/code: `4.3.1` / `55`
- Minimum SDK: 26
- Compile SDK / target SDK: 37 / 37
- JDK/JVM target: 17
- Android Gradle Plugin / Gradle: 9.3.1 / 9.6.0
- Kotlin: 2.4.21
- Compose BOM: 2026.09.00
- AndroidX Media3: 1.11.1
- Supabase Kotlin BOM: 3.9.0
- Ktor: 3.6.1
- ElevenLabs Android SDK: 0.13.1

These values must be treated as the current source baseline until a later reviewed Gradle change supersedes them.

## Confirmed documentation drift

| Document | Previous statement | Current source |
|---|---|---|
| `hte-universe-native/README.md` | Target SDK 36 | Gradle target SDK 37 |
| `hte-universe-native/README.md` | Compose BOM 2026.06.01 | Gradle Compose BOM 2026.09.00 |
| `NATIVE-PRODUCTION-CONSOLIDATION.md` | Version 3.1.0 / code 30 | Gradle version 4.3.1 / code 55 |
| `NATIVE-PRODUCTION-CONSOLIDATION.md` | Flutter remains production fallback | Root README and native workflow identify Kotlin/Compose as production |
| `hte-universe-native/HTE-ANDROID-QUALITY-AND-UI-AUDIT.md` | Version 4.0.9 / code 43 / min SDK 25 | Current Gradle version 4.3.1 / code 55 / min SDK 26 |

These differences are documentation defects. They do not, by themselves, prove a runtime defect or a failed build.

## Website architecture verified from repository documentation and package metadata

- Astro project: `website/`; package name `high-tech-entertainment-web`.
- Website build scripts: `dev`, `build`, `preview`, and `check`.
- Public homepage source: `website/src/pages/index.astro`.
- GitHub Pages is the production host.
- Root `index.html` is legacy and does not control the deployed Astro homepage.
- Account and Founder Studio use the shared account/admin route files and `assets/app.js`.
- Supabase is the shared authentication/data/storage backend.

## Required cross-platform consistency rules

1. Keep the public website, member account, Founder Studio, and Android app on the same approved Supabase project.
2. Keep catalogue visibility rules consistent: published content is public; draft content is not exposed to ordinary users.
3. Enforce Founder permissions in RLS/server-side authorization, never only in UI navigation.
4. Keep app package ID, signing identity, version code, and release tags aligned with the actual Gradle build.
5. Keep GitHub Pages as the canonical website host and avoid stale Netlify references.
6. Keep website canonical URLs, sitemap entries, app download links, and release metadata synchronized.
7. Keep user-facing network errors actionable and do not expose raw backend/provider exceptions.
8. Do not duplicate or replace existing backend tables, credentials, or production data as part of presentation work.

## Validation status and limitations

This document records a source/documentation audit, not a fresh build or full runtime acceptance. A green CI run does not prove device behavior. The following still require current evidence against the exact candidate commit:

- Astro build/type check and GitHub Pages workflow.
- Android unit tests, lint, debug assembly, and signed candidate build.
- Package/version/signer verification and installation over the current installed app.
- Supabase Auth/OAuth, RLS isolation, Storage access, booking, and Founder-only operations.
- Browser audio/video playback and physical Android audio/video/background/offline behavior.
- Low-memory Android Go, process recreation, accessibility, and retry/error states.

## Source files used

- `README.md`
- `website/README.md`
- `website/package.json`
- `hte-universe-native/README.md`
- `hte-universe-native/app/build.gradle.kts`
- `NATIVE-PRODUCTION-CONSOLIDATION.md`

The uploaded Android Studio ZIP is not part of this repository-only audit. Re-upload it before any code is copied from that archive or before claiming a new ZIP-to-branch source comparison.
