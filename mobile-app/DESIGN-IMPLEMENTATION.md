# HT Music and HT Admin — Design and implementation

This branch carries the expanded High-Tech Entertainment mobile experience into the existing Flutter Android application.

## Design prototypes

- [HT Music — MagicPath prototype](https://designs.magicpath.ai/v1/lively-room-4794)
- [HT Admin — MagicPath prototype](https://designs.magicpath.ai/v1/cleverly-brook-4577)
- The original project workspace: https://www.magicpath.ai/files/468084098691395584

The React/TSX files in `design-reference/` are design references only. They are not imported into the Flutter runtime.

## Native Flutter implementation

- `lib/main.dart`: existing HT Music app retained; home hero refined and a Continue Listening entry added.
- `lib/admin_studio_page.dart`: private Founder Studio dashboard.
- The signed-in account menu exposes Founder Studio only to `suwilanjisinkamba184@gmail.com`.
- The admin page checks the active Supabase session again before showing founder tools.
- Track publish/unpublish actions update the existing `tracks.is_published` field.

## Existing backend and security

The app continues using the existing Supabase project and existing `tracks`, `artists`, and `releases` tables. The mobile client uses the publishable key only. Supabase Row Level Security is the security boundary; the email check in the UI is not a replacement for server-side RLS policies.

## Not yet connected

- Binary audio/artwork file picker and Storage upload flow.
- Full create/edit/delete workflows for artists and releases.
- Protected server-side user administration.
- Event-backed analytics and audit log.
- Native build and device QA.

Do not place a Supabase service-role key in the Android application.
