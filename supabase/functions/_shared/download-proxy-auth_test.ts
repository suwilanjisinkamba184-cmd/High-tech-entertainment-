import { assertEquals } from "jsr:@std/assert@1";
import {
  isFreshProxyTimestamp,
  isProxyNonce,
  normalizeTrustedClientIp,
  proxySignaturePayload,
  verifyDownloadProxySignature,
} from "./download-proxy-auth.ts";

Deno.test("normalizes valid IPv4 and rejects spoof-shaped invalid values", () => {
  assertEquals(normalizeTrustedClientIp("203.0.113.7"), "203.0.113.7");
  assertEquals(normalizeTrustedClientIp("203.000.113.7"), null);
  assertEquals(normalizeTrustedClientIp("999.1.1.1"), null);
  assertEquals(normalizeTrustedClientIp("not-an-ip"), null);
});

Deno.test("normalizes IPv6 and rejects malformed IPv6", () => {
  assertEquals(normalizeTrustedClientIp("2001:db8::1"), "2001:db8::1");
  assertEquals(normalizeTrustedClientIp("2001:::1"), null);
});

Deno.test("accepts only fresh numeric timestamps and UUID nonces", () => {
  assertEquals(isFreshProxyTimestamp("1795291000", 1795291000), true);
  assertEquals(isFreshProxyTimestamp("1795290000", 1795291000), false);
  assertEquals(isFreshProxyTimestamp("not-a-time", 1795291000), false);
  assertEquals(isProxyNonce("3f8d1a8b-6a52-4b87-8d6a-2a9bdf123456"), true);
  assertEquals(isProxyNonce("caller-controlled"), false);
});

Deno.test("verifies the signed method, timestamp, nonce, IP and exact body", async () => {
  const secret = "test-only-download-proxy-secret";
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = "3f8d1a8b-6a52-4b87-8d6a-2a9bdf123456";
  const ip = "203.0.113.7";
  const body = '{"action":"prepare","trackId":"00000000-0000-4000-8000-000000000001"}';
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), {name:"HMAC",hash:"SHA-256"}, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(proxySignaturePayload("POST", timestamp, nonce, ip, body)));
  const hex = [...new Uint8Array(signature)].map(value => value.toString(16).padStart(2,"0")).join("");
  assertEquals(await verifyDownloadProxySignature(secret, "POST", timestamp, nonce, ip, body, hex), true);
  assertEquals(await verifyDownloadProxySignature(secret, "POST", timestamp, nonce, ip, body + " ", hex), false);
  assertEquals(await verifyDownloadProxySignature(secret, "POST", timestamp, "caller-controlled", ip, body, hex), false);
});
