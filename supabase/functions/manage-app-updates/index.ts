import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" },
});
const FOUNDER_ID = "b268df9e-eff5-43b9-a38f-869b33add38b";
const PACKAGE = "com.hightechentertainment.app";
const HOSTS = new Set(["github.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com"]);

function decode64(value: string): Uint8Array {
  return Uint8Array.from(atob(value), (c) => c.charCodeAt(0));
}
async function sign(payload: Record<string, unknown>): Promise<string> {
  const encoded = Deno.env.get("HTE_UPDATE_SIGNING_PRIVATE_KEY_PKCS8");
  if (!encoded) throw new Error("Missing signing secret");
  const key = await crypto.subtle.importKey("pkcs8", decode64(encoded), { name: "Ed25519" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("Ed25519", key, new TextEncoder().encode(JSON.stringify(payload)));
  return btoa(String.fromCharCode(...new Uint8Array(signature)));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const url = Deno.env.get("SUPABASE_URL");
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || !service || !anon) return reply({ error: "Server configuration missing." }, 500);
  const admin = createClient(url, service, { auth: { persistSession: false } });

  if (req.method === "GET") {
    const channel = new URL(req.url).searchParams.get("channel") ?? "stable";
    if (channel !== "stable" && channel !== "beta") return reply({ error: "Invalid channel." }, 400);
    const { data, error } = await admin.from("app_updates")
      .select("channel,version_name,version_code,minimum_supported_version_code,is_mandatory,release_notes,apk_url,apk_sha256,package_name,manifest_signature,published_at")
      .eq("channel", channel).eq("is_published", true).is("withdrawn_at", null)
      .order("version_code", { ascending: false }).limit(1).maybeSingle();
    if (error) return reply({ error: "Update lookup failed." }, 500);
    return reply({ update: data });
  }
  if (req.method !== "POST") return reply({ error: "Method not allowed." }, 405);

  const bearer = req.headers.get("Authorization") ?? "";
  const token = bearer.startsWith("Bearer ") ? bearer.slice(7) : "";
  if (!token) return reply({ error: "Founder sign-in required." }, 401);
  const authClient = createClient(url, anon, { auth: { persistSession: false } });
  const { data: authData, error: authError } = await authClient.auth.getUser(token);
  if (authError || authData.user?.id !== FOUNDER_ID) return reply({ error: "Only the HTE founder can manage releases." }, 403);

  let input: Record<string, unknown>;
  try { input = await req.json(); } catch { return reply({ error: "Invalid JSON." }, 400); }
  if (input.action === "withdraw") {
    if (typeof input.id !== "string") return reply({ error: "Release ID required." }, 400);
    const { error } = await admin.from("app_updates").update({
      is_published: false, withdrawn_at: new Date().toISOString(), updated_at: new Date().toISOString(),
    }).eq("id", input.id);
    return error ? reply({ error: "Withdraw failed." }, 500) : reply({ success: true });
  }
  if (input.action !== "publish") return reply({ error: "Unsupported action." }, 400);

  const versionName = input.version_name;
  const versionCode = input.version_code;
  const minimum = input.minimum_supported_version_code ?? 1;
  const notes = input.release_notes ?? "";
  const apkUrl = input.apk_url;
  const hash = input.apk_sha256;
  const channel = input.channel ?? "stable";
  const mandatory = input.is_mandatory === true;
  if (typeof versionName !== "string" || typeof versionCode !== "number" ||
      !Number.isSafeInteger(versionCode) || versionCode < 1 ||
      typeof minimum !== "number" || !Number.isSafeInteger(minimum) || minimum < 1 ||
      typeof notes !== "string" || notes.length > 4000 ||
      typeof apkUrl !== "string" || typeof hash !== "string" ||
      !/^[a-f0-9]{64}$/.test(hash) || (channel !== "stable" && channel !== "beta")) {
    return reply({ error: "Invalid release fields." }, 400);
  }
  let parsed: URL;
  try { parsed = new URL(apkUrl); } catch { return reply({ error: "Invalid APK URL." }, 400); }
  if (parsed.protocol !== "https:" || !HOSTS.has(parsed.hostname)) return reply({ error: "APK URL must be an approved HTTPS GitHub release URL." }, 400);

  const payload = {
    channel, version_name: versionName, version_code: versionCode,
    minimum_supported_version_code: minimum, is_mandatory: mandatory,
    release_notes: notes, apk_url: apkUrl, apk_sha256: hash, package_name: PACKAGE,
  };
  let signature: string;
  try { signature = await sign(payload); }
  catch { return reply({ error: "Release signing is not configured." }, 503); }
  const now = new Date().toISOString();
  const { data, error } = await admin.from("app_updates").insert({
    ...payload, manifest_signature: signature, is_published: true,
    published_at: now, created_by: FOUNDER_ID, updated_at: now,
  }).select("id,version_name,version_code,channel,is_mandatory,published_at").single();
  if (error) return reply({ error: "Publish failed. Check that version code is unique." }, 500);
  return reply({ success: true, release: data }, 201);
});
