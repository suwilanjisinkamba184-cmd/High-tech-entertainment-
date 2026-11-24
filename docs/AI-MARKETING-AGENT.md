# High-Tech Entertainment AI Marketing Agent

## Product intent
Provide a Founder Studio marketing workspace that helps prepare branded campaign assets and copy using approved HTE brand assets and published catalogue information. The existing Supabase project remains the system of record; do not replace Auth, profiles, catalogue, bookings or Founder Studio.

## Current integration boundary
The repository contains an `elevenlabs-ad-studio` Edge Function and related marketing schema/migration sources. Their presence does not prove that every AI provider, social account or publishing destination is connected. Verify deployed function state, provider credentials, OAuth grants and a successful live test before marking any integration as active.

The marketing workflow must remain separate from the normal music catalogue and member account permissions. Founder-only campaign operations require server-side role checks and RLS.

## Publishing readiness
| Destination | Integration path | Required verification |
|---|---|---|
| Instagram | Meta Graph API | Professional account, Meta app, account linkage, approved permissions and media-format validation |
| Facebook Page | Meta Graph API | Page ownership, Page token, publishing permission and live test post |
| TikTok | Content Posting API | Developer app, creator OAuth, required scopes, audit and visibility restrictions |
| YouTube | YouTube Data API | Google OAuth, channel authorization, quota and upload restrictions |
| X | X API | OAuth, media permissions and current usage/price limits |
| WhatsApp Business | WhatsApp Business Platform | Business number, templates/opt-in, permissions and message-cost controls |
| WhatsApp Status | Manual native share | Generate the approved asset and let the founder share it; do not automate WhatsApp Web or unofficial clients |

No destination is considered connected until OAuth succeeds, the granted scopes are checked and a harmless test call succeeds.

## Campaign workflow
1. Prepare a campaign brief, vertical-video script/storyboard, poster copy, captions, hashtags and call to action.
2. Use only approved brand assets and content HTE is authorized to promote.
3. Save drafts and generated media to the intended private or public Storage location according to its access requirements.
4. Validate destination media formats, caption limits, account permissions, consent and publication settings.
5. Require founder approval before a publish job is queued.
6. Publish only through official provider APIs; use manual native sharing where no supported API exists.
7. Record provider IDs, timestamps, outcomes and errors. Use idempotency and bounded retries.

## Security and cost controls
- Keep API keys, OAuth tokens, refresh tokens and service-role credentials in server-side secrets/Vault.
- Store only necessary non-secret connection metadata in the database.
- Enforce founder-only access with RLS and server-side checks.
- Provide per-platform disable switches, audit logs, rate limits and a global pause.
- Require an explicit budget limit before paid AI, video rendering, messaging or social API usage.
- Do not promise fully free production publishing. Provider access, storage, AI generation and message delivery can incur charges.

## Acceptance
Test draft creation, media access, approval, publish authorization, duplicate-job protection, provider failure, retry behavior and global pause. Until these tests and provider connections are verified, describe social publishing as planned or partially integrated—not live.
