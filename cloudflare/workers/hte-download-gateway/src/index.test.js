import test from "node:test";
import assert from "node:assert/strict";
import worker from "./index.js";

const ORIGIN = "https://suwilanjisinkamba184-cmd.github.io";
const BODY = JSON.stringify({ action: "prepare", trackId: "00000000-0000-4000-8000-000000000001" });

function request(headers = {}) {
  return new Request("https://hte-download-gateway.example.workers.dev", {
    method: "POST",
    headers: { Origin: ORIGIN, apikey: "public-test-key", "Content-Type": "application/json", ...headers },
    body: BODY
  });
}

test("ignores caller-supplied IP headers when Cloudflare request metadata is absent", async () => {
  const response = await worker.fetch(
    request({ "cf-connecting-ip": "198.51.100.44", "x-forwarded-for": "198.51.100.44" }),
    { HTE_DOWNLOAD_PROXY_SECRET: "test-only-secret", SUPABASE_URL: "https://example.supabase.co" }
  );
  assert.equal(response.status, 503);
  assert.match((await response.json()).error, /could not verify this connection/i);
});

test("rejects non-official browser origins", async () => {
  const req = new Request("https://hte-download-gateway.example.workers.dev", {
    method: "POST",
    headers: { Origin: "https://attacker.example", apikey: "public-test-key" },
    body: BODY
  });
  const response = await worker.fetch(req, { HTE_DOWNLOAD_PROXY_SECRET: "test-only-secret", SUPABASE_URL: "https://example.supabase.co" });
  assert.equal(response.status, 403);
});

test("fails closed when the shared HMAC secret is missing", async () => {
  const req = request();
  Object.defineProperty(req, "cf", { value: { connectingIP: "203.0.113.7" } });
  const response = await worker.fetch(req, { SUPABASE_URL: "https://example.supabase.co" });
  assert.equal(response.status, 503);
  assert.match((await response.json()).error, /not configured/i);
});
