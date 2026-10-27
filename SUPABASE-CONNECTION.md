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

## Launch status
This archive contains the integration and setup scripts, but the database changes must be run in your Supabase project and the deployed site must be tested by an account with project access. A successful SQL Editor message alone does not prove that every frontend workflow works.
