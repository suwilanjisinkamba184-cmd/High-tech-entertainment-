-- Keep the privileged role-table lookup in the unexposed private schema.
-- The public RPC is SECURITY INVOKER and returns only the caller's own role.
create or replace function private.hte_my_role()
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

revoke all on function private.hte_my_role() from public, anon;
grant execute on function private.hte_my_role() to authenticated;

create or replace function public.hte_my_role()
returns text
language sql
stable
security invoker
set search_path = ''
as $$
  select private.hte_my_role();
$$;

revoke all on function public.hte_my_role() from public, anon;
grant execute on function public.hte_my_role() to authenticated;

comment on function private.hte_my_role() is
  'Privileged internal lookup that returns only the current authenticated caller role.';
comment on function public.hte_my_role() is
  'Authenticated RPC wrapper for the caller role; server RLS remains the authorization authority.';
