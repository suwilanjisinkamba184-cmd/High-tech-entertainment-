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

test("signs the Cloudflare-observed IP and forwards the exact request body", async () => {
  const req = request({ Authorization: "Bearer member-session-token" });
  Object.defineProperty(req, "cf", { value: { connectingIP: "203.0.113.7" } });
  const originalFetch = globalThis.fetch;
  let forwarded;
  globalThis.fetch = async (url, init) => {
    forwarded = { url: String(url), init };
    return new Response(JSON.stringify({ success: true, downloadUrl: "https://example.invalid/signed" }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  };
  try {
    const secret = "test-only-secret";
    const response = await worker.fetch(req, {
      HTE_DOWNLOAD_PROXY_SECRET: secret,
      SUPABASE_URL: "https://example.supabase.co"
    });
    assert.equal(response.status, 200);
    assert.equal(forwarded.url, "https://example.supabase.co/functions/v1/hte-download-delivery");
    assert.equal(forwarded.init.method, "POST");
    assert.equal(forwarded.init.body, BODY);
    const headers = new Headers(forwarded.init.headers);
    assert.equal(headers.get("x-hte-client-ip"), "203.0.113.7");
    assert.equal(headers.get("Authorization"), "Bearer member-session-token");
    const timestamp = headers.get("x-hte-proxy-timestamp");
    const nonce = headers.get("x-hte-proxy-nonce");
    const signature = headers.get("x-hte-proxy-signature");
    assert.match(timestamp || "", /^\d{10}$/);
    assert.match(nonce || "", /^[0-9a-f-]{36}$/i);
    assert.match(signature || "", /^[0-9a-f]{64}$/);
    const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), {
      name: "HMAC", hash: "SHA-256"
    }, false, ["sign"]);
    const expected = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(
      ["POST", timestamp, nonce, "203.0.113.7", BODY].join("\n")
    ));
    const expectedHex = [...new Uint8Array(expected)].map(value => value.toString(16).padStart(2, "0")).join("");
    assert.equal(signature, expectedHex);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
