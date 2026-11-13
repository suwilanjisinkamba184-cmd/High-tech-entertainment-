import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const AGENT_ID = "agent_2601m6tt0dszfqaa7ec4jefe96ww";
const ALLOWED_ORIGINS = new Set([
  "https://suwilanjisinkamba184-cmd.github.io",
  "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev",
]);
const CORS_HEADERS = {
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};

function respond(body: unknown, status: number, origin: string) {
  const headers: Record<string, string> = {
    ...CORS_HEADERS,
    "Content-Type": "application/json",
    "Cache-Control": "no-store",
  };
  if (origin && ALLOWED_ORIGINS.has(origin)) headers["Access-Control-Allow-Origin"] = origin;
  return new Response(JSON.stringify(body), { status, headers });
}

Deno.serve(async (req) => {
  const origin = req.headers.get("origin") ?? "";
  if (origin && !ALLOWED_ORIGINS.has(origin)) {
    return respond({ error: "Origin not allowed." }, 403, "");
  }
  if (req.method === "OPTIONS") {
    if (!origin || !ALLOWED_ORIGINS.has(origin)) return new Response("Origin not allowed.", { status: 403, headers: CORS_HEADERS });
    return new Response("ok", { headers: { ...CORS_HEADERS, "Access-Control-Allow-Origin": origin } });
  }
  if (req.method !== "POST") return respond({ error: "Method not allowed." }, 405, origin);

  const authorization = req.headers.get("Authorization") ?? "";
  const accessToken = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!accessToken) return respond({ error: "A signed-in session is required." }, 401, origin);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  let publishableKey = "";
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
    publishableKey = String(keys.default ?? "");
  } catch {
    // Legacy anon key is supported below.
  }
  publishableKey ||= Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const elevenLabsKey = Deno.env.get("ELEVENLABS_API_KEY") ?? "";
  if (!supabaseUrl || !publishableKey || !elevenLabsKey) {
    console.error("Voice token endpoint is missing required server configuration.");
    return respond({ error: "Voice service is not configured yet." }, 503, origin);
  }

  const supabase = createClient(supabaseUrl, publishableKey, {
    global: { headers: { Authorization: `Bearer ${accessToken}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await supabase.auth.getUser(accessToken);
  if (error || !data.user) return respond({ error: "Your sign-in session is invalid. Sign in again." }, 401, origin);

  const tokenResponse = await fetch(
    `https://api.elevenlabs.io/v1/convai/conversation/token?agent_id=${encodeURIComponent(AGENT_ID)}`,
    { headers: { "xi-api-key": elevenLabsKey } },
  );
  if (!tokenResponse.ok) {
    const detail = await tokenResponse.text();
    console.error("ElevenLabs conversation token request failed:", tokenResponse.status, detail.slice(0, 300));
    return respond({ error: tokenResponse.status === 401 || tokenResponse.status === 403
      ? "Voice service credentials need to be corrected by the project owner."
      : "Could not start a voice session. Please try again." }, 502, origin);
  }

  const tokenBody = await tokenResponse.json().catch(() => ({}));
  const conversationToken = typeof tokenBody.token === "string" ? tokenBody.token : "";
  if (!conversationToken) return respond({ error: "Voice provider returned an invalid session token." }, 502, origin);

  return respond({ token: conversationToken, user_id: data.user.id }, 200, origin);
});