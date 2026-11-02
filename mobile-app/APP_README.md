# HT Music and HT Admin

High-Tech Entertainment ships one native Flutter Android application with two experiences.

- **HT Music** is the public listener experience.
- **HT Admin** is a private founder studio exposed only to the configured owner account and additionally protected by Supabase RLS.

The application uses the existing Supabase project, publishable key, Auth, Postgres tables and Storage buckets. It never embeds a service-role key.

See [README.md](README.md) for screen inventory, backend mapping, build instructions and signing requirements.
