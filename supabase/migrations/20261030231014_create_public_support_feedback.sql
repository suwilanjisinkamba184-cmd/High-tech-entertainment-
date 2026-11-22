-- Reconstructed from the live support_feedback table and RLS policy.
CREATE TABLE IF NOT EXISTS public.support_feedback (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  visitor_name text,
  email text,
  category text NOT NULL CHECK (category IN ('website','music','booking','account','other')),
  rating smallint CHECK (rating BETWEEN 1 AND 5),
  message text NOT NULL CHECK (char_length(message) BETWEEN 5 AND 3000),
  source text NOT NULL DEFAULT 'support_form' CHECK (source IN ('support_form','chat_feedback','survey')),
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.support_feedback ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Anyone can submit support feedback" ON public.support_feedback;
CREATE POLICY "Anyone can submit support feedback"
  ON public.support_feedback FOR INSERT TO anon, authenticated
  WITH CHECK (
    char_length(message) BETWEEN 5 AND 3000
    AND category IN ('website','music','booking','account','other')
    AND source IN ('support_form','chat_feedback','survey')
    AND (rating IS NULL OR rating BETWEEN 1 AND 5)
  );
REVOKE ALL ON TABLE public.support_feedback FROM PUBLIC, anon, authenticated;
GRANT INSERT ON TABLE public.support_feedback TO anon, authenticated;
GRANT ALL ON TABLE public.support_feedback TO service_role;
