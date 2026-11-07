# Secure founder-controlled OTA releases

This feature branch establishes the OTA security design. Production activation is intentionally gated until the signing keys, migration, app-side verification and Firebase push configuration are complete.

## Ownership and publishing
- Keep repository write/admin access limited to the founder. The repository is currently public, so source code can be read and copied; public visibility does not grant release publishing authority.
- Release signing keys must remain in GitHub Actions or Supabase secrets and must never be committed or embedded in the app.
- Founder authorization must be verified on the server using the authenticated Supabase user ID. A Flutter-side ID check is not an authorization boundary.

## Update protocol
Each stable/beta release must carry package name, version name/code, minimum supported version code, mandatory flag, release notes, HTTPS APK URL, SHA-256, publication timestamp and Ed25519 signature over a deterministic JSON payload. The Android client must verify the signature with an embedded public key, verify the APK SHA-256, reject downgrade/wrong package/wrong channel, and rely on Android's installer to enforce matching APK signing certificate.

## Update modes
- Optional: user may postpone.
- Mandatory: app blocks normal use only after a newer signed manifest is verified and an installation path is available. Explain the reason and provide retry/install actions.

## Required activation steps
1. Apply the accompanying Supabase migration.
2. Set the Ed25519 PKCS#8 private key as a Supabase Edge Function secret; put only the matching public key in Android build configuration.
3. Deploy and test the founder-authorized release endpoint.
4. Add client-side signature verification, hash verification, download progress, Android install-permission handling and downgrade protection.
5. Configure Firebase Cloud Messaging and test delivery on a real Android device before claiming push updates are enabled.
6. Configure GitHub branch protection and release-environment approvals; restrict repository write/admin access to the owner.

No OTA release is enabled by this documentation alone. Do not publish mandatory updates until all activation tests pass.