# Supabase connection and launch checklist

The frontend is configured for the Supabase project already present in `public/supabase-config.js`. That file contains only the project URL and a browser-safe publishable key.

## Current project status
The connected Supabase project already has the `artists`, `releases`, `tracks`, and `contact_messages` tables with row-level security enabled, and the `hte-audio` public bucket now exists. Admin upload, update, and delete policies are configured for `suwilanjisinkamba184@gmail.com`. The website config targets that existing project.

## Setup only if recreating or repairing a project
1. Run `supabase-setup.sql` only in a new/empty project or after reviewing its effects. It creates the tables, baseline RLS policies, and `hte-audio` bucket.
2. Run `supabase-admin-policies.sql` to install or refresh admin policies and the required SQL grants. It is safe to rerun.
3. In Authentication, ensure the administrator user exists with this exact email and can sign in. Disable public sign-ups if this is a single-admin site.
4. In Storage, confirm the bucket ID `hte-audio` exists. The additional bucket called `HT Storage` can remain, but this website does not use it. The code and SQL deliberately use the stable ID `hte-audio`.
5. Open the site over HTTPS. Test public music loading, admin login, audio upload, playback, and the contact form.

## Security
Never put a Supabase `service_role` or `sb_secret_` key in browser files. The `sb_publishable_` key in `public/supabase-config.js` is intended for browser use; row-level security policies are essential. Do not share passwords or secret keys in chat.


## Booking workflows

The live project now has two separate booking paths:

- **New guest booking:** the redesigned Astro homepage calls the `public-booking-request` Edge Function. JWT verification is disabled intentionally because guests do not sign in. The function validates fields, checks the website-origin allowlist, uses a honeypot, applies a per-IP limit of 5 requests per 15 minutes, writes the request server-side, and sends the email notification server-side.
- **Member booking:** signed-in members use the `submit_booking_request` RPC. It binds the booking to `auth.uid()`. The `booking-notification` Edge Function validates the member JWT internally and checks that the booking belongs to that user before sending an email.
- **Legacy guest compatibility:** the older static homepage still calls `submit_booking_request` followed by `booking-notification`. The RPC is a narrowly scoped, insert-only `SECURITY DEFINER` function with an empty search path: guests receive `user_id = NULL`, members are bound to `auth.uid()`, and callers cannot read messages or supply another user's ID. The notification function accepts guest rows only when `user_id` is NULL, the request is recent, the origin is an allowed website, and the IP rate limit passes. This compatibility path prevents the current live homepage from losing guest bookings before the redesign is merged.
- **Public contact:** the contact form remains separate and can create a contact message with no user ID. Anonymous users cannot read the contact inbox or invoke admin RPCs.

The service-role key and Resend key must remain in Supabase Edge Function secrets. Never add them to `supabase-config.js`, website source, or GitHub.


### Reviewed Supabase Advisor findings

- The duplicate permissive INSERT policies on `contact_messages` were consolidated into one policy. Anonymous inserts are limited to rows with `user_id IS NULL`; authenticated booking rows must use the caller's own `auth.uid()` and a booking subject.
- The `SECURITY DEFINER` RPC warnings are intentional: `hte_admin_get_analytics` and `hte_admin_list_users` are admin-guarded Founder Studio operations; `submit_booking_request` is the narrow insert-only legacy guest-compatibility endpoint. The admin RPCs use an empty search path, check the private owner/admin role guard, and are not executable by `anon`. The public booking wrapper is `SECURITY INVOKER`; its private helper uses an empty search path, accepts only validated booking fields, derives `user_id` from `auth.uid()`, and returns only the newly created ID. The private schema is not exposed through the Data API. Do not remove these controls without replacing the corresponding workflows.
- Supabase Auth's leaked-password protection is still disabled. Enable it in **Authentication → Settings → Password Security** if available for the project plan.
- Unused-index notices are informational. Keep the indexes until production query usage shows that they are genuinely unnecessary.

## Current deployment state

The booking database migration and both Edge Functions have been applied to the linked Supabase project. The website redesign is still under pull request review and has not been merged or deployed to the live website. Complete the GitHub Actions checks and test guest booking, member booking, contact submission, catalogue playback, downloads, and Founder Studio on the Pages preview before merging.

## Launch status
The booking migration and Edge Functions are already applied to the linked Supabase project. The website PR still requires final automated checks and real-device/browser verification; a successful build alone does not prove every frontend workflow works.
