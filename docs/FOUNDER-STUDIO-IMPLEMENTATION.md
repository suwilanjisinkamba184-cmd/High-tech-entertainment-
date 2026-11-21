# Founder Studio implementation notes

**Status:** Implementation staged in draft PR #160. No production deployment or merge.

The Founder Studio overhaul is being implemented on an isolated feature branch from main. This is a source change only; production is not deployed.

## Delivered in source
- Added a responsive Founder Command Centre controller and stylesheet, loaded only by admin/index.html.
- Added dedicated Bookings, Audience Analytics, System Health and Founder Activity Log workspaces.
- Reworked the Overview first viewport to prioritise operational KPIs and reduced the promotional hero footprint.
- Added a compact mobile topbar and improved small-screen layout.
- Unified display of structured member/Android bookings and guest website booking requests without moving source records.
- Added private notes and follow-up scheduling for guest booking requests, backed by an additive workflow table migration.
- Added explicit analytics labels distinguishing recorded events from verified downloads or plays; daily trends use a bounded 1–90 day server-side RPC.
- Added read-only authentication, catalogue database, storage and social-connection health checks.
- Added append-only audit logging for new Founder Studio booking status changes, catalogue operations, audio/video uploads and visibility changes, marketing draft creation and audio-ad visibility changes.
- Added dedicated CI checks for browser-controller syntax, Edge Function types and architecture invariants.
- Replaced the repository source for Meta publishing and Meta connection-check functions with role-based authorization using Supabase auth.getUser() plus hte_is_admin(), and strict official-origin CORS. The source no longer uses a hard-coded founder UUID.
- Kept existing Founder authentication, Supabase project, catalogue tables, public routes and Android source intact.

## Backend migration — not applied
The migration file supabase/migrations/20261121190000_founder_studio_operations.sql adds:
- Guest booking workflow metadata, including status, internal note and follow-up timestamp.
- An append-only founder audit table with founder-only RLS and insert-only access.
- A guarded audience time-series RPC with a 1–90 day bound.

No production DDL has been run. The UI gracefully identifies when these new tables/RPCs are not installed yet. The migration must be applied and RLS-tested on a Supabase development branch first.

## Edge Functions — source updated, live versions unchanged
The live meta-publish function previously used a hard-coded founder UUID comparison. The repository source now verifies the signed-in user and checks hte_is_admin(). The live deployed functions have not been replaced. Do not enable publishing until the functions pass Deno checks, founder/non-founder authorization tests, Meta Page permission checks and a real test publish in the isolated environment.

## External configuration
Supabase Auth leaked-password protection is disabled in the connected project's Security Advisor. This is an Auth dashboard setting and has not been changed by this PR. Enable it under Authentication → Settings → Password Security if supported by the project's plan.

## Verification status
- Browser controller Node syntax: passed in the first CI run.
- Founder Studio CI passed on the implementation snapshot: Node syntax checks for all affected browser scripts, Deno type checks for both Meta Edge Functions, and architecture-invariant checks all succeeded.
- Public website validation passed, including legacy JavaScript syntax and shared-route checks. The full website redesign build was still running at the last status check.
- Browser-based mobile/desktop interaction testing has not been performed.
- SQL migration has not been executed or validated against a development database.
- Public website and Android regression checks are required to pass before merge.
- PR remains draft; no merge or production deployment is authorized.
