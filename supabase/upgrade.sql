-- ===== Satark v2 upgrade: super admin, groups, approval, task, location =====
-- Existing database par chalao. Dobara chalana bhi safe hai.

-- 1) profiles: naye columns
alter table profiles
  add column if not exists task text not null default '',
  add column if not exists task_ms bigint not null default 0,
  add column if not exists lat double precision,
  add column if not exists lng double precision,
  add column if not exists loc_ms bigint not null default 0,
  add column if not exists p_alert boolean not null default false,
  add column if not exists p_groups boolean not null default false,
  add column if not exists p_approve boolean not null default false,
  add column if not exists p_users boolean not null default false,
  add column if not exists p_map boolean not null default false;

do $$ begin
  if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='profiles' and column_name='status') then
    alter table profiles add column status text not null default 'pending' check (status in ('pending','approved','rejected'));
    update profiles set status = 'approved';                         -- purane users auto-approved
    update profiles set p_alert=true, p_groups=true, p_approve=true, p_users=true, p_map=true where role = 'admin';
  end if;
end $$;

-- 2) groups
create table if not exists user_groups (
  id bigint generated always as identity primary key,
  name text not null unique,
  created_ms bigint not null default (extract(epoch from now())*1000)::bigint
);
create table if not exists group_members (
  group_id bigint not null references user_groups(id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  primary key (group_id, user_id)
);
alter table alerts add column if not exists group_id bigint;
alter table alerts add column if not exists group_name text not null default '';
alter table user_groups enable row level security;
alter table group_members enable row level security;

-- 3) helper functions
create or replace function is_approved() returns boolean language sql security definer set search_path = public stable as $$
  select exists (select 1 from profiles where id = auth.uid() and status = 'approved') $$;
create or replace function is_super() returns boolean language sql security definer set search_path = public stable as $$
  select exists (select 1 from profiles where id = auth.uid() and status = 'approved' and role = 'superadmin') $$;
create or replace function is_staff() returns boolean language sql security definer set search_path = public stable as $$
  select exists (select 1 from profiles where id = auth.uid() and status = 'approved' and role in ('admin','superadmin')) $$;
create or replace function is_member(gid bigint) returns boolean language sql security definer set search_path = public stable as $$
  select exists (select 1 from group_members where group_id = gid and user_id = auth.uid()) $$;
create or replace function has_perm(p text) returns boolean language sql security definer set search_path = public stable as $$
  select coalesce((select case
    when role = 'superadmin' then true
    when role = 'admin' then case p when 'alert' then p_alert when 'groups' then p_groups when 'approve' then p_approve
                                    when 'users' then p_users when 'map' then p_map else false end
    else false end
  from profiles where id = auth.uid() and status = 'approved'), false) $$;

-- 4) policies
drop policy if exists "read profiles" on profiles;
drop policy if exists "read profiles v2" on profiles;
create policy "read profiles v2" on profiles for select to authenticated
  using (id = auth.uid() or has_perm('users') or has_perm('approve') or has_perm('map') or has_perm('groups'));

drop policy if exists "read alerts" on alerts;
drop policy if exists "admin sends alerts" on alerts;
drop policy if exists "read alerts v2" on alerts;
drop policy if exists "send alerts v2" on alerts;
create policy "read alerts v2" on alerts for select to authenticated
  using (is_approved() and (group_id is null or is_staff() or is_member(group_id)));
create policy "send alerts v2" on alerts for insert to authenticated with check (has_perm('alert'));

drop policy if exists "groups read" on user_groups;
drop policy if exists "groups write" on user_groups;
create policy "groups read" on user_groups for select to authenticated using (has_perm('groups') or has_perm('alert') or is_member(id));
create policy "groups write" on user_groups for all to authenticated using (has_perm('groups')) with check (has_perm('groups'));

drop policy if exists "members read" on group_members;
drop policy if exists "members write" on group_members;
create policy "members read" on group_members for select to authenticated using (user_id = auth.uid() or has_perm('groups') or has_perm('alert'));
create policy "members write" on group_members for all to authenticated using (has_perm('groups')) with check (has_perm('groups'));

-- 5) safe functions (role/permission sirf inhi se badalte hain)
create or replace function set_status(target uuid, new_status text) returns void language plpgsql security definer set search_path = public as $$
begin
  if not has_perm('approve') then raise exception 'Aapko approval ka access nahi hai'; end if;
  if new_status not in ('pending','approved','rejected') then raise exception 'Galat status'; end if;
  update profiles set status = new_status where id = target and role <> 'superadmin';
end $$;

create or replace function set_access(target uuid, new_role text, a boolean, g boolean, ap boolean, u boolean, m boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not is_super() then raise exception 'Sirf super admin ye kar sakta hai'; end if;
  if new_role not in ('user','admin') then raise exception 'Galat role'; end if;
  update profiles set role = new_role, p_alert = a, p_groups = g, p_approve = ap, p_users = u, p_map = m
  where id = target and role <> 'superadmin';
end $$;

create or replace function set_my_task(t text) returns void language plpgsql security definer set search_path = public as $$
begin
  if t not in ('','QRT','Sentry','Patrol','Routine Task','Duty JCO','Duty NCO','Duty Officer') then raise exception 'Galat task'; end if;
  update profiles set task = t, task_ms = (extract(epoch from now())*1000)::bigint where id = auth.uid() and status = 'approved';
end $$;

create or replace function set_my_location(la double precision, ln double precision) returns void language plpgsql security definer set search_path = public as $$
begin
  update profiles set lat = la, lng = ln, loc_ms = (extract(epoch from now())*1000)::bigint where id = auth.uid() and status = 'approved';
end $$;

create or replace function clear_my_location() returns void language plpgsql security definer set search_path = public as $$
begin
  update profiles set lat = null, lng = null, loc_ms = 0 where id = auth.uid();
end $$;

-- 6) realtime: naye registration ki notification ke liye
do $$ begin
  begin alter publication supabase_realtime add table profiles; exception when others then null; end;
end $$;

-- 7) SUPER ADMIN banane ke liye (apna email daalo, alag se chalao):
-- update profiles set role = 'superadmin', status = 'approved' where lower(email) = lower('tumhara@email.com');
