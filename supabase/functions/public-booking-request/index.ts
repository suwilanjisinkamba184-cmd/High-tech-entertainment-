import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const allowedOrigins = new Set([
  "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev",
  "https://suwilanjisinkamba184-cmd.github.io",
  "https://high-techmedia.netlify.app",
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
    headers: { ...corsHeaders, "Access-Control-Allow-Origin": allowedOrigin, "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

const allowedServices = new Set([
  "Studio session",
  "Beat production",
  "Recording & engineering",
  "Mixing and mastering",
  "Artist development",
  "Artist promotion",
  "Spotify & digital promotion",
  "Music video / content",
  "Software development",
  "Collaboration",
  "Other",
]);

const clean = (value: unknown, max: number) => String(value ?? "").trim().slice(0, max);
const safe = (value: unknown) => String(value ?? "").replace(/[&<>"']/g, c => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
}[c]!));

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin") || "";
  if (req.method === "OPTIONS") {
    if (!isAllowedOrigin(origin)) return new Response("Origin not allowed", { status: 403, headers: corsHeaders });
    return new Response("ok", { headers: { ...corsHeaders, "Access-Control-Allow-Origin": origin } });
  }
  if (req.method !== "POST") return response({ error: "Method not allowed" }, 405, origin);
  if (!origin || !isAllowedOrigin(origin)) return response({ error: "Origin not allowed" }, 403, origin);

  let input: Record<string, unknown>;
  try {
    const body = await req.json();
    if (!body || typeof body !== "object" || Array.isArray(body)) return response({ error: "Invalid request body." }, 400, origin);
    input = body as Record<string, unknown>;
  } catch {
    return response({ error: "Invalid JSON body." }, 400, origin);
  }

  // Honeypot: silently accept bot submissions without creating a booking or sending email.
  if (clean(input.website, 200)) return response({ ok: true, saved: true, email_sent: true }, 200, origin);

  const name = clean(input.name, 100);
  const email = clean(input.email, 254).toLowerCase();
  const phone = clean(input.phone, 40);
  const service = clean(input.service, 80);
  const date = clean(input.date, 10);
  const time = clean(input.time, 5);
  const details = clean(input.details, 4000);
  const startedAt = Number(input.started_at || 0);

  if (startedAt > 0 && Date.now() - startedAt < 1200) {
    return response({ error: "Please wait a moment before submitting your request." }, 429, origin);
  }
  if (name.length < 1 || email.length < 3 || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
    return response({ error: "Enter a valid name and email address." }, 400, origin);
  }
  if (!allowedServices.has(service)) return response({ error: "Choose one of the listed services." }, 400, origin);
  if (date && !/^\d{4}-\d{2}-\d{2}$/.test(date)) return response({ error: "Choose a valid preferred date." }, 400, origin);
  if (time && !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) return response({ error: "Choose a valid preferred time." }, 400, origin);
  if (details.length < 1) return response({ error: "Add a few details about your project." }, 400, origin);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const secretKeysRaw = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (!supabaseUrl || !secretKeysRaw) return response({ error: "Booking service is not configured." }, 503, origin);

  const clientIp = req.headers.get("cf-connecting-ip") ||
    req.headers.get("x-real-ip") ||
    (req.headers.get("x-forwarded-for") || "").split(",")[0].trim();
  if (!clientIp) return response({ error: "Unable to verify the request source. Please retry later." }, 503, origin);

  try {
    const secretKeys = JSON.parse(secretKeysRaw);
    const serviceKey = secretKeys.default;
    if (!serviceKey) return response({ error: "Booking service is not configured." }, 503, origin);
    const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(clientIp));
    const ipHash = Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, "0")).join("");
    const { data: withinLimit, error: rateError } = await admin.rpc("hte_consume_booking_notification_limit", {
      _ip_hash: ipHash, _max_requests: 5, _window_seconds: 900,
    });
    if (rateError) throw rateError;
    if (withinLimit !== true) return response({ error: "Too many booking requests from this network. Please wait 15 minutes and try again." }, 429, origin);

    const subject = "Booking request — " + service;
    const message = [
      "Service: " + service,
      "Preferred date: " + (date || "Flexible"),
      "Preferred time: " + (time || "Flexible"),
      "Phone / WhatsApp: " + (phone || "Not provided"),
      "",
      "Project details:",
      details,
    ].join("\n");

    const { data: booking, error: insertError } = await admin
      .from("contact_messages")
      .insert({ name, email, subject, message, user_id: null })
      .select("id,created_at")
      .single();
    if (insertError) {
      const detail = String(insertError.message || "");
      if (/rate|limit|too many/i.test(detail)) return response({ error: "Too many requests. Please wait and try again." }, 429, origin);
      throw insertError;
    }

    const { error: logError } = await admin.from("booking_notification_log").insert({
      message_id: booking.id, status: "pending", error: null, updated_at: new Date().toISOString(),
    });
    if (logError) {
      console.error("public booking log insert failed", logError);
      return response({ ok: true, saved: true, email_sent: false, error: "Your request is saved, but the email alert is delayed. The team can still review it in Founder Studio." }, 200, origin);
    }

    const resendKey = Deno.env.get("RESEND_API_KEY");
    if (!resendKey) {
      await admin.from("booking_notification_log").update({
        status: "failed", error: "RESEND_API_KEY is not configured", updated_at: new Date().toISOString(),
      }).eq("message_id", booking.id);
      return response({ ok: true, saved: true, email_sent: false, error: "Your request is saved, but email notification is temporarily unavailable." }, 200, origin);
    }

    const sent = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { "Authorization": "Bearer " + resendKey, "Content-Type": "application/json" },
      body: JSON.stringify({
        from: "High Tech Entertainment <onboarding@resend.dev>",
        to: ["suwilanjisinkamba184@gmail.com"],
        reply_to: email,
        subject: "New website booking: " + subject,
        html: "<h2>New public booking request</h2><p><b>Name:</b> " + safe(name) +
          "</p><p><b>Email:</b> " + safe(email) + "</p><p><b>Received:</b> " + safe(booking.created_at) +
          "</p><h3>Booking details</h3><pre style=\"white-space:pre-wrap;font-family:Arial,sans-serif\">" +
          safe(message) + "</pre><p>Open Founder Studio → Inbox to review this request.</p>",
        text: "New public booking request\nName: " + name + "\nEmail: " + email +
          "\nReceived: " + booking.created_at + "\n\n" + message + "\n\nReview in Founder Studio → Inbox.",
      }),
    });

    const result = await sent.json().catch(() => ({}));
    if (!sent.ok) {
      const err = String(result.message || result.error || "Resend rejected the email").slice(0, 1000);
      await admin.from("booking_notification_log").update({
        status: "failed", error: err, updated_at: new Date().toISOString(),
      }).eq("message_id", booking.id);
      return response({ ok: true, saved: true, email_sent: false, error: "Your request is saved, but the email alert could not be sent. The team can still review it in Founder Studio." }, 200, origin);
    }

    const { error: updateError } = await admin.from("booking_notification_log").update({
      status: "sent", provider_message_id: result.id || null, error: null, updated_at: new Date().toISOString(),
    }).eq("message_id", booking.id);
    if (updateError) console.error("public booking email log update failed", updateError);

    return response({ ok: true, saved: true, email_sent: true }, 200, origin);
  } catch (error) {
    console.error("public-booking-request error", error);
    return response({ error: "Booking could not be submitted. Please try again." }, 500, origin);
  }
});