# High-Tech Entertainment

**Music. Beats. Production. Beyond.**

High-Tech Entertainment is a music, artist and production platform with a public website, member account, Founder Studio and Android application.

## Official services
- **Public website:** https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/
- **Founder Studio:** https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/admin/
- **Member account:** https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/account/
- **Android releases:** https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases

GitHub Pages is the canonical public website host. The workflow `.github/workflows/publish-github-pages.yml` runs `scripts/build-website.sh`, assembles the Astro site and shared account/admin routes into `dist/`, and publishes the result from `main`. Cloudflare is used for the dedicated signed-download gateway; it is not the website's canonical host. Netlify is not the production website host.

## Repository map

| Path | Responsibility |
|---|---|
| `website/` | Astro + React + TypeScript public website |
| `account/` | Member account pages |
| `admin/` | Founder Studio pages |
| `assets/` | Shared browser logic, styles and media |
| `hte-universe-native/` | Production Kotlin + Jetpack Compose Android app |
| `mobile-app/` | Legacy Flutter reference source; not the production Android app |
| `supabase/` | Supabase configuration, Edge Functions, migrations and tests |
| `cloudflare/workers/` | Cloudflare Worker source/configuration |
| `scripts/` | Website assembly, SEO generation and validation |
| `.github/workflows/` | CI, deployment and automation workflows |
| `docs/PLATFORM-SOURCE-OF-TRUTH.md` | Cross-platform architecture and release contract |

## Website development

Requirements are declared in `website/package.json` (Node.js >=22.23.4 and npm >=9.6.5). From the repository root:

```bash
npm --prefix website install
npm --prefix website run check
npm --prefix website run build
bash scripts/build-website.sh
```

The Astro homepage source is `website/src/pages/index.astro`. The root `index.html` is not the source of the deployed homepage. Shared account and Founder Studio pages are assembled by `scripts/build-website.sh`. Do not edit only the legacy root HTML when changing the live homepage.

## Android application

The production Android source is `hte-universe-native/`.

| Setting | Current source value |
|---|---|
| Application ID | `com.hightechentertainment.app` |
| Version name | `4.3.1` |
| Version code | `55` |
| Minimum SDK | `26` (Android 8.0) |
| Compile SDK | `37` |
| Target SDK | `37` |
| JDK/JVM target | `17` |
| Android Gradle Plugin | `9.3.1` |
| Gradle | `9.6.0` |
| Kotlin | `2.4.21` |
| Compose BOM | `2026.09.00` |
| Media3 | `1.11.1` |
| Supabase Kotlin BOM | `3.9.0` |

These are values in the current Gradle source, not proof that a matching APK has been published. The Android workflow currently sets public APK publishing and signed candidate generation to disabled during recovery. Do not promise a new installable APK until the workflow is deliberately re-enabled, a signed artifact is produced, and package/version/signature/install checks pass.

## Supabase and security

The existing Supabase project is shared by the website and Android app. Client applications may contain only the project URL and publishable key. A publishable key is not an authorization mechanism: RLS, Storage policies and server-side function checks must enforce access.

Never commit service-role or secret keys, database passwords, provider API keys, signing passwords or keystores. Do not reset or recreate production data as part of ordinary development. Review and test migrations on a disposable development branch before any approved production application.

See [SUPABASE-CONNECTION.md](SUPABASE-CONNECTION.md), [supabase/MIGRATION-RECONCILIATION.md](supabase/MIGRATION-RECONCILIATION.md) and [docs/PLATFORM-SOURCE-OF-TRUTH.md](docs/PLATFORM-SOURCE-OF-TRUTH.md).

## Important workflows

- `.github/workflows/publish-github-pages.yml` — official website publishing.
- `.github/workflows/deploy-pages.yml` and `.github/workflows/validate-website-pr.yml` — website validation.
- `.github/workflows/hte-universe-compose.yml` — Android tests, lint and gated release workflow.
- `.github/workflows/daily-marketing-video.yml` — daily promotional video generation.
- `.github/workflows/youtube-zambian-sync.yml` and `.github/workflows/sync-official-youtube-channel.yml` — YouTube catalogue synchronization paths.
- `.github/workflows/validate-supabase-migrations.yml` — migration validation workflow.

A green CI job proves only the checks that job ran. It does not prove every live OAuth provider, Supabase policy, upload, booking, playback path or physical device works.

## Legal and contact pages
- Privacy policy: [privacy-policy.html](privacy-policy.html)
- Terms of service: [terms-of-service.html](terms-of-service.html)
- Account deletion: [account-deletion.html](account-deletion.html)

Developer / founder: **Simon Suwilanji Sinkamba**  
Developer email: **suwilanjisinkamba184@gmail.com**

## Release acceptance
Before a website or Android release is described as production-ready, verify the relevant workflow, deployed URL or release asset, authentication, role authorization, media delivery, booking flow, error recovery and device compatibility. For Android, also verify the production signer and install-over compatibility. Record untested items as unverified.
