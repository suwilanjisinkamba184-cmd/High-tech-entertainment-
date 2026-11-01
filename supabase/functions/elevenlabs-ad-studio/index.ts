import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const allowedOrigins = new Set([
  "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev",
  "https://suwilanjisinkamba184-cmd.github.io",
]);
const cors = {
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};
function reply(body: unknown, status = 200, origin = "") {
  const safeOrigin = allowedOrigins.has(origin) ? origin : "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev";
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Access-Control-Allow-Origin": safeOrigin, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  const origin = req.headers.get("origin") || "";
  if (req.method === "OPTIONS") {
    if (!allowedOrigins.has(origin)) return new Response("Origin not allowed.", { status: 403, headers: cors });
    return new Response("ok", { headers: { ...cors, "Access-Control-Allow-Origin": origin } });
  }
  if (req.method !== "POST") return reply({ error: "Method not allowed." }, 405, origin);
  if (!allowedOrigins.has(origin)) return reply({ error: "Origin not allowed." }, 403, origin);

  const authHeader = req.headers.get("Authorization") || "";
  const token = authHeader.replace(/^Bearer\s+/i, "");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  // Support both legacy anon keys and the current named publishable-key format.
  let publishableKey = "";
  try {
    const keyMap = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") || "{}");
    publishableKey = String(keyMap.default || "");
  } catch { /* fall through to legacy key */ }
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") || publishableKey;
  const elevenKey = Deno.env.get("ELEVENLABS_API_KEY");
  const missingConfig = [
    !supabaseUrl && "SUPABASE_URL",
    !anonKey && "SUPABASE_ANON_KEY or SUPABASE_PUBLISHABLE_KEYS.default",
    !elevenKey && "ELEVENLABS_API_KEY",
  ].filter(Boolean);
  if (missingConfig.length) {
    console.error("AI Ad Studio missing runtime configuration:", missingConfig.join(", "));
    return reply({ error: "AI Ad Studio server configuration is incomplete.", missing: missingConfig }, 503, origin);
  }
  if (!token) return reply({ error: "Sign in as the founder first." }, 401, origin);

  const db = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: auth, error: authError } = await db.auth.getUser(token);
  if (authError || !auth.user) {
    return reply({ error: "A valid signed-in account is required." }, 401, origin);
  }
  const { data: isAdmin, error: roleError } = await db.rpc("hte_is_admin");
  if (roleError || isAdmin !== true) {
    return reply({ error: "An active HTE administrator role is required." }, 403, origin);
  }

  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return reply({ error: "Invalid JSON body." }, 400, origin); }
  const action = String(body.action || "generate");
  if (action === "voices") {
    const response = await fetch("https://api.elevenlabs.io/v1/voices", { headers: { "xi-api-key": elevenKey } });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const detail = typeof data.detail === "string" ? data.detail : data.detail?.message || JSON.stringify(data.detail || data);
      return reply({ error: `ElevenLabs voice list failed (${response.status}): ${String(detail).slice(0, 400)}` }, 502, origin);
    }
    return reply({ voices: (data.voices || []).map((v: any) => ({ voice_id: v.voice_id, name: v.name, category: v.category, labels: v.labels })) }, 200, origin);
  }

  const title = String(body.title || "").trim().slice(0, 120);
  const script = String(body.script || "").trim();
  const voiceId = String(body.voice_id || "").trim();
  const modelId = String(body.model_id || "eleven_multilingual_v2");
  if (!title || !script || !voiceId) return reply({ error: "Title, script and voice are required." }, 400, origin);
  if (script.length > 2500) return reply({ error: "Keep the advertisement script under 2,500 characters." }, 400, origin);
  if (!/^[a-zA-Z0-9_-]{5,80}$/.test(voiceId)) return reply({ error: "Invalid voice selection." }, 400, origin);
  if (!["eleven_multilingual_v2", "eleven_turbo_v2_5"].includes(modelId)) return reply({ error: "Unsupported voice model selected." }, 400, origin);

  const tts = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${encodeURIComponent(voiceId)}?output_format=mp3_44100_128`, {
    method: "POST",
    headers: { "xi-api-key": elevenKey, "Content-Type": "application/json", Accept: "audio/mpeg" },
    body: JSON.stringify({
      text: script,
      model_id: modelId,
      voice_settings: { stability: 0.48, similarity_boost: 0.78, style: 0.25, use_speaker_boost: true },
    }),
  });
  if (!tts.ok) {
    const detail = await tts.text();
    return reply({ error: `ElevenLabs generation failed (${tts.status}): ${detail.slice(0, 500)}` }, 502, origin);
  }
  const audio = new Uint8Array(await tts.arrayBuffer());
  if (!audio.length || audio.length > 25 * 1024 * 1024) return reply({ error: "Generated audio is empty or exceeds the 25 MB upload limit." }, 502, origin);

  const path = `ai-ads/${crypto.randomUUID()}.mp3`;
  const { error: uploadError } = await db.storage.from("hte-audio").upload(path, audio, { contentType: "audio/mpeg", upsert: false });
  if (uploadError) return reply({ error: `Audio generated but storage upload failed: ${uploadError.message}` }, 500, origin);
  const audioUrl = db.storage.from("hte-audio").getPublicUrl(path).data.publicUrl;
  const { data: ad, error: insertError } = await db.from("audio_ads").insert({
    title, audio_url: audioUrl, is_published: false, priority: 0,
  }).select("id,title,audio_url,is_published,created_at").single();
  if (insertError) {
    await db.storage.from("hte-audio").remove([path]);
    return reply({ error: `Audio was generated but the ad record could not be saved: ${insertError.message}` }, 500, origin);
  }
  return reply({ ok: true, ad, message: "Voice generated and saved as an unpublished draft. Review it before publishing." }, 200, origin);
});