# High-Tech Entertainment Music Platform

Responsive High-Tech Entertainment music website integrated with Supabase Auth, Postgres tables, Storage audio uploads, and contact submissions.

## Requirements
- Node.js 18 or newer
- npm

## Run locally
1. Extract this project folder.
2. Run `npm install`.
3. Copy `.env.example` to `.env` and set `ADMIN_UPLOAD_TOKEN` to a random secret of at least 32 characters. Never commit `.env`.
4. Set the environment variables in your shell or hosting provider. Node does not load `.env` automatically unless you configure a dotenv loader; for local development you can use `node --env-file=.env server.js` on supported Node versions.
5. Run `npm start` (or `node --env-file=.env server.js`).
6. Open `http://localhost:3000`.

## Security changes
- Uploads require the `X-Admin-Token` header matching the server-only `ADMIN_UPLOAD_TOKEN`; uploads are disabled if the token is not configured.
- Upload endpoint and signup endpoint have basic per-IP rate limits.
- Audio uploads are limited to 100 MB, restricted by extension and MIME type, then checked for basic file signatures. Uploads remain disabled unless the server token is at least 32 characters long.
- JSON writes use a temporary file and atomic rename; server responses include security headers, a restrictive Content Security Policy, and central error handling. The upload token is not retained in the page after upload.

## Important production limitations
- The upload token is a lightweight single-admin gate, not a user-management system. For a public launch, use Supabase Auth with admin role checks and Row Level Security.
- Local JSON files and `public/uploads` are not durable storage on ephemeral hosts and are not suitable for multi-instance deployments. Migrate tracks and signups to Supabase Database and audio to Supabase Storage before production.
- Add HTTPS at the hosting layer, backups, monitoring, and abuse protection. Rate limiting in this starter is in-memory and resets on restart.
- Validate that you have distribution rights for every track you publish.

## API
- `GET /api/health` — health check
- `GET /api/tracks` — public track catalogue
- `POST /api/upload` — administrator-only multipart upload (`audio`, `uploader`, optional `artist`, optional `title` fields; send `X-Admin-Token` header)
- `POST /api/signup` — email signup


## Supabase connection
- Browser config is in `public/supabase-config.js` and contains only the project URL and browser-safe publishable key. Never add a service-role or `sb_secret_` key to browser code.
- The public catalogue reads published rows from `public.tracks`; audio files use the `hte-audio` Storage bucket.
- Admin sign-in uses Supabase Auth. Contact submissions insert into `public.contact_messages`.
- Run `supabase-setup.sql` first, then `supabase-admin-policies.sql`. The admin policy file is preconfigured for `suwilanjisinkamba184@gmail.com`.
- Ensure the admin account exists with that exact email. Disable public sign-ups if this is a single-admin site.
- The separately created `HT Storage` bucket is not used by this version. The SQL creates the `hte-audio` bucket expected by the website; you may leave `HT Storage` in place.
- Existing local audio files in `public/uploads/` are not automatically copied into Supabase Storage. Upload them through the admin form after the database setup.
- Serve the website over HTTPS and test sign-in, upload, playback, and contact submissions before public launch.

See `SUPABASE-CONNECTION.md` for the exact setup order and verification checklist.
