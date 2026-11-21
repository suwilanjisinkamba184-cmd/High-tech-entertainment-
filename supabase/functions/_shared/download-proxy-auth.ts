const encoder = new TextEncoder();

export function normalizeTrustedClientIp(value: string): string | null {
  const raw = value.trim();
  if (!raw || raw.length > 64) return null;
  if (/^[0-9.]+$/.test(raw)) {
    const octets = raw.split(".");
    if (octets.length !== 4 || !octets.every(part => /^(0|[1-9][0-9]{0,2})$/.test(part) && Number(part) <= 255)) return null;
    return octets.map(Number).join(".");
  }
  if (/^[0-9a-fA-F:]+$/.test(raw) && raw.includes(":")) {
    try {
      return new URL("http://[" + raw + "]/").hostname.slice(1, -1).toLowerCase();
    } catch {
      return null;
    }
  }
  return null;
}

export function proxySignaturePayload(method: string, timestamp: string, nonce: string, clientIp: string, rawBody: string) {
  return [method.toUpperCase(), timestamp, nonce, clientIp, rawBody].join("\n");
}

export function isFreshProxyTimestamp(value: string, nowSeconds = Math.floor(Date.now() / 1000)) {
  if (!/^\d{10}$/.test(value)) return false;
  const timestamp = Number(value);
  return Number.isSafeInteger(timestamp) && Math.abs(nowSeconds - timestamp) <= 300;
}

export function isProxyNonce(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

export async function verifyDownloadProxySignature(
  secret: string,
  method: string,
  timestamp: string,
  nonce: string,
  clientIp: string,
  rawBody: string,
  signatureHex: string
): Promise<boolean> {
  if (!secret || !isFreshProxyTimestamp(timestamp) || !isProxyNonce(nonce)) return false;
  if (!/^[0-9a-f]{64}$/i.test(signatureHex)) return false;
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const signature = new Uint8Array(signatureHex.match(/.{2}/g)!.map(byte => Number.parseInt(byte, 16)));
  return crypto.subtle.verify(
    "HMAC",
    key,
    signature,
    encoder.encode(proxySignaturePayload(method, timestamp, nonce, clientIp, rawBody))
  );
}
