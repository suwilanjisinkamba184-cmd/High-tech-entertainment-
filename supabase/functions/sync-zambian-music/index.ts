import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info, x-ht-sync-key",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" },
});
const blocked = /\b(live stream|full concert|podcast|interview|reaction|behind the scenes|vlog|news bulletin)\b/i;

function durationSeconds(value: string) {
  const match = value.match(/^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/);
  if (!match) return 0;
  return Number(match[1] || 0) * 3600 + Number(match[2] || 0) * 60 + Number(match[3] || 0);
}
async function youtube(apiKey: string, endpoint: string, params: Record<string,string>) {
  const url = new URL("https://www.googleapis.com/youtube/v3/" + endpoint);
  for (const [key,value] of Object.entries({ ...params, key: apiKey })) url.searchParams.set(key,value);
  const response = await fetch(url);
  const data = await response.json();
  if (!response.ok) throw new Error(String(data?.error?.message || "YouTube API request failed"));
  return data;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "Method not allowed" }, 405);

  const youtubeKey = Deno.env.get("YOUTUBE_API_KEY") || "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const projectUrl = Deno.env.get("SUPABASE_URL") || "";
  if (!youtubeKey || !serviceKey || !projectUrl) return reply({ error: "Zambian music sync is not configured in Supabase." }, 503);
  if (req.headers.get("x-ht-sync-key") !== youtubeKey) return reply({ error: "Sync authorisation failed." }, 401);

  let body: any;
  try { body = await req.json(); } catch { return reply({ error: "Invalid sync request." }, 400); }
  if (body?.action !== "sync") return reply({ error: "Unknown sync action." }, 400);

  const channels = Array.isArray(body.channels) ? body.channels.map((x: unknown) => String(x).trim()).filter(Boolean).slice(0, 30) : [];
  if (!channels.length) return reply({ error: "No approved artist channels were supplied." }, 400);
  const lookbackDays = Math.max(1, Math.min(45, Number(body.lookback_days) || 21));
  const maxPosts = Math.max(1, Math.min(20, Number(body.max_posts_per_run) || 8));
  const minDuration = Math.max(30, Number(body.min_duration_seconds) || 60);
  const maxDuration = Math.max(minDuration, Math.min(1800, Number(body.max_duration_seconds) || 900));
  const cutoff = Date.now() - lookbackDays * 24 * 60 * 60 * 1000;
  const admin = createClient(projectUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

  try {
    const channelResults = await Promise.all(channels.map(async (handle: string) => {
      const resolved = await youtube(youtubeKey, "channels", { part: "snippet,contentDetails", forHandle: handle.replace(/^@/, "") });
      const channel = resolved.items?.[0];
      if (!channel?.id || !channel.contentDetails?.relatedPlaylists?.uploads) return null;
      return { id: channel.id, name: channel.snippet?.title || handle, uploads: channel.contentDetails.relatedPlaylists.uploads };
    }));
    const approved = channelResults.filter(Boolean) as {id:string;name:string;uploads:string}[];
    const playlistResults = await Promise.all(approved.map(async channel => {
      const result = await youtube(youtubeKey, "playlistItems", {
        part: "snippet,contentDetails", playlistId: channel.uploads, maxResults: "10",
      });
      return (result.items || []).map((item: any) => ({
        id: item.contentDetails?.videoId,
        channelId: channel.id,
        channelName: channel.name,
        publishedAt: item.snippet?.publishedAt || item.contentDetails?.videoPublishedAt || "",
      })).filter((item: any) => item.id && Date.parse(item.publishedAt) >= cutoff);
    }));
    const candidates = playlistResults.flat();
    const uniqueIds = [...new Set(candidates.map((item: any) => item.id))];
    if (!uniqueIds.length) return reply({ success: true, imported: 0, scannedChannels: approved.length, message: "No recent uploads found." });

    const details: any[] = [];
    for (let i = 0; i < uniqueIds.length; i += 50) {
      const batch = uniqueIds.slice(i, i + 50);
      const result = await youtube(youtubeKey, "videos", { part: "snippet,contentDetails,status", id: batch.join(",") });
      details.push(...(result.items || []));
    }
    const candidateById = new Map(candidates.map((item: any) => [item.id, item]));
    const eligible = details.filter(video => {
      const candidate = candidateById.get(video.id);
      if (!candidate || video.status?.privacyStatus !== "public" || video.status?.embeddable === false) return false;
      const published = Date.parse(video.snippet?.publishedAt || candidate.publishedAt);
      if (!Number.isFinite(published) || published < cutoff) return false;
      const duration = durationSeconds(video.contentDetails?.duration || "");
      if (duration < minDuration || duration > maxDuration) return false;
      const title = String(video.snippet?.title || "");
      return !!title && !blocked.test(title);
    }).slice(0, maxPosts);

    if (!eligible.length) return reply({ success: true, imported: 0, scannedChannels: approved.length, message: "No eligible recent music videos found." });
    const ids = eligible.map(video => video.id);
    const { data: existing, error: existingError } = await admin.from("videos").select("youtube_id").in("youtube_id", ids);
    if (existingError) throw existingError;
    const existingIds = new Set((existing || []).map((row: any) => row.youtube_id));
    const rows = eligible.filter(video => !existingIds.has(video.id)).map(video => {
      const candidate = candidateById.get(video.id);
      const thumbs = video.snippet?.thumbnails || {};
      const thumbnail = thumbs.maxres?.url || thumbs.high?.url || thumbs.medium?.url || thumbs.default?.url || "";
      return {
        title: String(video.snippet?.title || "").slice(0, 240),
        artist: candidate.channelName,
        description: String(video.snippet?.description || "").slice(0, 5000),
        video_url: "https://www.youtube.com/watch?v=" + video.id,
        thumbnail_url: thumbnail,
        is_published: true,
        youtube_id: video.id,
        source_platform: "youtube",
        published_at: video.snippet?.publishedAt || candidate.publishedAt,
        download_url: null,
      };
    });
    if (rows.length) {
      const { error: insertError } = await admin.from("videos").insert(rows);
      if (insertError) throw insertError;
    }
    return reply({
      success: true,
      imported: rows.length,
      skippedDuplicates: eligible.length - rows.length,
      scannedChannels: approved.length,
      titles: rows.map(row => row.title),
    });
  } catch (error) {
    console.error("Zambian music sync failed", error);
    return reply({ error: error instanceof Error ? error.message : "Zambian music sync failed." }, 500);
  }
});
