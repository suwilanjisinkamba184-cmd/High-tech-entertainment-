import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import type { FormEvent } from 'react';
import { useEffect, useRef, useState } from 'react';

type FormStatus = { kind: 'success' | 'error'; message: string };

declare global {
  interface Window {
    HTE_SUPABASE_CONFIG?: { url: string; publishableKey: string };
  }
}

let client: SupabaseClient | null = null;
function supabase() {
  if (client) return client;
  const config = window.HTE_SUPABASE_CONFIG;
  if (!config?.url || !config?.publishableKey) throw new Error('The feedback service is temporarily unavailable.');
  client = createClient(config.url, config.publishableKey, {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
  return client;
}

export default function PublicFeedbackForm() {
  const formRef = useRef<HTMLFormElement>(null);
  const [status, setStatus] = useState<FormStatus | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (formRef.current) formRef.current.dataset.startedAt = String(Date.now());
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const form = event.currentTarget;
    const values = new FormData(form);
    if (String(values.get('website') || '').trim()) {
      setStatus({ kind: 'success', message: 'Thank you. Your feedback has been received.' });
      form.reset();
      return;
    }
    const startedAt = Number(form.dataset.startedAt || 0);
    if (startedAt && Date.now() - startedAt < 1200) {
      setStatus({ kind: 'error', message: 'Please take a moment to complete the survey before submitting.' });
      return;
    }

    setSubmitting(true);
    setStatus(null);
    try {
      const name = String(values.get('name') || '').trim();
      const email = String(values.get('email') || '').trim().toLowerCase();
      const rating = String(values.get('rating') || '');
      const feedback = String(values.get('message') || '').trim();
      if (!name || !email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || !['1','2','3','4','5'].includes(rating) || !feedback) {
        throw new Error('Please complete your name, valid email, rating and feedback.');
      }

      const { error } = await supabase().from('contact_messages').insert({
        name,
        email,
        subject: 'Website feedback',
        message: `Website experience rating: ${rating}/5\n\n${feedback}`,
        user_id: null,
      });
      if (error) {
        const text = String(error.message || '').toLowerCase();
        if (text.includes('too many') || text.includes('rate limit')) {
          throw new Error('You have sent feedback recently. Please wait a few minutes before trying again.');
        }
        throw new Error('We could not save your feedback. Please try again in a few minutes.');
      }

      setStatus({ kind: 'success', message: 'Thank you. Your feedback has been sent to the High-Tech Entertainment team.' });
      form.reset();
      form.dataset.startedAt = String(Date.now());
    } catch (error) {
      setStatus({
        kind: 'error',
        message: error instanceof Error ? error.message : 'We could not save your feedback. Please try again.',
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form ref={formRef} className="public-booking-form public-feedback-form" onSubmit={handleSubmit}>
      <div className="booking-honeypot" aria-hidden="true">
        <label>Leave this field empty<input name="website" type="text" tabIndex={-1} autoComplete="off" /></label>
      </div>
      <div className="public-booking-grid">
        <label>
          Your name
          <input name="name" type="text" required maxLength={100} autoComplete="name" placeholder="Your name" />
        </label>
        <label>
          Email address
          <input name="email" type="email" required maxLength={254} autoComplete="email" placeholder="you@example.com" />
        </label>
        <label className="booking-field-full">
          How was your experience?
          <select name="rating" required defaultValue="">
            <option value="" disabled>Choose a rating</option>
            <option value="5">5 — Excellent</option>
            <option value="4">4 — Good</option>
            <option value="3">3 — Average</option>
            <option value="2">2 — Poor</option>
            <option value="1">1 — Very poor</option>
          </select>
        </label>
        <label className="booking-field-full">
          Your feedback
          <textarea name="message" required maxLength={4000} rows={5} placeholder="What worked well? What should we improve?" />
        </label>
      </div>
      <div className="public-booking-actions">
        <button className="button button-gold" type="submit" disabled={submitting}>{submitting ? 'Sending feedback…' : 'Submit feedback'}</button>
        <p className="booking-disclaimer">Please do not include passwords, payment details or other sensitive information.</p>
      </div>
      {status && <p className={`booking-status ${status.kind}`} role={status.kind === 'error' ? 'alert' : 'status'} aria-live={status.kind === 'error' ? 'assertive' : 'polite'}>{status.message}</p>}
    </form>
  );
}
