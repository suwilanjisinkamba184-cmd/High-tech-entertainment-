import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { ...cors, "Content-Type": "application/json" },
});

function extractYouTubeId(input: string): string | null {
  try {
    const url = new URL(input);
    const host = url.hostname.toLowerCase().replace(/^www\./, "");
    let id = "";
    if (host === "youtu.be") id = url.pathname.split("/").filter(Boolean)[0] || "";
    else if (["youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com"].includes(host)) {
      id = url.searchParams.get("v") || "";
      if (!id) {
        const parts = url.pathname.split("/").filter(Boolean);
        if (["embed", "shorts", "live"].includes(parts[0])) id = parts[1] || "";
      }
    }
    return /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
  } catch { return null; }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "Method not allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const youtubeKey = Deno.env.get("YOUTUBE_API_KEY");
  const authorization = req.headers.get("Authorization") || "";
  const token = authorization.replace(/^Bearer\s+/i, "");
  if (!url || !serviceKey || !youtubeKey) return reply({ error: "Submission service is not configured" }, 503);
  if (!token || token === authorization) return reply({ error: "Sign in before submitting a video" }, 401);

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const { data: authData, error: authError } = await admin.auth.getUser(token);
  if (authError || !authData.user) return reply({ error: "Your session is invalid. Please sign in again." }, 401);

  let input: { url?: string; rights_confirmed?: boolean };
  try { input = await req.json(); } catch { return reply({ error: "Invalid JSON body" }, 400); }
  if (input.rights_confirmed !== true) return reply({ error: "Confirm that you own or are authorized to submit and share this video." }, 400);
  const id = extractYouTubeId(String(input.url || ""));
  if (!id) return reply({ error: "Enter a valid public YouTube video URL." }, 400);

  const { data: existing, error: lookupError } = await admin.from("videos")
    .select("id").eq("youtube_id", id).maybeSingle();
  if (lookupError) return reply({ error: "Could not check the video catalogue." }, 500);
  if (existing) return reply({ error: "This video is already in the HTE catalogue.", already_exists: true }, 409);

  const metadataResponse = await fetch(
    "https://www.googleapis.com/youtube/v3/videos?part=snippet,status&id=" + id + "&key=" + encodeURIComponent(youtubeKey),
  );
  if (!metadataResponse.ok) return reply({ error: "YouTube could not verify this video right now." }, 502);
  const metadata = await metadataResponse.json();
  const item = metadata.items?.[0];
  if (!item || item.status?.privacyStatus !== "public" || item.snippet?.liveBroadcastContent === "upcoming") {
    return reply({ error: "Only publicly available YouTube videos can be submitted." }, 422);
  }

  const snippet = item.snippet;
  const record = {
    title: String(snippet.title || "Untitled video").slice(0, 250),
    description: String(snippet.description || "").slice(0, 5000),
    artist: String(snippet.channelTitle || "Independent artist").slice(0, 160),
    video_url: "https://www.youtube.com/watch?v=" + id,
    thumbnail_url: snippet.thumbnails?.high?.url || snippet.thumbnails?.medium?.url || null,
    youtube_id: id,
    youtube_channel_id: snippet.channelId || null,
    source_platform: "artist_submission",
    is_published: true,
    published_at: snippet.publishedAt || new Date().toISOString(),
    download_url: null,
  };
  const { data: saved, error: saveError } = await admin.from("videos")
    .insert(record).select("id,title,artist,video_url,thumbnail_url,created_at").single();
  if (saveError) {
    if (saveError.code === "23505") return reply({ error: "This video is already in the HTE catalogue.", already_exists: true }, 409);
    return reply({ error: "The video could not be added to the catalogue." }, 500);
  }
  return reply({ success: true, video: saved, message: "Your video is now listed on High-Tech Entertainment." }, 201);
});