import { readFileSync } from "node:fs";
import assert from "node:assert/strict";

const read = (path) => readFileSync(new URL("../" + path, import.meta.url), "utf8");
const admin = read("admin/index.html");
const controller = read("assets/founder-command-centre.js");
const styles = read("assets/founder-command-centre.css");
const migration = read("supabase/migrations/20261121190000_founder_studio_operations.sql");
const publish = read("supabase/functions/meta-publish/index.ts");
const check = read("supabase/functions/meta-connection-check/index.ts");

for (const asset of ["founder-command-centre.css", "founder-command-centre.js"]) {
  assert.ok(admin.includes(asset), "Founder Studio must load " + asset);
}
for (const view of ["bookings", "audience", "system", "audit"]) {
  assert.ok(controller.includes("['" + view + "'"), "Missing workspace: " + view);
}
assert.ok(controller.includes("hte_is_admin"), "Every new workspace must verify founder role");
assert.ok(controller.includes("hte_admin_get_analytics"), "Audience workspace must use guarded analytics RPC");
assert.ok(controller.includes("hte_booking_workflow"), "Guest booking workflow must use workflow metadata");
assert.ok(controller.includes("hte_admin_audit_log"), "Founder actions must use audit log");
assert.ok(styles.includes("@media(max-width:480px)"), "Mobile layout rules are required");
assert.ok(migration.includes("enable row level security"), "New tables must enable RLS");
assert.ok(migration.includes("append-only"), "Audit log must be append-only");
assert.ok(migration.includes("least(coalesce"), "Analytics date range must be bounded");
assert.ok(!publish.includes("b268df9e-eff5-43b9-a38f-869b33add38b"), "Meta publisher must not hard-code founder identity");
assert.ok(!check.includes("Access-Control-Allow-Origin\":\"*"), "Meta connection check must not use wildcard CORS");
assert.ok(publish.includes("hte_is_admin") && check.includes("hte_is_admin"), "Both Meta functions must use role-based founder authorization");
console.log("Founder Studio static architecture checks passed.");
