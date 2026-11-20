const PROJECT_REF = "wqrxacjbsldbnoekbkbf";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function parseChannelRef(raw: string): { id?: string; handle?: string } {
  const value = raw.trim();
  const channelUrl = value.match(/youtube\.com\/channel\/(UC[A-Za-z0-9_-]{22})/i);
  if (channelUrl) return { id: channelUrl[1] };
  const handleUrl = value.match(/youtube\.com\/@([^/?#]+)/i);
  if (handleUrl) return { handle: handleUrl[1].replace(/^@/, "") };
  if (/^UC[A-Za-z0-9_-]{22}$/.test(value)) return { id: value };
  if (/^@?[A-Za-z0-9._-]{3,30}$/.test(value)) return { handle: value.replace(/^@/, "") };
  throw new Error("Use a YouTube channel URL, UC channel ID, or exact @handle.");
}

async function youtubeGet(path: string, params: Record<string, string>, apiKey: string) {
  const url = new URL(`https://www.googleapis.com/youtube/v3/${path}`);
  for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
  url.searchParams.set("key", apiKey);
  const response = await fetch(url);
  const payload = await response.json();
  if (!response.ok) {
    const message = payload?.error?.message || `YouTube API returned ${response.status}`;
    throw new Error(message);
  }
  return payload;
}

async function supabaseRequest(path: string, serviceKey: string, init: RequestInit = {}) {
  const base = (Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
  const response = await fetch(`${base}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: serviceKey,
      authorization: `Bearer ${serviceKey}`,
      ...(init.headers || {}),
    },
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Supabase REST ${response.status}: ${text.slice(0, 500)}`);
  return text ? JSON.parse(text) : [];
}

async function existingYoutubeIds(ids: string[], serviceKey: string): Promise<Set<string>> {
  const found = new Set<string>();
  for (let i = 0; i < ids.length; i += 100) {
    const batch = ids.slice(i, i + 100);
    const query = new URLSearchParams({ select: "youtube_id", youtube_id: `in.(${batch.join(",")})` });
    const rows = await supabaseRequest(`videos?${query.toString()}`, serviceKey);
    for (const row of rows) if (row.youtube_id) found.add(row.youtube_id);
  }
  return found;
}

async function validateManagementToken(token: string): Promise<boolean> {
  // Validate that the caller's Supabase Management API token can access this exact project.
  // The token is never logged or persisted by this function.
  const response = await fetch(`https://api.supabase.com/v1/projects/${PROJECT_REF}`, {
    headers: { authorization: `Bearer ${token}` },
  });
  return response.ok;
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json({ error: "POST required" }, 405);

  const managementToken = request.headers.get("x-supabase-access-token") || "";
  let authorized = false;
  try { authorized = !!managementToken && await validateManagementToken(managementToken); } catch { authorized = false; }
  if (!authorized) return json({ error: "Unauthorized sync request" }, 401);

  try {
    const body = await request.json();
    const channelInput = String(body.channel || body.channelId || "").trim();
    if (!channelInput) return json({ error: "Missing channel. Set HTE_YOUTUBE_CHANNEL_ID." }, 400);

    const apiKey = Deno.env.get("YOUTUBE_API_KEY") || "";
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    if (!apiKey || !serviceKey || !supabaseUrl) {
      return json({ error: "Supabase or YouTube server configuration is incomplete." }, 500);
    }

    const ref = parseChannelRef(channelInput);
    const channelParams: Record<string, string> = { part: "snippet,contentDetails", maxResults: "1" };
    if (ref.id) channelParams.id = ref.id;
    else channelParams.forHandle = ref.handle!;
    const channelResponse = await youtubeGet("channels", channelParams, apiKey);
    const channel = channelResponse.items?.[0];
    if (!channel) return json({ error: "The supplied YouTube channel could not be found." }, 404);
    const channelId = channel.id;
    const channelTitle = channel.snippet?.title || "High-Tech Entertainment";
    const uploadsPlaylist = channel.contentDetails?.relatedPlaylists?.uploads;
    if (!uploadsPlaylist) return json({ error: "The channel has no uploads playlist." }, 404);

    const maxPages = Math.max(1, Math.min(50, Number(body.maxPages) || 20));
    const videoIds: string[] = [];
    let pageToken = "";
    let pagesRead = 0;
    for (let page = 0; page < maxPages; page++) {
      const params: Record<string, string> = {
        part: "snippet,contentDetails", playlistId: uploadsPlaylist, maxResults: "50",
      };
      if (pageToken) params.pageToken = pageToken;
      const playlist = await youtubeGet("playlistItems", params, apiKey);
      for (const item of playlist.items || []) {
        const id = item.contentDetails?.videoId || item.snippet?.resourceId?.videoId;
        if (id && /^[A-Za-z0-9_-]{11}$/.test(id)) videoIds.push(id);
      }
      pagesRead++;
      pageToken = playlist.nextPageToken || "";
      if (!pageToken) break;
    }

    const uniqueIds = [...new Set(videoIds)];
    const existing = await existingYoutubeIds(uniqueIds, serviceKey);
    const newIds = uniqueIds.filter((id) => !existing.has(id));
    if (!newIds.length) {
      return json({ ok: true, channel: channelTitle, channelId, pagesRead, playlistItemsChecked: videoIds.length, newVideosInserted: 0 });
    }

    const rows: Record<string, unknown>[] = [];
    for (let i = 0; i < newIds.length; i += 50) {
      const batch = newIds.slice(i, i + 50);
      const detailResponse = await youtubeGet("videos", { part: "snippet,status", id: batch.join(",") }, apiKey);
      for (const item of detailResponse.items || []) {
        if (item.status?.privacyStatus !== "public") continue;
        const snippet = item.snippet || {};
        const thumbnails = snippet.thumbnails || {};
        const thumbnail = thumbnails.maxres || thumbnails.standard || thumbnails.high || thumbnails.medium || thumbnails.default;
        rows.push({
          title: String(snippet.title || "Untitled video").slice(0, 240),
          artist: String(snippet.channelTitle || channelTitle).slice(0, 160),
          description: String(snippet.description || "").slice(0, 3000),
          video_url: `https://www.youtube.com/watch?v=${item.id}`,
          thumbnail_url: thumbnail?.url || `https://i.ytimg.com/vi/${item.id}/hqdefault.jpg`,
          youtube_id: item.id,
          youtube_channel_id: channelId,
          source_platform: "youtube",
          download_url: null,
          published_at: snippet.publishedAt || null,
          is_published: true,
        });
      }
    }

    let inserted: unknown[] = [];
    if (rows.length) {
      const result = await supabaseRequest("videos?on_conflict=youtube_id", serviceKey, {
        method: "POST",
        headers: { "content-type": "application/json", prefer: "resolution=ignore-duplicates,return=representation" },
        body: JSON.stringify(rows),
      });
      inserted = Array.isArray(result) ? result : [];
    }

    return json({
      ok: true, channel: channelTitle, channelId, pagesRead,
      playlistItemsChecked: videoIds.length, newPublicVideos: rows.length,
      newVideosInserted: inserted.length,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown sync error";
    console.error("HTE YouTube sync failed:", message);
    return json({ error: message }, 500);
  }
});
