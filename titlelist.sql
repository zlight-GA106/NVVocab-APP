begin;

create table if not exists public.titlelist (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid()
        references auth.users(id) on delete cascade,
    name text not null,
    source text not null default 'XML'
        check (source in ('XML', 'AI')),
    practice_type text,
    difficulty text,
    questions jsonb not null default '[]'::jsonb
        check (jsonb_typeof(questions) = 'array'),
    imported_at timestamptz not null default now()
);

create index if not exists titlelist_user_imported_at_idx
    on public.titlelist (user_id, imported_at desc);

create index if not exists titlelist_user_source_idx
    on public.titlelist (user_id, source);

alter table public.titlelist enable row level security;

do $$
begin
    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'titlelist'
          and policyname = 'titlelist_select_own'
    ) then
        execute 'create policy titlelist_select_own on public.titlelist for select to authenticated using (auth.uid() = user_id)';
    end if;

    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'titlelist'
          and policyname = 'titlelist_insert_own'
    ) then
        execute 'create policy titlelist_insert_own on public.titlelist for insert to authenticated with check (auth.uid() = user_id)';
    end if;

    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'titlelist'
          and policyname = 'titlelist_update_own'
    ) then
        execute 'create policy titlelist_update_own on public.titlelist for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id)';
    end if;

    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'titlelist'
          and policyname = 'titlelist_delete_own'
    ) then
        execute 'create policy titlelist_delete_own on public.titlelist for delete to authenticated using (auth.uid() = user_id)';
    end if;
end
$$;

grant select, insert, update, delete on public.titlelist to authenticated;

comment on table public.titlelist is
'NVVocab question banks. questions is an array of question objects with options and answers.';

commit;

notify pgrst, 'reload schema';
