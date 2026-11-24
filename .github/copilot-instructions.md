# High-Tech Entertainment — repository instructions

## Canonical architecture
- The public website is an Astro application in `website/`. Its production build is assembled by `scripts/build-website.sh` and deployed to GitHub Pages by `.github/workflows/publish-github-pages.yml`.
- The public site, member account (`account/`), and Founder Studio (`admin/`) are separate source surfaces assembled into the same GitHub Pages deployment. Shared browser behavior and styles live in `assets/`.
- The production Android application is Kotlin + Jetpack Compose in `hte-universe-native/`, using AndroidX Media3 and the Supabase Kotlin client.
- `mobile-app/` is legacy Flutter source retained for reference. Do not describe it as the current production app or add it to the native build.
- Supabase is the shared backend for Auth, Postgres, Storage and Edge Functions. Preserve the existing project and data.

## Source of truth
Before changing a documented value, inspect the corresponding source:
- Android package, SDK levels, version and dependencies: `hte-universe-native/app/build.gradle.kts`.
- Android workflow and release gates: `.github/workflows/hte-universe-compose.yml`.
- Website commands and dependencies: `website/package.json`.
- Website output and route assembly: `scripts/build-website.sh`.
- Production website publishing: `.github/workflows/publish-github-pages.yml`.
- Supabase function JWT settings: `supabase/config.toml`.
- Download Worker settings: `cloudflare/workers/hte-download-gateway/wrangler.toml`.
- Cross-platform architecture and release rules: `docs/PLATFORM-SOURCE-OF-TRUTH.md`.

Never infer that a feature is production-ready from a README, a source file, or a successful compile alone. Distinguish implemented, CI-tested, deployed, and physically verified states.

## Required working practices
- Inspect the existing implementation and repository structure before changing files. Preserve working behavior and deployment configuration unless explicitly requested.
- Do not reset, recreate, or bulk-rewrite the Supabase project. Do not apply migrations to production without an explicit reviewed change and approval.
- Never commit Supabase service-role/secret keys, provider API keys, passwords, signing passwords, keystores, access tokens or private user data.
- Treat Supabase RLS and server-side authorization as the security boundary. Hiding Founder Studio controls is not authorization.
- Keep public media visibility intentional. Do not make private avatars, user data or private media public to fix rendering.
- Preserve `com.hightechentertainment.app`, the existing signing identity and backend unless an approved migration explicitly changes them.
- Do not publish an APK or release tag while the Android workflow's recovery gate disables publication. Never call an APK ready without verifying artifact, package, version, signature and install compatibility.
- Do not publish the website directly from a feature branch. Use the reviewed GitHub Pages workflow on `main`.
- Do not describe social publishing, AI generation, OAuth providers or third-party integrations as connected until their credentials, permissions and live test calls have been verified.

## Validation and reporting
- Run the narrowest relevant checks, then the complete required workflow when practical.
- For website work, run `npm --prefix website run check`, `npm --prefix website run build`, and `bash scripts/build-website.sh` when the change affects the published output.
- For Android work, run unit tests, instrumentation tests, debug/release lint and the configured CI workflow. Physical-device checks are still required for device-specific behavior.
- For Supabase work, inspect migration ordering, RLS, grants, function search paths and Storage policies. Validate SQL on a disposable development branch before any production application.
- Report exactly what was run and its result. Label unrun checks and device tests as not verified.

## Interface quality
Use the checked-in skills under `.github/skills/` when relevant. Prefer accessible controls, keyboard support, reduced-motion behavior, responsive layouts, readable typography and efficient animation. Preserve the HTE obsidian, gold and silver identity while using platform-appropriate interaction patterns. Avoid unnecessary dependencies and do not copy entire modules from legacy projects without compatibility review.
