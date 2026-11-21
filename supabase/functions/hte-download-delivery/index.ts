import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

const BUCKET = "hte-download-temp";
const MAX_BYTES = 80 * 1024 * 1024;
const JOB_TTL_MS = 60 * 60 * 1000;
const DELIVERY_TTL_MS = 5 * 60 * 1000;
const SIGNED_URL_TTL_SECONDS = 120;
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" },
});
const nowIso = () => new Date().toISOString();

function safeFileName(value: string) {
  const stem = value.normalize("NFKD").replace(/[^A-Za-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 70) || "HTE-audio";
  return stem;
}
function extForMime(mime: string) {
  const type = mime.toLowerCase().split(";")[0].trim();
  const map: Record<string,string> = {
    "audio/mpeg": "mp3", "audio/mp3": "mp3", "audio/mp4": "m4a",
    "audio/x-m4a": "m4a", "audio/aac": "aac", "audio/wav": "wav",
    "audio/x-wav": "wav", "audio/ogg": "ogg", "audio/webm": "webm",
  };
  return map[type] || "";
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
function safeHteAudio(value: string, projectUrl: string) {
  return !!parseHteAudio(value, projectUrl);
}
async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, "0")).join("");
}
function randomToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
async function cleanupExpired(admin: SupabaseClient<any, "public", any>) {
  const cutoff = Date.now() - 30 * 60 * 1000;
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
async function findJob(admin: SupabaseClient<any, "public", any>, id: string) {
  const { data, error } = await admin.from("hte_download_jobs")
    .select("id,track_id,object_path,file_name,ip_hash,download_token_hash,status,expires_at,delivery_expires_at")
    .eq("id", id).maybeSingle();
  if (error) throw error;
  return data;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST" && req.method !== "GET") return reply({ error: "Method not allowed" }, 405);

  const projectUrl = Deno.env.get("SUPABASE_URL") || "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!projectUrl || !serviceKey) return reply({ error: "Download delivery is not configured." }, 503);
  const admin = createClient(projectUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

  if (req.method === "GET") {
    const url = new URL(req.url);
    const jobId = url.searchParams.get("job") || "";
    const token = url.searchParams.get("token") || "";
    if (!/^[0-9a-f-]{36}$/i.test(jobId) || token.length < 32) return reply({ error: "This download link is invalid." }, 400);
    try {
      const job = await findJob(admin, jobId);
      if (!job || job.status !== "uploaded" || (!job.delivery_expires_at || Date.parse(job.delivery_expires_at) <= Date.now())) {
        return reply({ error: "This temporary download has expired. Please prepare it again." }, 410);
      }
      const tokenHash = await sha256(token);
      if (tokenHash !== job.download_token_hash) return reply({ error: "This download link is invalid or has already been used." }, 403);
      const { data: claimed, error: claimError } = await admin.from("hte_download_jobs")
        .update({ status: "downloading" })
        .eq("id", jobId).eq("status", "uploaded").eq("download_token_hash", tokenHash)
        .select("id").maybeSingle();
      if (claimError || !claimed) return reply({ error: "This download link has already been used." }, 409);

      const { data: signed, error: signError } = await admin.storage.from(BUCKET)
        .createSignedUrl(job.object_path, SIGNED_URL_TTL_SECONDS);
      if (signError || !signed?.signedUrl) {
        await admin.from("hte_download_jobs").update({ status: "uploaded" }).eq("id", jobId);
        return reply({ error: "The temporary audio could not be opened. Please retry." }, 502);
      }
      const upstream = await fetch(signed.signedUrl);
      if (!upstream.ok || !upstream.body) {
        await admin.from("hte_download_jobs").update({ status: "uploaded" }).eq("id", jobId);
        return reply({ error: "The temporary audio could not be delivered. Please retry." }, 502);
      }

      const contentType = upstream.headers.get("Content-Type") || "application/octet-stream";
      const contentLength = upstream.headers.get("Content-Length");
      const reader = upstream.body.getReader();
      let cleaned = false;
      const cleanup = async () => {
        if (cleaned) return;
        cleaned = true;
        await admin.storage.from(BUCKET).remove([job.object_path]);
        await admin.from("hte_download_jobs").update({ status: "delivered", download_token_hash: null }).eq("id", jobId);
      };
      const stream = new ReadableStream<Uint8Array>({
        async pull(controller) {
          try {
            const part = await reader.read();
            if (part.done) {
              await cleanup();
              controller.close();
            } else controller.enqueue(part.value);
          } catch (error) {
            await cleanup();
            controller.error(error);
          }
        },
        async cancel() {
          try { await reader.cancel(); } finally { await cleanup(); }
        },
      });
      const headers = new Headers({
        ...cors,
        "Content-Type": contentType,
        "Content-Disposition": 'attachment; filename="' + job.file_name.replace(/["\\\r\n]/g, "") + '"',
        "Cache-Control": "no-store, private, max-age=0",
        "X-Content-Type-Options": "nosniff",
      });
      if (contentLength) headers.set("Content-Length", contentLength);
      return new Response(stream, { status: 200, headers });
    } catch (error) {
      console.error("HTE temporary download stream failed", error);
      return reply({ error: "The temporary download could not be delivered. Please try again." }, 500);
    }
  }

  let body: any;
  try { body = await req.json(); } catch { return reply({ error: "Invalid request body." }, 400); }
  try { await cleanupExpired(admin); } catch (error) { console.warn("HTE temp cleanup skipped", error); }

  if (body?.action === "source") {
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
    const { data: signed, error: signError } = await admin.storage.from(source.bucket).createSignedUrl(source.path, 10 * 60);
    if (signError || !signed?.signedUrl) return reply({ error: "The original HTE audio could not be opened securely." }, 502);
    return reply({ success: true, sourceUrl: signed.signedUrl, alreadyBranded: !!branded });
  }

  if (body?.action === "cleanup") return reply({ success: true, cleaned: true });

  if (body?.action === "prepare") {
    const trackId = String(body.trackId || "");
    const mime = String(body.contentType || "").toLowerCase().split(";")[0].trim();
    const size = Number(body.fileSize || 0);
    const extension = extForMime(mime);
    if (!/^[0-9a-f-]{36}$/i.test(trackId)) return reply({ error: "Select a valid track." }, 400);
    if (!extension || !Number.isSafeInteger(size) || size < 1 || size > MAX_BYTES) {
      return reply({ error: "This audio file type or size is not supported for temporary delivery." }, 413);
    }
    const { data: track, error: trackError } = await admin.from("tracks")
      .select("id,title,audio_url,source_platform,rights_status,download_allowed,is_published")
      .eq("id", trackId).eq("is_published", true).maybeSingle();
    if (trackError || !track) return reply({ error: "This track is not available for download." }, 404);
    if (track.source_platform !== "direct" || track.download_allowed !== true || track.rights_status !== "owned_or_licensed") {
      return reply({ error: "Download is available only for HTE-owned or licensed audio with confirmed permissions." }, 403);
    }
    if (!safeHteAudio(String(track.audio_url || ""), projectUrl)) {
      return reply({ error: "The original audio is not stored in an approved HTE audio bucket." }, 422);
    }

    const forwarded = req.headers.get("cf-connecting-ip") || req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
    const ipHash = await sha256(serviceKey + ":" + forwarded);
    const since = new Date(Date.now() - 60 * 60 * 1000).toISOString();
    const { count, error: rateError } = await admin.from("hte_download_jobs")
      .select("id", { count: "exact", head: true }).eq("ip_hash", ipHash).gte("created_at", since);
    if (rateError) return reply({ error: "Download service is temporarily unavailable." }, 503);
    if ((count || 0) >= 30) return reply({ error: "Too many downloads from this connection. Please try again later." }, 429);

    const id = crypto.randomUUID();
    const fileName = safeFileName(String(track.title || "HTE-audio")) + "-HTE." + extension;
    const objectPath = "requests/" + id + "." + extension;
    const expiresAt = new Date(Date.now() + JOB_TTL_MS).toISOString();
    const { error: insertError } = await admin.from("hte_download_jobs").insert({
      id, track_id: track.id, object_path: objectPath, file_name: fileName, ip_hash: ipHash, status: "created", expires_at: expiresAt,
    });
    if (insertError) return reply({ error: "A temporary download could not be prepared. Please retry." }, 500);
    const { data: upload, error: uploadError } = await admin.storage.from(BUCKET).createSignedUploadUrl(objectPath);
    if (uploadError || !upload?.token) {
      await admin.from("hte_download_jobs").delete().eq("id", id);
      return reply({ error: "Secure temporary storage is unavailable. Please retry." }, 503);
    }
    return reply({ success: true, jobId: id, path: objectPath, token: upload.token, bucket: BUCKET, fileName });
  }

  if (body?.action === "complete") {
    const jobId = String(body.jobId || "");
    if (!/^[0-9a-f-]{36}$/i.test(jobId)) return reply({ error: "Invalid temporary download request." }, 400);
    const job = await findJob(admin, jobId);
    if (!job || job.status !== "created" || Date.parse(job.expires_at) <= Date.now()) {
      return reply({ error: "The temporary upload expired. Prepare the download again." }, 410);
    }
    const parts = job.object_path.split("/");
    const { data: files, error: listError } = await admin.storage.from(BUCKET).list("requests", { limit: 1000, search: jobId });
    const file = (files || []).find((item: any) => item.name === parts[1]);
    if (listError || !file) return reply({ error: "The temporary audio upload was not found. Please retry." }, 404);
    const fileSize = Number(file.metadata?.size || 0);
    const fileType = String(file.metadata?.mimetype || "").toLowerCase().split(";")[0];
    if (!fileSize || fileSize > MAX_BYTES || (fileType && !extForMime(fileType))) {
      await admin.storage.from(BUCKET).remove([job.object_path]);
      await admin.from("hte_download_jobs").delete().eq("id", jobId);
      return reply({ error: "The uploaded temporary file did not pass validation." }, 422);
    }
    const token = randomToken();
    const tokenHash = await sha256(token);
    const deliveryExpiresAt = new Date(Date.now() + DELIVERY_TTL_MS).toISOString();
    const { error: updateError } = await admin.from("hte_download_jobs")
      .update({ status: "uploaded", download_token_hash: tokenHash, delivery_expires_at: deliveryExpiresAt })
      .eq("id", jobId).eq("status", "created");
    if (updateError) return reply({ error: "The temporary download could not be finalised." }, 500);
    const downloadUrl = new URL("/functions/v1/hte-download-delivery", projectUrl);
    downloadUrl.searchParams.set("job", jobId);
    downloadUrl.searchParams.set("token", token);
    return reply({ success: true, downloadUrl: downloadUrl.toString(), fileName: job.file_name });
  }

  return reply({ error: "Unknown download action." }, 400);
});
