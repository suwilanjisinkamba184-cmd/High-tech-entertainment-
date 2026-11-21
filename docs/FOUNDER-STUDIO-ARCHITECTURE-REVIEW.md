# Founder Studio — Architecture Review

**Status:** Architecture review complete; implementation staged in PR #160; backend migration remains unapplied  
**Review date:** 2026-11-21  
**Source of truth:** `main` at `2a5d205464197965fdf70997609001c41ab7f1b9`  
**Supabase project:** High-tech entertainment (`wqrxacjbsldbnoekbkbf`), PostgreSQL 17.11.0.003  
**Scope:** Founder Studio only. Public website and Android application are protected regression surfaces.

## Executive assessment

Founder Studio has a real foundation: Supabase-backed founder authorization, catalogue management, video management, an audience analytics RPC, campaign drafts, social publishing tables, and several Edge Functions. The problem is not that the Studio is only a static mockup. The problem is that its workspaces are fragmented, key workflows are not surfaced in the UI, and some integrations have backend pieces without an end-to-end founder workflow.

The current implementation should be evolved additively. Do not replace the Supabase project, reset production data, or rewrite shared public-site/Android services as part of this work.

## Verified architecture

- GitHub repository default branch is `main`; reviewed commit: `2a5d205464197965fdf70997609001c41ab7f1b9`.
- Founder authorization is based on `private.user_roles` and `hte_is_admin()`; the live role table currently has one active `owner` assignment.
- Founder-facing RPCs include `hte_admin_get_analytics()` and `hte_admin_list_users()`. Their public wrappers are invoker functions; privileged implementations live in the non-exposed `private` schema and perform owner/admin checks.
- Core content tables include `tracks`, `artists`, `releases`, and `videos`. These tables have RLS enabled.
- The live database includes `bookings`, `contact_messages`, `booking_notification_log`, `hte_audience_events`, `hte_marketing_campaigns`, `hte_social_connections`, and `hte_social_publish_jobs`.
- Current public booking Edge Function writes guest booking requests into `contact_messages`. The separate `bookings` table is used by the member/native booking path. The two paths are not yet presented as one managed booking workspace.
- The marketing UI reads social connection status and campaign/job records. Saving a campaign deliberately saves a draft only; the client code explicitly does not enqueue or publish it.
- Active Meta Edge Functions exist for connection checks and Facebook publishing. The Meta publisher is gated at the Edge Function gateway and checks the founder subject against a configured/fallback founder user ID. This is narrower than a generic authenticated-user endpoint, but it is inconsistent with the database's role-based authorization model and should be standardized before expanding publishing.
- The ElevenLabs Ad Studio Edge Function verifies the signed-in user and calls `hte_is_admin()`; generated audio is stored as an unpublished draft.
- Audience analytics already record page views, track plays and downloads and expose aggregate KPIs. The current UI injects these metrics into the Overview page rather than giving audience intelligence a dedicated workspace.
- Existing website CI performs broad route and static checks, but the Founder Studio scripts and their real interactions need dedicated checks.

## Live-state snapshot (2026-11-21)

- `bookings`: 0 rows.
- `contact_messages` booking-request records: 4 rows.
- `hte_marketing_campaigns`: 1 row (draft).
- `hte_social_connections`: 0 rows.
- `hte_social_publish_jobs`: 0 rows.
- `hte_audience_events`: 75 rows.
- Catalogue row counts returned by Supabase: 4 tracks, 5 artists, 2 releases, 89 videos.

These are point-in-time counts, not targets or guarantees.

## Findings and priority

### P0 — Booking workflows are split across two data models

The public guest booking Edge Function inserts into `contact_messages` with a `Booking request — …` subject and stores service/date/time/phone inside the message text. The member/native workflow uses `bookings`, which has structured `booking_type`, `requested_date`, and `status` fields. The current web Founder Studio inbox queries only `contact_messages`; there is no dedicated bookings navigation item or management screen.

**Risk:** founder operations can miss or misclassify bookings from one intake path; the current enquiry count counts every contact message, not booking requests by status.

**Proposed change:** add a dedicated Bookings workspace with two clearly identified source queues (legacy/public booking messages and structured member/native bookings). Normalize them in the UI without moving or deleting existing records. Add workflow metadata for legacy contact-message bookings (status, internal note, follow-up date, updated-by) in an additive table keyed to the original message ID. Keep member/native booking status in its existing table. Define a later, separately reviewed migration only if a single canonical table is truly required.

### P0 — Marketing is draft-first, not end-to-end publishing

The current UI explicitly saves drafts without queueing or publishing. Live state shows no social connections and no publish jobs. The database already has social connection and publish-job tables, and Meta connection/publish Edge Functions exist, but the frontend does not yet provide a verified connect → permission check → approval → queue/schedule → publish → reconcile lifecycle. Current Meta publishing authorization is tied to a configured/fallback founder UUID instead of the shared role RPC.

**Risk:** the dashboard can imply social automation that is not currently operational; platform connection and delivery states are not proven by the existence of tables/functions alone.

**Proposed change:** standardize all publisher functions on validated Supabase user identity plus `hte_is_admin()`; keep provider tokens server-side only; implement explicit per-platform capability/permission checks; only enqueue after founder approval; add retry/backoff, idempotency, status reconciliation and audit history. Mark unsupported channels as manual/not available rather than displaying them as connected. Keep WhatsApp Status as a manual native-share path unless an approved API workflow is implemented.

### P1 — Audience analytics are useful but fragmented

The existing analytics RPC returns page views, unique viewers, members, artist subscribers, plays, downloads and 30-day page views. The client renders those figures as injected panels under Overview. It does not provide a dedicated analytics workspace with date-range trends, source/platform splits, content rankings, conversion funnels or recent activity. The aggregate play metric combines listening history and audience events; this needs a documented event-source/deduplication definition before it is used as a business KPI.

**Proposed change:** retain the current privacy-safe event model; add dedicated 7/30/90-day reporting and time-series queries; distinguish website from Android, anonymous from signed-in visitors, unique sessions from unique visitors, and recorded events from verified downloads. Do not collect unnecessary personal data.

### P1 — System status is a visual label, not a health monitor

The sidebar displays a static green status orb and “STUDIO SECURE”. This does not prove the live status of Supabase, Storage, auth, Edge Functions, scheduled jobs or provider connections.

**Proposed change:** replace the static assertion with timestamped health checks and explicit states (healthy, degraded, unavailable, not checked). Probe only safe read-only endpoints and never expose secret values. Keep security posture separate from service availability.

### P1 — Overview hierarchy and mobile header need redesign

The Overview leads with a large promotional hero and an AI feature card. Operational metrics and audience metrics are injected below the catalogue counts. The topbar action container uses wrapping flex layout, which explains the stacked controls visible in the supplied mobile screenshot. The page loads multiple overlapping style layers (`admin.css`, `admin-enhancements.css`, `founder-studio-v2.css`, and `emil-interaction-polish.css`), so changes need cascade and breakpoint regression checks.

**Proposed change:** redesign the first viewport around KPIs, urgent tasks, recent activity and quick actions. Keep the HTE logo and black/metallic-gold/silver identity. Use a compact mobile topbar and a single responsive navigation system. Put AI tools inside their own workspaces rather than using promotional cards as primary dashboard content.

### P1 — Founder actions lack a unified audit trail

Catalogue mutations, video changes, booking decisions, marketing approvals and integration changes are handled by separate scripts and tables. No unified append-only audit record was verified for who changed what and when.

**Proposed change:** add a founder audit log for high-impact actions with actor, action, entity type/id, timestamp and safe metadata. Do not log credentials, tokens, raw booking messages or unnecessary personal information.

### P2 — Static CI checks are not sufficient for Founder Studio

The current workflow checks the presence of admin routes and selected source patterns, but does not prove login/authorization, booking management, media CRUD, campaign approval, mobile navigation, analytics loading or failure states.

**Proposed change:** add admin-specific unit/static checks and an isolated preview test plan. Run public-site and Android regression checks on the same PR. Do not publish from this feature branch.

## Recommended target architecture

1. **Founder shell:** one authenticated application shell at `/admin/`, gated by `hte_is_admin()`, with role-aware navigation, compact mobile header, accessible states and one shared session lifecycle.
2. **Workspace modules:** Overview; Music & Releases; Video Library; Artists; Bookings; Enquiries; Audience Analytics; Marketing & Social; AI Ad Studio; System Health; Founder Settings.
3. **Data-access boundary:** centralize browser-safe Supabase calls in small modules. Browser code uses only the publishable key and user session. Privileged operations remain in Edge Functions or guarded private-schema RPCs.
4. **Booking adapter:** read both current booking sources; preserve source IDs and provenance; write legacy workflow state separately; do not silently migrate or discard existing submissions.
5. **Marketing delivery:** draft → founder approval → validated queue → scheduled/attempted → published/failed/manual-share. Provider capability and connection status must be proven before a channel can be selected for publishing.
6. **Analytics:** retain the existing event table and guarded RPC, document event semantics, add bounded date-range aggregations and charts, and clearly label estimates/coverage.
7. **Operations and audit:** real health probes, last-checked timestamps, actionable error states, and append-only audit records for sensitive founder actions.
8. **Design system:** consolidate duplicate CSS ownership, preserve the HTE mark and palette, and test small Android-sized screens as well as desktop.

## Backend change proposal — approval gate

The following additive backend changes are recommended, but **none have been applied**:

- Add a `hte_booking_workflow` table for workflow state attached only to legacy guest booking requests stored in `contact_messages` (keyed by message ID), with strict owner/admin RLS and explicit grants. Member/native booking status remains in `bookings`.
- Add guarded, read-only analytics RPCs for time-series and source/platform breakdowns, with bounded date windows and documented deduplication.
- Standardize Meta and future publishing Edge Functions on the shared role authorization RPC; remove hard-coded fallback identity assumptions after confirming the founder role path.
- Add a minimal `hte_admin_audit_log` table and guarded server-side insert path.
- Review `hte_social_connections` token strategy before OAuth is introduced. Do not place access/refresh tokens in browser-readable rows or metadata.
- Confirm every new public-schema table/function has explicit minimum grants and RLS because Data API exposure defaults changed in 2026.

Before any production DDL or Edge Function deployment, create an isolated Supabase development branch (if available and cost-acceptable), test migrations and RLS there, run Supabase advisors, verify the founder and non-founder authorization cases, and only then propose a production rollout. No production schema change is included in this review.

## Non-regression boundaries

- Do not edit Android application source or build/release workflows unless a separately approved integration requires it.
- Do not replace public website routes, shared catalogue/player code, auth callbacks or the current Supabase project.
- Keep public booking submission, member booking, account downloads, catalogue playback, artist/release visibility and Android media/auth paths working.
- Keep the official GitHub Pages deployment as the production target; the feature branch must not deploy to production.
- Do not merge the PR until code review, CI, preview tests, RLS tests and mobile/desktop checks pass.

## Audit caveats

This review used the GitHub `main` snapshot and read-only live Supabase metadata, schema, policy, migration, function and advisor inspection. It did not perform a live OAuth connection, publish a social post, submit a booking, modify production data, run a browser-based end-to-end session, or inspect secret values. Those remain explicit test tasks for implementation and sign-off.
