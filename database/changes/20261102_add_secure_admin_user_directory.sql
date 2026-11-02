create or replace function public.hte_admin_list_users()
returns table (
  id uuid,
  email text,
  created_at timestamptz,
  last_sign_in_at timestamptz,
  role text
)
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not private.hte_has_role(array['owner','admin']) then
    raise exception 'Not authorized to view platform users'
      using errcode = '42501';
  end if;

  return query
  select
    users.id,
    users.email::text,
    users.created_at,
    users.last_sign_in_at,
    coalesce(user_roles.role, 'user')::text
  from auth.users as users
  left join private.user_roles as user_roles
    on user_roles.user_id = users.id
  order by users.created_at desc
  limit 200;
end;
$$;

revoke all on function public.hte_admin_list_users() from public, anon;
grant execute on function public.hte_admin_list_users() to authenticated;
