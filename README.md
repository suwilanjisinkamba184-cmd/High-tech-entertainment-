# High-Tech Entertainment

**Music. Beats. Production. Beyond.**

[![OpenRepos](https://img.shields.io/endpoint?url=https://openrepos.org/status/high-tech-entertainment.openrepos.org.json&style=flat-square)](https://high-tech-entertainment.openrepos.org/)

High-Tech Entertainment is a focused music and artist platform for discovering releases, featured music, viewing artists and videos, and contacting or booking the team. The project includes a public website, a founder administration area, and a native Flutter application.

## Production addresses

- **Official public website (GitHub Pages):** https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/
- **Founder Studio:** https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/admin/
- **Account:** https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/account/

GitHub Pages is the official and canonical public website host. The `.github/workflows/publish-github-pages.yml` workflow installs Node.js, builds the Astro site in `website/`, assembles shared account and Founder Studio routes into `dist/` with `scripts/build-website.sh`, and deploys it to GitHub Pages on pushes to `main`. Supabase provides authentication, database, media storage and serverless functions.

## Project components

| Component | Location | Purpose |
|---|---|---|
| Public website | `website/` | Astro + React + TypeScript + Tailwind public music experience |
| Founder Studio | `admin/` | Administrative tools for managing platform content and enquiries |
| Account pages | `account/` | User account and profile experience |
| Flutter application | `mobile-app/` | Android and Windows application source |
| Supabase setup | Root SQL files and `database/` | Database schema, policies and migrations |
| GitHub skills | `.github/skills/` | Design and engineering guidance for supported coding agents |

### Music platform features

- Public streaming catalogue with title/artist/genre search and genre filtering.
- Device playlist and favourites for guests; authenticated favourites and the personal playlist sync through Supabase.
- Listening activity is recorded to the signed-in user’s private listening history.
- Founder Studio includes track metadata editing and live counts for unpublished tracks, videos and enquiries.
- Track edits, publishing and uploads remain protected by Supabase Row Level Security; no service-role key is shipped to the browser.

## Technology

- HTML, CSS and browser JavaScript for the website.
- Flutter and Dart for the native application.
- Supabase Auth, PostgreSQL, Row Level Security (RLS), and Storage.
- GitHub Actions and GitHub Pages for website publishing, validation and application builds/releases.

## Supabase configuration

The website's `supabase-config.js` contains the Supabase project URL and a browser-safe publishable key. A publishable key is intended for client-side use; it does **not** replace database security. Protect data with correct RLS policies and Storage policies.

**Never commit or expose** a Supabase `service_role` key, an `sb_secret_` key, database passwords, signing passwords, or a keystore file. Keep private credentials in the relevant secure service settings or GitHub Actions secrets.

Founder-only actions must be enforced by database RLS and server-side authorization. Hiding admin controls in the browser is not a security boundary.

### Authentication URL settings

In Supabase Dashboard → Authentication → URL Configuration:

Set the production Site URL to:

```
https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/
```

Add the production GitHub Pages routes used by the application to the allowed redirect URLs:

```
https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/
https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/admin/
https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/account/
https://high-tech-entertainment-16g.pages.dev/
https://high-tech-entertainment-16g.pages.dev/admin/
https://high-tech-entertainment-16g.pages.dev/account/
```

Add any additional callback URLs only when they are actually used by the website or mobile authentication flow. Do not use localhost as the production Site URL.

## Database and Storage

Database setup and migration files currently include:

- `supabase-setup.sql` — baseline database and storage setup.
- `supabase-admin-policies.sql` — founder/admin access policies.
- `SUPABASE-REDESIGN-MIGRATION.sql` — profile and avatar-related migration.
- `database/changes/` — subsequent database changes.

The website upload code expects these exact Supabase Storage bucket IDs:

| Bucket ID | Intended content | Access configuration |
|---|---|---|
| `hte-audio` | Published audio tracks | Public reads; uploads/deletes controlled by policies |
| `hte-avatars` | User profile avatars | Private; access controlled by policies or signed URLs |
| `hte-video` | Music videos | Confirm intended public/private access and policies before use |

Bucket **IDs** are not necessarily the same as their display names. Verify bucket IDs, size limits, MIME restrictions and policies in Supabase Dashboard → Storage before enabling uploads. Do not make private profile images public merely to make them display.

## Flutter application

Flutter source code is in `mobile-app/`.

The GitHub Actions workflow `.github/workflows/mobile-build.yml` builds:

- Android release APK.
- Android App Bundle (AAB).
- Windows desktop ZIP package.

The Android job requires all four repository Actions secrets before it can run:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

Follow [Android release signing setup](mobile-app/ANDROID-RELEASE-SIGNING.md) to configure them. The workflow **fails when any required signing secret is missing**; it does not produce a debug-signed fallback build. Keep the keystore and all passwords private.

The planned Android application ID is `com.hightechentertainment.app`. Verify the generated package/application ID before registering the app in Google Play Console.

## GitHub Actions

- `.github/workflows/publish-github-pages.yml` — assembles and publishes the official public website to GitHub Pages on every push to `main`.
- `.github/workflows/deploy-pages.yml` — validation only: checks required website files, JavaScript syntax, package JSON and sitemap XML.
- `.github/workflows/mobile-build.yml` — builds Android and Windows application packages and publishes releases when its required conditions and secrets are satisfied.

Open the repository's **Actions** tab to inspect workflow runs and download available artifacts. A successful validation run does not, by itself, prove that the live website, Supabase authentication, uploads or media playback work.

## Design and coding-agent guidance

- `.github/skills/` contains the project's installed design and engineering skills.
- `.github/copilot-instructions.md` contains repository-specific development guidance.

Use these resources when working on interface design, animation, mobile behavior, accessibility, performance and code review. Review changes and run the relevant checks before merging them into `main`.

## GitHub coding-agent skills

The repository includes reusable agent skills under `.github/skills/`:

- **Impeccable** — primary design director for UX critique, accessibility, responsive layout, typography, polish and hardening.
- **Emil Kowalski design engineering** — interaction detail, component craft and purposeful motion.
- **Break UI** — stress-testing unusual content, empty states, overflow and edge cases.
- **Mobile Native** — touch-first responsive behavior and mobile-browser quality.
- **Apple Design** — gesture, motion and interface feedback principles.
- **Animation Performance / Animate** — reduced-motion-aware animation and performance.
- **Pick UI Library** — dependency evaluation before adding UI libraries.
- **Prototype** — comparing materially different interface directions.

Start with `PRODUCT.md`, `DESIGN.md` and `.github/copilot-instructions.md`. The checked-in Impeccable skill and references are available to compatible coding agents. Its optional CLI and automatic hooks are installed separately with `npx impeccable install`; they are not assumed active merely because the skill files are present.

## Local development

Run a local HTTP server from the repository root. For example, with Python installed:

```bash
python -m http.server 8000
```

Then open:

```
http://localhost:8000/
```

Do not rely on opening the HTML files directly with `file://`; browser authentication redirects and some web APIs require an HTTP origin.

## Pre-release verification checklist

Before calling a release production-ready, verify each item in the actual environment:

- [ ] Public website loads over HTTPS at the configured GitHub Pages URL.
- [ ] Supabase email/password and any enabled OAuth sign-in flows complete and return to the correct page.
- [ ] Founder/admin authorization is enforced by RLS and server-side checks.
- [ ] Admin create, edit and delete operations work for tracks, artists, releases and videos.
- [ ] Storage bucket IDs, privacy settings, file limits and upload policies match the application.
- [ ] Audio and video playback work in supported desktop and mobile browsers.
- [ ] Booking, contact and support forms deliver submissions to the intended destination.
- [ ] Android APK installs and runs on a physical device; background playback and authentication are tested.
- [ ] Windows package extracts and launches on a supported Windows machine.
- [ ] Release signing and update compatibility are verified before distributing an Android update.

## Support and maintenance

Keep this README aligned with the actual GitHub Pages deployment, routes, Supabase schema, bucket IDs and workflow behavior. Update it whenever deployment architecture or application build requirements change.


## Legal documents and developer contact

- Privacy policy: [privacy-policy.html](privacy-policy.html)
- Terms of use: [terms-of-use.html](terms-of-use.html)
- Account deletion: [account-deletion.html](account-deletion.html)
- Developer / founder: **Simon Suwilanji Sinkamba**
- Developer email: **suwilanjisinkamba184@gmail.com**

The Android app's private Founder Studio includes an on-device developer diagnostics view. Flutter framework and uncaught platform errors are retained locally (up to 100 entries); the founder can review and explicitly export a redacted report. Diagnostic reports are not uploaded automatically.
