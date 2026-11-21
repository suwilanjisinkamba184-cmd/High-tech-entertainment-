# Founder Studio implementation notes

The first implementation slice adds a responsive Founder Command Centre.

## Included in source
- Compact responsive header rules and reduced Overview hero dominance.
- Dedicated Bookings, Audience Analytics, System Health and Founder Activity Log workspaces.
- Unified display of structured member/Android bookings and guest website booking requests without moving source records.
- Explicit analytics labels distinguishing recorded events from verified downloads or plays.
- Read-only authentication, catalogue database, storage and social-connection checks.
- Additive SQL migration draft for guest booking workflow metadata, append-only audit records and a bounded 1–90 day audience series RPC.
- New data calls require an authenticated founder session and the existing hte_is_admin() role check.
- No browser-side secrets, production DDL, Edge Function deployment, public route changes or Android source changes.

## Backend deployment gate
The SQL migration is source-only and has not been applied to production. A Supabase development branch requires a cost quote and explicit user approval before creation. The live meta-publish Edge Function still contains a hard-coded founder UUID check; standardize it to auth.getUser() plus hte_is_admin() in an isolated environment before enabling live publishing.

## External configuration
Supabase Auth leaked-password protection is disabled in the connected project's Security Advisor. This is an Auth dashboard setting and has not been changed. Enable it under Authentication → Settings → Password Security if supported by the project's plan.

## Verification status
Source and schema were inspected. UI and migration have not yet been deployed or browser-tested. CI, RLS tests and cross-product regression checks remain required before merge.
