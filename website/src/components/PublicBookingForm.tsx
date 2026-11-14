import { useEffect, useRef, useState, type SyntheticEvent } from 'react';
import { createClient, type SupabaseClient } from '@supabase/supabase-js';

let client:SupabaseClient|null = null;

function supabase() {
  if (client) return client;
  const cfg = (window as Window & { HTE_SUPABASE_CONFIG?:{url:string;publishableKey:string} }).HTE_SUPABASE_CONFIG;
  if (!cfg?.url || !cfg?.publishableKey) throw new Error('Booking service is not configured.');
  client = createClient(cfg.url,cfg.publishableKey,{auth:{persistSession:false,autoRefreshToken:false,detectSessionInUrl:false}});
  return client;
}

type Status = {kind:'success'|'error'|'info';message:string};

export default function PublicBookingForm() {
  const formRef = useRef<HTMLFormElement|null>(null);
  const [submitting,setSubmitting] = useState(false);
  const [status,setStatus] = useState<Status|null>(null);

  useEffect(() => {
    if (formRef.current) formRef.current.dataset.startedAt = String(Date.now());
  },[]);

  const submit = async (event:SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return;
    const form = event.currentTarget;
    const values = new FormData(form);
    setSubmitting(true);
    setStatus({kind:'info',message:'Sending your booking request…'});
    try {
      const {data,error} = await supabase().functions.invoke('public-booking-request',{
        body:{
          name:String(values.get('name')||'').trim(),
          email:String(values.get('email')||'').trim(),
          phone:String(values.get('phone')||'').trim(),
          service:String(values.get('service')||'').trim(),
          date:String(values.get('date')||'').trim(),
          time:String(values.get('time')||'').trim(),
          details:String(values.get('details')||'').trim(),
          website:String(values.get('website')||''),
          started_at:Number(form.dataset.startedAt||0)
        }
      });
      if (error) throw error;
      if (data?.saved !== true) throw new Error(data?.error || 'Your request could not be saved.');
      form.reset();
      if (formRef.current) formRef.current.dataset.startedAt = String(Date.now());
      if (data.email_sent === true) {
        setStatus({kind:'success',message:'Your request has been received. We will contact you to confirm the details and availability.'});
      } else {
        setStatus({kind:'success',message:data.error || 'Your request has been saved. Our team will review it in Founder Studio and follow up.'});
      }
    } catch (error) {
      console.error('HTE public booking submission failed',error);
      let message = 'We could not submit your request. Please try again or contact High-Tech Entertainment directly.';
      const context = (error as {context?:Response})?.context;
      if (context && typeof context.clone === 'function') {
        const body = await context.clone().json().catch(() => null);
        if (typeof body?.error === 'string' && body.error.trim()) message = body.error;
      } else if (error instanceof Error && error.message && !error.message.includes('Edge Function returned')) {
        message = error.message;
      }
      setStatus({kind:'error',message});
    } finally {
      setSubmitting(false);
    }
  };

  return <form className="public-booking-form" onSubmit={submit} ref={formRef}>
    <div className="booking-honeypot" aria-hidden="true"><label>Leave this field empty<input name="website" type="text" tabIndex={-1} autoComplete="off" /></label></div>
    <div className="public-booking-grid">
      <label>Full name<input name="name" type="text" required maxLength={100} autoComplete="name" placeholder="Your name" /></label>
      <label>Email address<input name="email" type="email" required maxLength={254} autoComplete="email" placeholder="you@example.com" /></label>
      <label>Phone / WhatsApp <span className="optional-label">Optional</span><input name="phone" type="tel" maxLength={40} autoComplete="tel" placeholder="+260…" /></label>
      <label>Service<select name="service" required defaultValue=""><option value="" disabled>Choose a service</option><option>Studio session</option><option>Beat production</option><option>Recording &amp; engineering</option><option>Mixing and mastering</option><option>Artist development</option><option>Artist promotion</option><option>Spotify &amp; digital promotion</option><option>Music video / content</option><option>Software development</option><option>Collaboration</option><option>Other</option></select></label>
      <label>Preferred date <span className="optional-label">Optional</span><input name="date" type="date" /></label>
      <label>Preferred time <span className="optional-label">Optional</span><input name="time" type="time" /></label>
      <label className="booking-field-full">Project details<textarea name="details" required maxLength={4000} rows={5} placeholder="Tell us what you need, your project goals and any useful details…" /></label>
    </div>
    <div className="public-booking-actions"><button className="button button-gold" type="submit" disabled={submitting}>{submitting ? 'Sending request…' : 'Send booking request'} <span>↗</span></button><p className="booking-disclaimer">A request is not a confirmed appointment. Our team will contact you to discuss availability and next steps.</p></div>
    <p className={'booking-status ' + (status?.kind || '')} role={status?.kind === 'error' ? 'alert' : 'status'} aria-live={status?.kind === 'error' ? 'assertive' : 'polite'}>{status?.message || ''}</p>
  </form>;
}
