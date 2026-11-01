import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const allowedOrigins = new Set([
  "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev",
  "https://suwilanjisinkamba184-cmd.github.io",
]);
const corsHeaders = {
  "Access-Control-Allow-Origin": "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};

function response(body: unknown, status = 200, origin = "") {
  const allowedOrigin = allowedOrigins.has(origin) ? origin : "https://high-tech-entertainment.suwilanjisinkamba184.workers.dev";
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Access-Control-Allow-Origin": allowedOrigin, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin") || "";
  if (req.method === "OPTIONS") {
    if (!allowedOrigins.has(origin)) return new Response("Origin not allowed", { status: 403, headers: corsHeaders });
    return new Response("ok", { headers: { ...corsHeaders, "Access-Control-Allow-Origin": origin } });
  }
  if (req.method !== "POST") return response({ error: "Method not allowed" }, 405, origin);
  if (origin && !allowedOrigins.has(origin)) return response({ error: "Origin not allowed" }, 403, origin);

  const resendKey = Deno.env.get("RESEND_API_KEY");
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

    const { data: booking, error: bookingError } = await admin
      .from("contact_messages")
      .select("id,name,email,subject,message,created_at")
      .eq("id", messageId)
      .maybeSingle();
    if (bookingError) throw bookingError;
    if (!booking || !String(booking.subject || "").startsWith("Booking request")) {
      return response({ error: "Booking request not found." }, 404, origin);
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