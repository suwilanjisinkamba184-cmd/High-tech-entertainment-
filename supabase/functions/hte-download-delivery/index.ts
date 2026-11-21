import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@6.1.0";
import { normalizeTrustedClientIp, verifyDownloadProxySignature } from "../_shared/download-proxy-auth.ts";

const BUCKET = "hte-download-temp";
const MAX_BYTES = 100 * 1024 * 1024;
const JOB_TTL_MS = 60 * 60 * 1000;
const OBJECT_TTL_MS = 30 * 60 * 1000;
const SIGNED_URL_TTL_SECONDS = 180;
const TRUSTED_GITHUB_REPOSITORY = "suwilanjisinkamba184-cmd/High-tech-entertainment-";
const TRUSTED_CLEANUP_WORKFLOW = `${TRUSTED_GITHUB_REPOSITORY}/.github/workflows/cleanup-temporary-downloads.yml@refs/heads/main`;
const GITHUB_OIDC_ISSUER = "https://token.actions.githubusercontent.com";
const GITHUB_OIDC_AUDIENCE = "hte-download-cleanup";
const GITHUB_OIDC_KEYS = createRemoteJWKSet(new URL("https://token.actions.githubusercontent.com/.well-known/jwks"));
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" },
});
const nowIso = () => new Date().toISOString();

function safeFileName(value: string) {
  return value.normalize("NFKD").replace(/[^A-Za-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 70) || "HTE-audio";
}
function extForMime(mime: string) {
  const type = mime.toLowerCase().split(";")[0].trim();
  const map: Record<string,string> = {
    "audio/mpeg": "mp3", "audio/mp3": "mp3", "audio/mp4": "m4a",
    "audio/x-m4a": "m4a", "audio/aac": "aac", "audio/wav": "wav",
    "audio/x-wav": "wav", "audio/ogg": "ogg", "audio/webm": "webm",
    "audio/flac": "flac", "audio/x-flac": "flac",
  };
  return map[type] || "";
}
function extForPath(path: string) {
  const extension = path.split("/").pop()?.split(".").pop()?.toLowerCase() || "";
  const map: Record<string,string> = {
    mp3: "mp3", mp4: "m4a", m4a: "m4a", aac: "aac",
    wav: "wav", ogg: "ogg", webm: "webm", flac: "flac",
  };
  return map[extension] || "";
}
function parseHteAudio(value: string, projectUrl: string) {
  try {
    const url = new URL(value);
    if (url.origin !== new URL(projectUrl).origin || url.protocol !== "https:" || url.username || url.password) return null;
    const path = decodeURIComponent(url.pathname);
    const match = path.match(/^\/storage\/v1\/object\/(?:public|sign|authenticated)\/([^/]+)\/(.+)$/);
    if (!match) return null;
    const bucket = decodeURIComponent(match[1]);
    if (!["hte-audio", "ht storage"].includes(bucket)) return null;
    return { bucket, path: match[2] };
  } catch { return null; }
}
async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, "0")).join("");
}
async function isTrustedCleanupRequest(req: Request) {
  const authorization = req.headers.get("authorization") || "";
  const token = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!token) return false;
  try {
    const { payload } = await jwtVerify(token, GITHUB_OIDC_KEYS, {
      issuer: GITHUB_OIDC_ISSUER,
      audience: GITHUB_OIDC_AUDIENCE,
      clockTolerance: 5,
      maxTokenAge: "5 minutes",
    });
    return payload.repository === TRUSTED_GITHUB_REPOSITORY
      && payload.ref === "refs/heads/main"
      && payload.workflow_ref === TRUSTED_CLEANUP_WORKFLOW
      && ["schedule", "workflow_dispatch"].includes(String(payload.event_name));
  } catch {
    return false;
  }
}

async function cleanupExpired(admin: SupabaseClient<any, "public", any>) {
  const cutoff = Date.now() - OBJECT_TTL_MS;
  const { data: objects, error } = await admin.storage.from(BUCKET).list("requests", {
    limit: 1000, sortBy: { column: "created_at", order: "asc" },
  });
  if (!error && objects?.length) {
    const stale = objects
      .filter((item: any) => {
        const created = Date.parse(item.created_at || item.updated_at || "");
        return !Number.isFinite(created) || created < cutoff;
      })
      .map((item: any) => "requests/" + item.name);
    if (stale.length) await admin.storage.from(BUCKET).remove(stale);
  }
  await admin.from("hte_download_jobs").delete().lt("expires_at", nowIso());
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "Method not allowed" }, 405);

  const projectUrl = Deno.env.get("SUPABASE_URL") || "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!projectUrl || !serviceKey) return reply({ error: "Download delivery is not configured." }, 503);
  const admin = createClient(projectUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

  const rawBody = await req.text();
  let body: any;
  try { body = JSON.parse(rawBody); } catch { return reply({ error: "Invalid request body." }, 400); }

  if (body?.action === "cleanup") {
    if (!await isTrustedCleanupRequest(req)) return reply({ error: "Cleanup is restricted to the trusted HTE GitHub workflow." }, 401);
    try {
      await cleanupExpired(admin);
      return reply({ success: true, cleaned: true });
    } catch (error) {
      console.error("HTE scheduled temporary cleanup failed", error);
      return reply({ error: "Temporary download cleanup failed." }, 500);
    }
  }
  if (body?.action !== "prepare") return reply({ error: "Unknown download action." }, 400);

  // Prepare requests must be signed by the dedicated Cloudflare Worker. The
  // function URL is public, so browser-supplied IP headers are never trusted.
  // The signature binds method, timestamp, one-time nonce, edge-observed IP
  // and the exact raw request body. Direct calls cannot manufacture this proof.
  const proxySecret = Deno.env.get("HTE_DOWNLOAD_PROXY_SECRET") || "";
  if (!proxySecret) return reply({ error: "Secure download delivery is not configured." }, 503);
  const proxyTimestamp = req.headers.get("x-hte-proxy-timestamp") || "";
  const proxyNonce = req.headers.get("x-hte-proxy-nonce") || "";
  const proxySignature = req.headers.get("x-hte-proxy-signature") || "";
  const rawClientIp = req.headers.get("x-hte-client-ip") || "";
  const clientIp = normalizeTrustedClientIp(rawClientIp);
  if (!clientIp || !await verifyDownloadProxySignature(
    proxySecret, req.method, proxyTimestamp, proxyNonce, clientIp, rawBody, proxySignature
  )) {
    return reply({ error: "This download request did not come through the trusted HTE download gateway." }, 401);
  }

  const trackId = String(body.trackId || "");
  if (!/^[0-9a-f-]{36}$/i.test(trackId)) return reply({ error: "Select a valid track." }, 400);
  const { data: track, error: trackError } = await admin.from("tracks")
    .select("id,title,audio_url,source_platform,rights_status,download_allowed,is_published,branded_audio_url,download_branding_status")
    .eq("id", trackId).eq("is_published", true).maybeSingle();
  if (trackError || !track) return reply({ error: "This track is not available for download." }, 404);
  if (track.source_platform !== "direct" || track.download_allowed !== true || track.rights_status !== "owned_or_licensed") {
    return reply({ error: "Download is available only for HTE-owned or licensed audio with confirmed permissions." }, 403);
  }

  const branded = track.download_branding_status === "ready" ? parseHteAudio(String(track.branded_audio_url || ""), projectUrl) : null;
  const original = parseHteAudio(String(track.audio_url || ""), projectUrl);
  const source = branded || original;
  if (!source) return reply({ error: "The original audio is not stored in an approved HTE audio bucket." }, 422);
  const extension = extForPath(source.path);
  if (!extension) return reply({ error: "This audio format is not supported for temporary delivery." }, 415);

  const ipHash = await sha256(serviceKey + ":" + clientIp);

  const id = crypto.randomUUID();
  const fileName = safeFileName(String(track.title || "HTE-audio")) + "-HTE." + extension;
  const objectPath = "requests/" + id + "." + extension;
  const expiresAt = new Date(Date.now() + JOB_TTL_MS).toISOString();
  const { data: jobCreated, error: insertError } = await admin.rpc("hte_create_download_job", {
    p_id: id,
    p_request_nonce: proxyNonce,
    p_track_id: track.id,
    p_object_path: objectPath,
    p_file_name: fileName,
    p_ip_hash: ipHash,
    p_expires_at: expiresAt,
    p_max_downloads: 20,
  });
  if (insertError) {
    if (insertError.code === "23505") return reply({ error: "This download request has already been used. Please retry." }, 409);
    console.error("HTE atomic download quota reservation failed", insertError);
    return reply({ error: "Download service is temporarily unavailable. Please retry." }, 503);
  }
  if (jobCreated !== true) {
    return reply({ error: "Too many downloads from this connection. Please try again later." }, 429);
  }

  // Run opportunistic cleanup only after the atomic quota reservation succeeds.
  // Rejected requests therefore cannot trigger privileged storage-list/delete work.
  try { await cleanupExpired(admin); } catch (error) { console.warn("HTE temp cleanup skipped", error); }

  // Copy directly between private-managed buckets on the server. The visitor never uploads bytes.
  const { error: copyError } = await admin.storage.from(source.bucket).copy(source.path, objectPath, { destinationBucket: BUCKET });
  if (copyError) {
    await admin.from("hte_download_jobs").delete().eq("id", id);
    console.error("HTE temporary audio copy failed", copyError);
    return reply({ error: "The authorised audio could not be staged in temporary Supabase storage." }, 502);
  }

  const { data: files, error: listError } = await admin.storage.from(BUCKET).list("requests", { limit: 1000, search: id });
  const file = (files || []).find((item: any) => item.name === id + "." + extension);
  const fileSize = Number(file?.metadata?.size || 0);
  const fileType = String(file?.metadata?.mimetype || "").toLowerCase().split(";")[0];
  if (listError || !file || !fileSize || fileSize > MAX_BYTES || (fileType && !extForMime(fileType))) {
    await admin.storage.from(BUCKET).remove([objectPath]);
    await admin.from("hte_download_jobs").delete().eq("id", id);
    return reply({ error: "The copied audio did not pass file validation or exceeds the 100 MB limit." }, 422);
  }

  const { error: updateError } = await admin.from("hte_download_jobs")
    .update({ status: "uploaded" }).eq("id", id).eq("status", "created");
  if (updateError) {
    await admin.storage.from(BUCKET).remove([objectPath]);
    await admin.from("hte_download_jobs").delete().eq("id", id);
    return reply({ error: "The temporary download could not be finalised." }, 500);
  }
  const { data: signed, error: signError } = await admin.storage.from(BUCKET)
    .createSignedUrl(objectPath, SIGNED_URL_TTL_SECONDS, { download: fileName });
  if (signError || !signed?.signedUrl) {
    await admin.storage.from(BUCKET).remove([objectPath]);
    await admin.from("hte_download_jobs").delete().eq("id", id);
    return reply({ error: "The temporary download link could not be created." }, 502);
  }
  return reply({ success: true, downloadUrl: signed.signedUrl, fileName, expiresInSeconds: SIGNED_URL_TTL_SECONDS });
});
