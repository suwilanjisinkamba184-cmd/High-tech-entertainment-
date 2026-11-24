# Supabase migration reconciliation

**Project reference:** `wqrxacjbsldbnoekbkbf`  
**Repository inventory checked:** 24 November 2026  
**Current source inventory:** 63 SQL files under `supabase/migrations/`

## Status and limits
This document is a repository-source inventory, not a live database attestation. The previous detailed ledger snapshot was captured on 21 November 2026 and reported 53 live migration records and 62 repository migration files at that time. The source tree now contains 63 SQL migration files. The live migration ledger has not been re-read as part of this documentation update, so the old 53-record figure, 53/53 coverage statement and old list of seven pending files must not be treated as current.

The presence of a SQL file does not prove it has been applied. A matching migration version does not prove the SQL can replay cleanly from an empty database. Some earlier files were reconstructed or consolidated and require clean replay testing.

## Production safety
- Never reset, drop, recreate or bulk-replay the production database.
- Do not apply any migration based only on its filename or an old checklist.
- Before a production change, compare the live migration ledger, inspect the exact SQL diff, validate on a disposable Supabase branch/database and obtain explicit approval.
- Preserve existing user, catalogue, booking, media and storage data.
- Keep RLS, grants, function security mode/search_path, triggers, Storage policies and Realtime publication in the validation scope.

## Required reconciliation procedure
1. Export/read the current production migration ledger without changing the database.
2. Inventory every file in `supabase/migrations/` and map it to a unique migration version.
3. Identify missing source versions, duplicate versions, no-op markers, reconstructed SQL and pending migrations.
4. Build a disposable development database and apply the intended source sequence in chronological order.
5. Compare tables, columns, constraints, indexes, functions, triggers, grants, RLS policies, Storage buckets and Realtime publication with the intended schema.
6. Run the repository SQL tests, including privilege and temporary-download quota tests where applicable.
7. Record the exact commit, database branch, ledger snapshot, test output and unresolved differences.
8. Only then propose a separately reviewed production migration.

## Historical snapshot
The 21 November inventory below is retained as historical context only. Its migration table and pending-file classifications were prepared against the earlier 53-record live ledger and 62-file source tree. They require a fresh reconciliation before operational use. Do not follow the old “pending” list without rechecking the current live ledger and current source files.

## Current documentation-only update
This update changes documentation only. It does not execute SQL, inspect or alter production data, apply migrations, change RLS or Storage policies, or certify a clean-room replay.
