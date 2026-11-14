-- Expose only the authenticated caller's role to the native Founder Studio.
-- Do not return user IDs, emails, or the role directory.
create or replace function public.hte_my_role()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when auth.uid() is null then null
    else coalesce(
      (
        select user_roles.role::text
        from private.user_roles as user_roles
        where user_roles.user_id = auth.uid()
        order by
          (user_roles.role::text = 'owner') desc,
          (user_roles.role::text = 'admin') desc
        limit 1
      ),
      'user'
    )
  end;
$$;

revoke all on function public.hte_my_role() from public, anon;
grant execute on function public.hte_my_role() to authenticated;

comment on function public.hte_my_role() is
  'Returns only the authenticated caller role for HTE app gating; server RLS remains authoritative.';
