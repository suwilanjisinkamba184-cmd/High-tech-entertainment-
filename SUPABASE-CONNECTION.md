# Supabase connection and operational safety

## Existing project
The repository is configured for the existing Supabase project `wqrxacjbsldbnoekbkbf`. The website's browser configuration is copied from the root `supabase-config.js` by `scripts/build-website.sh`; the Android app receives its runtime URL and publishable key through Gradle configuration and `BuildConfig`.

Do not create a replacement project, reset the existing database, recreate tables, or bulk-rewrite production records as part of routine application work.

## Client configuration
Browser and Android clients may use only:
- Supabase project URL.
- Supabase publishable key (or the legacy anon JWT where the project still requires it).

These values are public client configuration. They do not grant privileged access. RLS and Storage policies must enforce every read/write boundary.

Never place a service-role key, `sb_secret_` key, provider API key, database password, signing password or keystore in browser files, mobile source, documentation examples, commits or chat. Provider secrets belong in the corresponding server-side secret store.

## Authentication and authorization
- Configure the production GitHub Pages origin in Supabase Authentication URL settings.
- Add callback URLs only when they are used by an implemented authentication flow.
- Do not assume Google, Facebook, X or another OAuth provider is enabled just because a button or logo exists. Verify provider configuration, redirect URI, consent and a live sign-in round trip.
- Founder-only operations must be authorized by database RLS and trusted server-side checks. UI visibility is not a security boundary.
- Test member and founder roles separately. A signed-in user is not automatically a founder.

## Booking and contact flows
The repository contains distinct guest and authenticated booking paths and server-side notification functions. Before changing them, inspect the current website components, Edge Function source and `supabase/config.toml`. In particular, a function configured with `verify_jwt = false` must perform its own validation appropriate to its public or signed-request contract. Do not add a service-role key to a client to work around authorization.

Do not rely on old screenshots or earlier deployment notes to infer which booking path is currently live. Confirm the workflow, deployed function version, allowed origins, rate limits and end-to-end delivery before claiming the flow is operational.

## Database migrations
- Treat `supabase/migrations/` as versioned source history, not permission to replay SQL against production.
- Read [supabase/MIGRATION-RECONCILIATION.md](supabase/MIGRATION-RECONCILIATION.md) before proposing schema changes.
- Test new migrations on a disposable development branch/database, inspect RLS/grants/functions/triggers and run the repository SQL tests.
- Compare the migration ledger before applying anything to production. Never reset production or replay already-applied versions.

## Storage
Bucket IDs, public/private status, MIME restrictions, size limits and policies must be read from the current application and Supabase project configuration before making changes. Do not infer a bucket's ID from its display name. Do not make private avatars, user files or restricted media public merely to fix image loading.

## Operational checklist
1. Confirm the intended project ID and environment.
2. Verify only publishable configuration is present in client builds.
3. Inspect current Auth redirect/provider settings.
4. Inspect RLS, grants, Storage policies and Edge Function JWT configuration.
5. Test anonymous, member and founder paths independently.
6. Verify guest/member booking and contact delivery where relevant.
7. Validate proposed SQL on a disposable development branch.
8. Record the exact tests and environment used; label unverified behavior accurately.
