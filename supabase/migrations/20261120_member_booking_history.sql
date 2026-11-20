-- Allow members to read only their own authenticated booking requests.
-- Public contact messages and other members' messages remain hidden.
drop policy if exists "Members read own booking requests" on public.contact_messages;
create policy "Members read own booking requests"
on public.contact_messages
for select
to authenticated
using (user_id = (select auth.uid()) and subject like 'Booking request%');
