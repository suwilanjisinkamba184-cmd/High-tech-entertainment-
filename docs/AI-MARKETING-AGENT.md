# High-Tech Entertainment AI Marketing Agent

## Goal
Add a Founder Studio marketing workspace shared by the GitHub Pages website and Android app, backed by the existing Supabase project. The agent prepares daily branded campaigns, stores media and copy, and distributes approved-format content through official platform APIs after the founder connects the relevant accounts.

## Existing infrastructure confirmed
- Existing Supabase project remains the system of record; do not replace Auth, profiles, catalogue, bookings, or Founder Studio.
- Existing `elevenlabs-ad-studio` Edge Function (version 14 at inspection) generates voice advertisements and saves drafts to `audio_ads` / `hte-audio`.
- Public website is hosted from the existing GitHub Pages repository.
- No social publishing connections were present in the inspected Supabase Edge Function list.

## Supported publishing plan
| Platform | Planned path | Requirements / limitations |
|---|---|---|
| Instagram | Meta Graph API publishing | Professional account, Meta app, account/page linkage, publishing permissions and app review where required. Reels need a publicly fetchable video asset in a supported format. |
| Facebook | Meta Graph API Page publishing | Facebook Page, Page token and relevant permissions/app review. |
| TikTok | Content Posting API | TikTok developer app, creator OAuth, scopes and audit; unaudited direct posts may be restricted to private visibility. |
| YouTube | YouTube Data API video upload | Google OAuth, YouTube channel and API project/quota; uploads may be subject to unverified-project restrictions. |
| X | X API v2 create-post/media upload | OAuth and paid/usage-based API access may be required; not assumed free. |
| WhatsApp Business | WhatsApp Business Platform Cloud API | Business phone number, Meta app, approved templates/opt-in for business-initiated marketing messages; message delivery may incur fees. |
| WhatsApp Status | Assisted share workflow only | No supported public API for ordinary WhatsApp Status publishing was verified. Generate the vertical media and caption, then open the native WhatsApp share flow for the founder to post manually. Do not automate WhatsApp Web or use unofficial clients. |

## Security rules
- Never put provider access tokens, app secrets, refresh tokens, or service-role keys in browser code, GitHub, or ordinary database rows.
- Keep provider secrets in Supabase Edge Function secrets / Vault. Store only account IDs, labels, scopes, connection state and non-sensitive metadata in `hte_social_connections`.
- All campaign, connection, and job tables are founder-admin-only via `public.hte_is_admin()` and RLS.
- Automatic publishing must only send founder-authorized campaigns and WhatsApp messages to opted-in recipients. Respect platform limits, templates, rate limits, and user opt-outs.
- Provide per-platform enable/disable switches, idempotency, retry limits, audit logs, and a global pause switch.
- No platform should be marked connected until OAuth succeeds and a test call confirms the granted scopes.

## Daily campaign workflow
1. Generate a campaign pack (Reel script/storyboard, poster copy, captions, hashtags, CTA, WhatsApp share variant) using available approved brand assets and published catalogue data.
2. Save draft and generated media to Supabase Storage.
3. Validate each platform's media format, caption limits, account connection, consent and publishing permissions.
4. Queue one publish job per enabled destination, with idempotency and retry limits.
5. Publish only through official APIs. Route WhatsApp Status to manual native share.
6. Record provider post IDs, timestamps, errors and engagement metrics where available.

## Free-first policy
Use free/test tiers for development, local generation and test accounts where possible. Do not promise fully free production publishing: X API usage, WhatsApp message delivery, AI generation, video rendering, storage and hosting can incur charges. Require explicit budget configuration before any paid API usage.

## Not yet connected
This foundation does not connect any social accounts by itself. The founder must complete provider OAuth / app setup and configure Edge Function secrets before live publishing can be enabled.
