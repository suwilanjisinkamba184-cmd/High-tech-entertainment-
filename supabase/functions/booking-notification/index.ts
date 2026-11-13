import { createClient } from "npm:@supabase/supabase-js@2";

const allowedOrigins = new Set([
  "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev",
  "https://suwilanjisinkamba184-cmd.github.io",
  "https://website-redesign-mainline-20.high-tech-entertainment.pages.dev",
]);
function isAllowedOrigin(origin: string): boolean {
  return allowedOrigins.has(origin) ||
    origin === "https://high-tech-entertainment.pages.dev" ||
    /^https:\/\/[a-z0-9-]+\.high-tech-entertainment\.pages\.dev$/.test(origin);
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};

function response(body: unknown, status = 200, origin = "") {
  const allowedOrigin = isAllowedOrigin(origin) ? origin : "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev";
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Access-Control-Allow-Origin": allowedOrigin, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin") || "";
  if (req.method === "OPTIONS") {
    if (!isAllowedOrigin(origin)) return new Response("Origin not allowed", { status: 403, headers: corsHeaders });
    return new Response("ok", { headers: { ...corsHeaders, "Access-Control-Allow-Origin": origin } });
  }
  if (req.method !== "POST") return response({ error: "Method not allowed" }, 405, origin);
  if (origin && !isAllowedOrigin(origin)) return response({ error: "Origin not allowed" }, 403, origin);

  const accessToken = req.headers.get("authorization")?.match(/^Bearer\s+(.+)$/i)?.[1] || "";

  const resendKey = Deno.env.get("RESEND_API_KEY") || Deno.env.get("Resend");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const secretKeysRaw = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (!resendKey || !supabaseUrl || !secretKeysRaw) {
    return response({ error: "Notification service is not configured. Check RESEND_API_KEY and Supabase secret keys." }, 503, origin);
  }

  let messageId: string;
  try {
    const body = await req.json();
    messageId = String(body.message_id || "");
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(messageId)) {
      return response({ error: "A valid booking message_id is required." }, 400, origin);
    }
  } catch {
    return response({ error: "Invalid JSON body." }, 400, origin);
  }

  try {
    const secretKeys = JSON.parse(secretKeysRaw);
    const serviceKey = secretKeys.default;
    if (!serviceKey) return response({ error: "Supabase server secret key is unavailable." }, 503, origin);
    const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

    // Member bookings require a valid user token. Guest bookings are accepted only
    // for NULL user_id rows from an allowed website origin, with the same rate limits.
    let authenticatedUserId: string | null = null;
    if (accessToken) {
      const { data: authData, error: authError } = await admin.auth.getUser(accessToken);
      if (!authError && authData.user) authenticatedUserId = authData.user.id;
    }

    // Rate-limit notifications by a one-way hash of the gateway-provided client IP.
    const clientIp = req.headers.get("cf-connecting-ip") ||
      req.headers.get("x-real-ip") ||
      (req.headers.get("x-forwarded-for") || "").split(",")[0].trim();
    if (!clientIp) return response({ error: "Unable to verify the request source. Please retry later." }, 503, origin);
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(clientIp));
    const ipHash = Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
    const { data: withinLimit, error: rateError } = await admin.rpc("hte_consume_booking_notification_limit", {
      _ip_hash: ipHash, _max_requests: 5, _window_seconds: 900,
    });
    if (rateError) throw rateError;
    if (withinLimit !== true) return response({ error: "Too many booking notifications from this network. Please wait and try again." }, 429, origin);

    const { data: booking, error: bookingError } = await admin
      .from("contact_messages")
      .select("id,user_id,name,email,subject,message,created_at")
      .eq("id", messageId)
      .maybeSingle();
    if (bookingError) throw bookingError;
    if (!booking || !String(booking.subject || "").startsWith("Booking request")) {
      return response({ error: "Booking request not found." }, 404, origin);
    }
    if (booking.user_id) {
      if (!authenticatedUserId || booking.user_id !== authenticatedUserId) {
        return response({ error: "Booking request not found." }, 404, origin);
      }
    } else if (!origin || !isAllowedOrigin(origin)) {
      return response({ error: "Guest booking notifications are only accepted from the official website." }, 403, origin);
    }
    const createdAt = Date.parse(String(booking.created_at || ""));
    if (!Number.isFinite(createdAt) || Date.now() - createdAt > 30 * 60 * 1000 || createdAt > Date.now() + 60 * 1000) {
      return response({ error: "This booking notification request has expired." }, 410, origin);
    }

    const { data: previous, error: previousError } = await admin
      .from("booking_notification_log")
      .select("status")
      .eq("message_id", messageId)
      .maybeSingle();
    if (previousError) throw previousError;
    if (previous?.status === "sent") return response({ ok: true, already_sent: true }, 200, origin);

    const { error: logError } = await admin.from("booking_notification_log").upsert({
      message_id: messageId, status: "pending", error: null, updated_at: new Date().toISOString()
    }, { onConflict: "message_id" });
    if (logError) throw logError;

    const safe = (v: unknown) => String(v ?? "").replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]!));
    const sent = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { "Authorization": `Bearer ${resendKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        from: "High Tech Entertainment <onboarding@resend.dev>",
        to: ["suwilanjisinkamba184@gmail.com"],
        reply_to: booking.email,
        subject: `New website booking: ${booking.subject || "Booking request"}`,
        html: `<h2>New booking request</h2><p><b>Name:</b> ${safe(booking.name)}</p><p><b>Email:</b> ${safe(booking.email)}</p><p><b>Received:</b> ${safe(booking.created_at)}</p><h3>Booking details</h3><pre style="white-space:pre-wrap;font-family:Arial,sans-serif">${safe(booking.message)}</pre><p>Open Founder Studio → Inbox to review this request.</p>`,
        text: `New booking request\nName: ${booking.name}\nEmail: ${booking.email}\nReceived: ${booking.created_at}\n\n${booking.message}\n\nReview in Founder Studio → Inbox.`
      })
    });

    const result = await sent.json().catch(() => ({}));
    if (!sent.ok) {
      const err = String(result.message || result.error || "Resend rejected the email").slice(0, 1000);
      await admin.from("booking_notification_log").update({ status: "failed", error: err, updated_at: new Date().toISOString() }).eq("message_id", messageId);
      return response({ error: err }, 502, origin);
    }

    const { error: updateError } = await admin.from("booking_notification_log").update({
      status: "sent", provider_message_id: result.id || null, error: null, updated_at: new Date().toISOString()
    }).eq("message_id", messageId);
    if (updateError) throw updateError;

    return response({ ok: true, email_sent: true }, 200, origin);
  } catch (error) {
    console.error("booking-notification error", error);
    return response({ error: "Notification processing failed. Check the Supabase function logs." }, 500, origin);
  }
});