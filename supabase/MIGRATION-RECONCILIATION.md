# Supabase migration reconciliation inventory

**Project:** `wqrxacjbsldbnoekbkbf`  
**Audit date:** 2026-11-21  
**Live migration ledger:** 53 records  
**Repository formal migrations:** 60 files  
**Live version coverage:** 50/53 versions represented

## Status

All 53 live migration versions now have a corresponding source file in `supabase/migrations/`. However, this is **source coverage, not yet a clean-replay certification**. Several early migrations were reconstructed from live catalog metadata, and several related migrations are intentionally consolidated into one source file with no-op version markers. Those files must be tested in chronological order against a clean temporary Supabase database before this is considered fully reproducible.

Do not reset or replay the production database. The production project already records all 53 versions, so matching files should be skipped by version. The seven additional files in the repository are pending/unapplied migrations or new hardening work.

| Live version | Live migration name | Source file | Recovery method |
|---|---|---|---|
| `20261027232300` | `add_admin_audio_update_policy` | `supabase/migrations/20261027232300_add_admin_audio_update_policy.sql` | Reconstructed baseline combining current supabase-setup.sql and supabase-admin-policies.sql |
| `20261028002834` | `fix_hte_catalog_schema_and_storage` | `supabase/migrations/20261028002834_fix_hte_catalog_schema_and_storage.sql` | Consolidated marker; final baseline schema is in 20261027232300 |
| `20261028002944` | `optimize_hte_admin_rls_policies` | `supabase/migrations/20261028002944_optimize_hte_admin_rls_policies.sql` | Consolidated marker; final initial admin policies are in 20261027232300 |
| `20261028102747` | `profiles_and_private_avatars` | `supabase/migrations/20261028102747_profiles_and_private_avatars.sql` | Copied from SUPABASE-REDESIGN-MIGRATION.sql |
| `20261030180810` | `consolidate_catalog_read_policies` | `supabase/migrations/20261030180810_consolidate_catalog_read_policies.sql` | Copied from supplemental SQL |
| `20261030202848` | `add_spotify_release_links` | `supabase/migrations/20261030202848_add_spotify_release_links.sql` | Reconstructed from live schema and setup baseline |
| `20261030230016` | `add_booking_notification_log` | `supabase/migrations/20261030230016_add_booking_notification_log.sql` | Reconstructed from live table, constraints, grants and RLS |
| `20261030231014` | `create_public_support_feedback` | `supabase/migrations/20261030231014_create_public_support_feedback.sql` | Reconstructed from live table, constraints, grants and RLS |
| `20261030231529` | `secure_public_booking_submission_rpc` | `supabase/migrations/20261030231529_secure_public_booking_submission_rpc.sql` | Reconstructed from live function definitions and execute grants |
| `20261031232509` | `hte_streaming_features` | `supabase/migrations/20261031232509_hte_streaming_features.sql` | Reconstructed from live streaming/library tables and RLS |
| `20261101121639` | `revoke_anon_personal_app_data_access` | `supabase/migrations/20261101121639_revoke_anon_personal_app_data_access.sql` | Reconstructed revoke plus role table and helper RPCs, ordered before Founder policies |
| `20261101121711` | `split_audio_ads_founder_policies` | `supabase/migrations/20261101121711_split_audio_ads_founder_policies.sql` | Reconstructed from live audio_ads policies |
| `20261101191601` | `hte_role_based_admin_access` | `supabase/migrations/20261101191601_hte_role_based_admin_access.sql` | Consolidated marker; role table and helper RPCs are in 20261101121639 to satisfy earlier dependencies |
| `20261101191636` | `hte_admin_security_cleanup` | `supabase/migrations/20261101191636_hte_admin_security_cleanup.sql` | Consolidated marker; function grants are in 20261101191601 |
| `20261101191840` | `hte_revoke_anonymous_personal_data_grants` | `supabase/migrations/20261101191840_hte_revoke_anonymous_personal_data_grants.sql` | Consolidated marker; anonymous revokes are in 20261101121639 |
| `20261101192215` | `hte_rate_limit_booking_notifications` | `supabase/migrations/20261101192215_hte_rate_limit_booking_notifications.sql` | Reconstructed from live private rate-limit table and function |
| `20261101192359` | `hte_booking_rate_limit_hardening` | `supabase/migrations/20261101192359_hte_booking_rate_limit_hardening.sql` | Reconstructed from live contact-message trigger |
| `20261101194034` | `hte_partner_admin_contact_directory` | `supabase/migrations/20261101194034_hte_partner_admin_contact_directory.sql` | Copied from supplemental admin directory SQL |
| `20261102200232` | `create_hte_artwork_bucket` | `supabase/migrations/20261102200232_create_hte_artwork_bucket.sql` | Copied from supplemental SQL |
| `20261102200923` | `allow_admin_artwork_library_listing` | `supabase/migrations/20261102200923_allow_admin_artwork_library_listing.sql` | Copied from supplemental SQL |
| `20261102201210` | `add_secure_admin_user_directory` | `supabase/migrations/20261102201210_add_secure_admin_user_directory.sql` | Copied from supplemental SQL |
| `20261102201315` | `add_secure_admin_analytics` | `supabase/migrations/20261102201315_add_secure_admin_analytics.sql` | Copied from supplemental SQL |
| `20261105020850` | `create_app_notifications` | `supabase/migrations/20261105020850_app_notifications_and_video_storage.sql` | Consolidated source containing notifications, video bucket and target policies |
| `20261105020932` | `ensure_hte_videos_storage` | `supabase/migrations/20261105020932_ensure_hte_videos_storage.sql` | Consolidated marker; video bucket included in 20261105020850 |
| `20261105021142` | `restrict_app_notification_targets` | `supabase/migrations/20261105021142_restrict_app_notification_targets.sql` | Consolidated marker; final notification policy included in 20261105020850 |
| `20261107070030` | `native_android_bookings_workflow` | `supabase/migrations/20261107070030_native_android_bookings_workflow.sql` | Consolidated source for native booking, avatar, release description and founder RPC changes |
| `20261107070158` | `native_android_bookings_workflow` | `supabase/migrations/20261107070158_native_android_bookings_workflow.sql` | Consolidated marker; included in 20261107070030 |
| `20261107070320` | `native_android_public_avatar_delivery` | `supabase/migrations/20261107070320_native_android_public_avatar_delivery.sql` | Consolidated marker; included in 20261107070030 |
| `20261107071201` | `native_android_release_descriptions` | `supabase/migrations/20261107071201_native_android_release_descriptions.sql` | Consolidated marker; included in 20261107070030 |
| `20261107071316` | `restrict_anonymous_booking_privileges` | `supabase/migrations/20261107071316_restrict_anonymous_booking_privileges.sql` | Consolidated marker; included in 20261107070030 |
| `20261107071614` | `least_privilege_native_bookings` | `supabase/migrations/20261107071614_least_privilege_native_bookings.sql` | Consolidated marker; included in 20261107070030 |
| `20261107071850` | `native_founder_access_check_rpc` | `supabase/migrations/20261107071850_native_founder_access_check_rpc.sql` | Consolidated marker; included in 20261107070030 |
| `20261107072031` | `set_founder_check_rpc_invoker` | `supabase/migrations/20261107072031_set_founder_check_rpc_invoker.sql` | Consolidated marker; included in 20261107070030 |
| `20261107084008` | `native_booking_status_only_updates` | `supabase/migrations/20261107084008_native_booking_status_only_updates.sql` | Consolidated marker; included in 20261107070030 |
| `20261108025145` | `secure_member_booking_notifications_20261108` | `supabase/migrations/20261108025145_secure_member_booking_notifications_20261108.sql` | Copied from supplemental SQL |
| `20261108030703` | `consolidate_contact_messages_insert_policy` | `supabase/migrations/20261108030703_consolidate_contact_messages_insert_policy.sql` | Copied from supplemental SQL |
| `20261108031032` | `guest_booking_rpc_compatibility` | `supabase/migrations/20261108031032_guest_booking_rpc_compatibility.sql` | Copied from supplemental SQL |
| `20261108031111` | `guest_booking_rpc_definer` | `supabase/migrations/20261108031111_guest_booking_rpc_definer.sql` | Copied from supplemental SQL |
| `20261108032148` | `private_guest_booking_rpc` | `supabase/migrations/20261108032148_private_guest_booking_rpc.sql` | Copied from supplemental SQL |
| `20261108032308` | `move_admin_rpc_private` | `supabase/migrations/20261108032308_move_admin_rpc_private.sql` | Copied from supplemental SQL |
| `20261108230111` | `hte_ai_marketing_agent` | `supabase/migrations/20261108230111_hte_ai_marketing_agent.sql` | Copied from formal source with corrected live version |
| `20261108230316` | `hte_marketing_created_by_idx` | `supabase/migrations/20261108230316_hte_marketing_created_by_idx.sql` | Copied from formal source with corrected live version |
| `20261116162504` | `hte_catalogue_audience_download_branding_20261116` | `supabase/migrations/20261116162504_hte_catalogue_audience_download_branding_20261116.sql` | Copied from supplemental SQL |
| `20261116162608` | `index_hte_audience_artist_events_20261116` | `supabase/migrations/20261116162608_index_hte_audience_artist_events_20261116.sql` | Reconstructed from live index definition; idempotent |
| `20261116162721` | `hte_download_branding_settings_20261116` | `supabase/migrations/20261116162721_hte_download_branding_settings_20261116.sql` | Copied from supplemental SQL |
| `20261120082626` | `public_artist_drafts_discoverable_20261120` | `supabase/migrations/20261120082626_public_artist_drafts_discoverable_20261120.sql` | Existing formal migration |
| `20261120083422` | `split_download_branding_read_and_admin_policies_20261120` | `supabase/migrations/20261120083422_split_download_branding_read_and_admin_policies_20261120.sql` | Existing formal migration |
| `20261120180335` | `enable_realtime_for_releases_and_videos` | `supabase/migrations/20261120180335_enable_realtime_for_releases_and_videos.sql` | Existing formal migration |
| `20261121153907` | `hte_secure_temporary_downloads_20261121` | `supabase/migrations/20261121153907_hte_secure_temporary_downloads_20261121.sql` | Copied from supplemental SQL |
| `20261121154050` | `hte_temp_download_limit_100mb_20261121` | `supabase/migrations/20261121154050_hte_temp_download_limit_100mb_20261121.sql` | Copied from supplemental SQL |
| `20261121154406` | `hte_temp_download_allow_flac_20261121` | `supabase/migrations/20261121154406_hte_temp_download_allow_flac_20261121.sql` | Copied from supplemental SQL |
| `20261121161222` | `hte_download_jobs_track_idx_and_contact_select_policy_20261121` | `supabase/migrations/20261121161222_hte_download_jobs_track_idx_and_contact_policy_20261121.sql` | Copied from supplemental SQL |
| `20261121162206` | `hte_download_jobs_service_role_policy_20261121` | `supabase/migrations/20261121162206_hte_download_jobs_service_role_policy_20261121.sql` | Copied from supplemental SQL |

## Additional source files not in the live 53-version ledger

- `20261109090000_hte_ai_marketing_agent.sql` and `20261109093000_hte_marketing_created_by_idx.sql` are deprecated no-op markers. Their DDL is represented by the exact live-version files `20261108230111` and `20261108230316`.
- The following five migrations are pending and must be validated before production application:
  - `supabase/migrations/20261120103000_shared_video_catalogue_sources.sql`
  - `supabase/migrations/20261120_member_booking_history.sql`
  - `supabase/migrations/20261121140000_backfill_native_video_download_urls.sql`
  - `supabase/migrations/20261121170000_hte_atomic_download_quota.sql`
  - `supabase/migrations/20261121173000_hte_revoke_excess_client_table_privileges.sql`
- `supabase/migrations/20261121204500_hte_contact_rate_limit_search_path.sql`

## Required validation before sign-off

1. Create a clean temporary Supabase branch/database.
2. Apply all 60 formal migration files in timestamp order.
3. Compare tables, columns, constraints, indexes, functions, triggers, policies, grants, storage buckets and realtime publication against the live project.
4. Run the SQL assertions in `supabase/tests/htesecurity_privilege_tests.sql`.
5. Verify the two new pending migrations on the temporary branch before applying either to production.

The inventory must not be treated as proof that the consolidated/reconstructed SQL is identical to the original manual migration statements.
