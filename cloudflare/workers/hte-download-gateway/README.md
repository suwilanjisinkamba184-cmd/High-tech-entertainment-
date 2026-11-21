# HTE signed temporary-download gateway

This Worker is a dedicated ingress for public website audio downloads. It does not replace or modify the existing `high-tech-entertainment` Worker.

## Security boundary

- The Worker obtains the client address from Cloudflare's immutable `request.cf.connectingIP` metadata. It never trusts a caller-provided IP header.
- It signs the HTTP method, timestamp, one-time nonce, client IP, and exact request body with HMAC-SHA256.
- The Supabase Edge Function rejects direct/unsigned prepare requests, validates the signature and five-minute timestamp window, and reserves the nonce in a unique database column.
- PostgreSQL serializes quota reservations by the verified IP hash and inserts the download job in the same transaction. The limit is 20 jobs per rolling hour.
- Scheduled cleanup remains a separate GitHub OIDC-authenticated action and does not pass through this Worker.

## Required deployment configuration

1. Generate one high-entropy secret (at least 32 random bytes).
2. Configure the same value as the Cloudflare Worker secret `HTE_DOWNLOAD_PROXY_SECRET` and the Supabase Edge Function secret `HTE_DOWNLOAD_PROXY_SECRET`. Never put it in source control or a browser bundle.
3. Deploy this Worker as `hte-download-gateway` with the supplied `wrangler.toml`.
4. Apply the migration `supabase/migrations/20261121170000_hte_atomic_download_quota.sql` before deploying the updated Edge Function.
5. Deploy `hte-download-delivery` with `verify_jwt = false` retained: the function performs its own HMAC verification for prepare requests and GitHub OIDC verification for cleanup.
6. Deploy the website update that calls `https://hte-download-gateway.suwilanjisinkamba184-d77.workers.dev`.
7. Smoke-test a legitimate website download, a direct unsigned Supabase prepare call (must return 401), an expired signature, a replayed nonce (must return 409), and concurrent quota-boundary requests (must never exceed 20).

## Rollback

If the gateway is not healthy, disable the website download action or roll the website back to a non-download fallback while keeping the Edge Function's signed-request requirement in place. Do not restore the old unsigned prepare endpoint. Do not remove the new database column or nonce index; they are additive and safe for existing jobs. Do not deploy the new Edge Function before both secrets and the Worker are configured, because it intentionally fails closed when the shared secret is missing.
