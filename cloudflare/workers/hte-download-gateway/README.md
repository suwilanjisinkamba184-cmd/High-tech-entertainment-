# HTE signed temporary-download gateway

## Purpose and boundaries
The `hte-download-gateway` Worker is a dedicated ingress for the HTE signed temporary-download flow. It is not the public website host and does not replace the existing `high-tech-entertainment` Worker. GitHub Pages remains the canonical public website host.

The Worker and the Supabase Edge Function form one security boundary. Do not weaken the signed-request contract or reintroduce an unsigned public prepare endpoint as a fallback.

## Configuration
Worker configuration is in `wrangler.toml`. The current file sets the Worker name, entry point, compatibility date, Workers Dev setting and Supabase project URL. The HMAC secret is a secret, not a `vars` value.

Configure the same high-entropy secret as `HTE_DOWNLOAD_PROXY_SECRET` in the Cloudflare Worker secret store and the Supabase Edge Function secret store. Never put it in source control, a public environment variable, a browser bundle or a support message.

## Security contract
The implementation is expected to:
- Derive the connecting client address from Cloudflare request metadata rather than a caller-supplied IP header.
- Authenticate the method, timestamp, nonce, client address and exact request body using HMAC-SHA256.
- Reject invalid signatures and timestamps outside the accepted window.
- Reserve one-time nonces to prevent replay.
- Apply quota reservation atomically in the database so concurrent requests cannot bypass the limit.
- Keep scheduled cleanup separate from the public download request path and authenticate that maintenance action independently.

Before changing any of these rules, inspect both `src/index.js` and the matching Supabase Edge Function source. The documentation is not a substitute for code review or a live security test.

## Deployment sequence
1. Review the Worker source, `wrangler.toml`, Edge Function source and related SQL migrations together.
2. Confirm the development Supabase branch contains the required schema and security changes.
3. Run the repository download/security SQL tests on that development branch.
4. Configure both copies of the shared secret without printing it in logs.
5. Deploy the Worker and Edge Function only through their reviewed deployment process.
6. Test a legitimate signed request, an unsigned request, an expired signature, a replayed nonce and concurrent quota-boundary requests.
7. Verify the website's download flow against the deployed gateway.

Do not assume a migration is applied merely because a SQL file exists. Check the target environment's migration ledger before deployment. Do not deploy the new function before its required secrets and Worker are configured; the signed flow should fail closed when configuration is missing.

## Rollback
If the gateway is unhealthy, disable or hide the website download action and investigate. Do not restore an unsigned prepare endpoint, expose the shared secret or remove nonce/quota protections as a quick fix. Any schema rollback requires a separate reviewed migration plan.
