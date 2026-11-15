create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null default '', email text not null default '', phone text not null default '',
  role text not null default 'user',
  created_ms bigint not null default (extract(epoch from now())*1000)::bigint,
  last_login_ms bigint not null default (extract(epoch from now())*1000)::bigint
);
create table alerts (
  id bigint generated always as identity primary key,
  type text not null check (type in ('start','stop')),
  message text not null default '', by_name text not null default '',
  by_id uuid default auth.uid(),
  ts_ms bigint not null default (extract(epoch from now())*1000)::bigint
);
alter table profiles enable row level security;
alter table alerts enable row level security;

create function is_admin() returns boolean language sql security definer set search_path = public stable as $$
  select exists (select 1 from profiles where id = auth.uid() and role = 'admin') $$;

create function handle_new_user() returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into profiles (id, email, name, phone)
  values (new.id, coalesce(new.email,''), coalesce(new.raw_user_meta_data->>'name',''), coalesce(new.raw_user_meta_data->>'phone',''));
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function handle_new_user();

create policy "read profiles" on profiles for select to authenticated using (id = auth.uid() or is_admin());
create policy "update own login time" on profiles for update to authenticated using (id = auth.uid());
revoke update on profiles from authenticated;
grant update (last_login_ms) on profiles to authenticated;   -- koi user apna role nahi badal sakta

create policy "read alerts" on alerts for select to authenticated using (true);
create policy "admin sends alerts" on alerts for insert to authenticated with check (is_admin());

alter publication supabase_realtime add table alerts;

-- Pehla admin banane ke liye (apna email daalo, app me sign up karne ke baad chalao):
-- update profiles set role = 'admin' where email = 'tumhara@email.com';
