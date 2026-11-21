const OFFICIAL_ORIGIN = "https://suwilanjisinkamba184-cmd.github.io";
const FUNCTION_PATH = "/functions/v1/hte-download-delivery";
const encoder = new TextEncoder();

function json(body, status, origin = OFFICIAL_ORIGIN) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
      "Vary": "Origin",
      "Cache-Control": "no-store",
      "Content-Type": "application/json"
    }
  });
}

function trustedClientIp(request) {
  const value = String(request.cf?.connectingIP || "").trim();
  if (!value || value.length > 64) return null;
  if (/^[0-9.]+$/.test(value)) {
    const octets = value.split(".");
    if (octets.length !== 4 || !octets.every(part => /^(0|[1-9][0-9]{0,2})$/.test(part) && Number(part) <= 255)) return null;
    return octets.map(Number).join(".");
  }
  if (/^[0-9a-fA-F:]+$/.test(value) && value.includes(":")) {
    try { return new URL("http://[" + value + "]/").hostname.slice(1, -1).toLowerCase(); }
    catch { return null; }
  }
  return null;
}

function signaturePayload(method, timestamp, nonce, clientIp, rawBody) {
  return [method.toUpperCase(), timestamp, nonce, clientIp, rawBody].join("\n");
}

async function sign(secret, payload) {
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret), {name:"HMAC",hash:"SHA-256"}, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(payload));
  return [...new Uint8Array(signature)].map(value => value.toString(16).padStart(2,"0")).join("");
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    if (request.method === "OPTIONS") {
      if (origin !== OFFICIAL_ORIGIN) return json({error:"Origin not allowed."}, 403);
      return new Response(null, {status:204, headers:{
        "Access-Control-Allow-Origin":OFFICIAL_ORIGIN,
        "Access-Control-Allow-Methods":"POST, OPTIONS",
        "Access-Control-Allow-Headers":"authorization, apikey, content-type, x-client-info",
        "Access-Control-Max-Age":"600",
        "Vary":"Origin"
      }});
    }
    if (request.method !== "POST") return json({error:"Method not allowed."}, 405);
    if (origin !== OFFICIAL_ORIGIN) return json({error:"Origin not allowed."}, 403);
    if (!env.HTE_DOWNLOAD_PROXY_SECRET) return json({error:"Secure download gateway is not configured."}, 503);
    if (!env.SUPABASE_URL) return json({error:"Secure download gateway is not configured."}, 503);

    const clientIp = trustedClientIp(request);
    if (!clientIp) return json({error:"The download service could not verify this connection. Please retry."}, 503);

    const rawBody = await request.text();
    if (!rawBody || rawBody.length > 8192) return json({error:"Invalid download request."}, 400);
    let body;
    try { body = JSON.parse(rawBody); } catch { return json({error:"Invalid download request."}, 400); }
    if (body?.action !== "prepare") return json({error:"Unsupported download action."}, 400);

    const apiKey = request.headers.get("apikey") || "";
    if (!apiKey) return json({error:"The HTE public API key is missing. Reload the page and retry."}, 401);

    const timestamp = String(Math.floor(Date.now() / 1000));
    const nonce = crypto.randomUUID();
    const signature = await sign(env.HTE_DOWNLOAD_PROXY_SECRET, signaturePayload("POST", timestamp, nonce, clientIp, rawBody));
    const headers = new Headers({
      "Content-Type":"application/json",
      "apikey":apiKey,
      "x-hte-client-ip":clientIp,
      "x-hte-proxy-timestamp":timestamp,
      "x-hte-proxy-nonce":nonce,
      "x-hte-proxy-signature":signature
    });
    const authorization = request.headers.get("Authorization");
    const clientInfo = request.headers.get("x-client-info");
    if (authorization) headers.set("Authorization", authorization);
    if (clientInfo) headers.set("x-client-info", clientInfo);

    let upstream;
    try {
      upstream = await fetch(env.SUPABASE_URL.replace(/\/+$/, "") + FUNCTION_PATH, {
        method:"POST",
        headers,
        body:rawBody,
        redirect:"manual"
      });
    } catch {
      return json({error:"The HTE download service is temporarily unreachable. Please retry."}, 502);
    }
    const responseHeaders = new Headers({
      "Access-Control-Allow-Origin":OFFICIAL_ORIGIN,
      "Access-Control-Allow-Methods":"POST, OPTIONS",
      "Access-Control-Allow-Headers":"authorization, apikey, content-type, x-client-info",
      "Vary":"Origin",
      "Cache-Control":"no-store",
      "Content-Type":upstream.headers.get("Content-Type") || "application/json"
    });
    return new Response(upstream.body, {status:upstream.status, headers:responseHeaders});
  }
};
