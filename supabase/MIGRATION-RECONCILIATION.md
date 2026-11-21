# Supabase migration reconciliation inventory

**Project:** `wqrxacjbsldbnoekbkbf`  
**Audit date:** 2026-11-21  
**Live migration ledger:** 53 records  
**Status:** NOT REPRODUCIBLE YET — this is an inventory, not a claim that the history is repaired.

The live ledger was read from the connected Supabase project and compared with the repository SQL inventory. The repository contains 8 formal migrations before the new quota migration, plus supplemental SQL scripts and consolidated baseline files. Several live migration versions have no isolated, ordered source migration in the repository. Some are only represented by combined scripts, and those are marked as candidates rather than exact equivalents.

Do not reset the live database, replay candidate scripts, or mark missing migrations as applied based on this inventory. The next safe step is to recover the original SQL for every unrecovered version or produce and review a verified schema baseline from the live database, then validate it against a clean temporary Supabase branch.

| Live version | Live migration name | Repository source evidence |
|---|---|---|
| `20261027232300` | `add_admin_audio_update_policy` | supabase-admin-policies.sql (combined candidate; exact migration not isolated) |
| `20261028002834` | `fix_hte_catalog_schema_and_storage` | supabase-setup.sql (baseline candidate; exact migration not isolated) |
| `20261028002944` | `optimize_hte_admin_rls_policies` | supabase-admin-policies.sql (combined candidate; exact migration not isolated) |
| `20261028102747` | `profiles_and_private_avatars` | SUPABASE-REDESIGN-MIGRATION.sql (candidate; later avatar changes also exist) |
| `20261030180810` | `consolidate_catalog_read_policies` | database/changes/20261030_consolidate_catalog_read_policies.sql |
| `20261030202848` | `add_spotify_release_links` | supabase-setup.sql (combined candidate; exact migration not isolated) |
| `20261030230016` | `add_booking_notification_log` | UNRECOVERED |
| `20261030231014` | `create_public_support_feedback` | UNRECOVERED |
| `20261030231529` | `secure_public_booking_submission_rpc` | UNRECOVERED |
| `20261031232509` | `hte_streaming_features` | UNRECOVERED |
| `20261101121639` | `revoke_anon_personal_app_data_access` | supabase-admin-policies.sql (combined candidate; exact migration not isolated) |
| `20261101121711` | `split_audio_ads_founder_policies` | supabase-admin-policies.sql (combined candidate; exact migration not isolated) |
| `20261101191601` | `hte_role_based_admin_access` | supabase-admin-policies.sql (combined candidate; exact migration not isolated) |
| `20261101191636` | `hte_admin_security_cleanup` | supabase-admin-policies.sql (combined candidate; exact migration not isolated) |
| `20261101191840` | `hte_revoke_anonymous_personal_data_grants` | supabase-admin-policies.sql (combined candidate; exact migration not isolated) |
| `20261101192215` | `hte_rate_limit_booking_notifications` | UNRECOVERED |
| `20261101192359` | `hte_booking_rate_limit_hardening` | UNRECOVERED |
| `20261101194034` | `hte_partner_admin_contact_directory` | database/changes/20261102_add_secure_admin_user_directory.sql (candidate; confirm exact function/policy set) |
| `20261102200232` | `create_hte_artwork_bucket` | database/changes/20261102_create_hte_artwork_bucket.sql |
| `20261102200923` | `allow_admin_artwork_library_listing` | database/changes/20261102_allow_admin_artwork_library_listing.sql |
| `20261102201210` | `add_secure_admin_user_directory` | database/changes/20261102_add_secure_admin_user_directory.sql |
| `20261102201315` | `add_secure_admin_analytics` | database/changes/20261102_add_secure_admin_analytics.sql |
| `20261105020850` | `create_app_notifications` | database/changes/20261105_app_notifications_and_video_storage.sql (combined candidate) |
| `20261105020932` | `ensure_hte_videos_storage` | database/changes/20261105_app_notifications_and_video_storage.sql (combined candidate) |
| `20261105021142` | `restrict_app_notification_targets` | database/changes/20261105_app_notifications_and_video_storage.sql (combined candidate; exact restriction migration not isolated) |
| `20261107070030` | `native_android_bookings_workflow` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate; two live versions share this name) |
| `20261107070158` | `native_android_bookings_workflow` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate; two live versions share this name) |
| `20261107070320` | `native_android_public_avatar_delivery` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate) |
| `20261107071201` | `native_android_release_descriptions` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate) |
| `20261107071316` | `restrict_anonymous_booking_privileges` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate) |
| `20261107071614` | `least_privilege_native_bookings` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate) |
| `20261107071850` | `native_founder_access_check_rpc` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate) |
| `20261107072031` | `set_founder_check_rpc_invoker` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate) |
| `20261107084008` | `native_booking_status_only_updates` | database/2026-11-07-high-tech-entertainment-3.1.sql (combined candidate) |
| `20261108025145` | `secure_member_booking_notifications_20261108` | database/changes/20261108_secure_member_booking_notifications.sql |
| `20261108030703` | `consolidate_contact_messages_insert_policy` | database/changes/20261108_03_consolidate_contact_messages_insert_policy.sql |
| `20261108031032` | `guest_booking_rpc_compatibility` | database/changes/20261108_04_guest_booking_compatibility.sql |
| `20261108031111` | `guest_booking_rpc_definer` | database/changes/20261108_05_guest_booking_rpc_definer.sql |
| `20261108032148` | `private_guest_booking_rpc` | database/changes/20261108_06_private_guest_booking_rpc.sql |
| `20261108032308` | `move_admin_rpc_private` | database/changes/20261108_07_move_admin_rpc_private.sql |
| `20261108230111` | `hte_ai_marketing_agent` | supabase/migrations/20261109090000_hte_ai_marketing_agent.sql (version differs from live ledger) |
| `20261108230316` | `hte_marketing_created_by_idx` | supabase/migrations/20261109093000_hte_marketing_created_by_idx.sql (version differs from live ledger) |
| `20261116162504` | `hte_catalogue_audience_download_branding_20261116` | database/changes/20261116_01_catalogue_audience_download_branding.sql |
| `20261116162608` | `index_hte_audience_artist_events_20261116` | UNRECOVERED (may be embedded in another script; not confirmed) |
| `20261116162721` | `hte_download_branding_settings_20261116` | database/changes/20261116_02_download_branding_settings.sql |
| `20261120082626` | `public_artist_drafts_discoverable_20261120` | supabase/migrations/20261120082626_public_artist_drafts_discoverable_20261120.sql |
| `20261120083422` | `split_download_branding_read_and_admin_policies_20261120` | supabase/migrations/20261120083422_split_download_branding_read_and_admin_policies_20261120.sql |
| `20261120180335` | `enable_realtime_for_releases_and_videos` | supabase/migrations/20261120180335_enable_realtime_for_releases_and_videos.sql |
| `20261121153907` | `hte_secure_temporary_downloads_20261121` | database/changes/20261121_01_secure_temporary_downloads.sql |
| `20261121154050` | `hte_temp_download_limit_100mb_20261121` | database/changes/20261121_02_expand_temp_download_limit.sql |
| `20261121154406` | `hte_temp_download_allow_flac_20261121` | database/changes/20261121_03_allow_flac_temp_downloads.sql |
| `20261121161222` | `hte_download_jobs_track_idx_and_contact_select_policy_20261121` | database/changes/20261121_04_optimize_download_jobs_and_contact_policies.sql |
| `20261121162206` | `hte_download_jobs_service_role_policy_20261121` | database/changes/20261121_05_scope_download_job_policy_to_service_role.sql |

## New source migration (not yet applied)

- `supabase/migrations/20261121170000_hte_atomic_download_quota.sql`
- Adds a unique one-time request nonce and an atomic rolling-hour quota reservation function.
- It is intentionally not included in the live 53-record count because it has not been applied.

## Release gate

This issue remains open until all live migration versions are mapped to replayable source SQL or a reviewed, reproducible baseline. The new incremental migration must then be tested on a clean database before any production application.
