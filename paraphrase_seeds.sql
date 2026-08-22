begin;

create extension if not exists pgcrypto;

create table if not exists public.paraphrase_seeds (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid()
        references auth.users(id) on delete cascade,
    source_text text not null,
    target_text text not null,
    context_text text,
    source_reference text,
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint paraphrase_seeds_source_not_blank
        check (length(btrim(source_text)) > 0),
    constraint paraphrase_seeds_target_not_blank
        check (length(btrim(target_text)) > 0)
);

create index if not exists paraphrase_seeds_user_updated_idx
    on public.paraphrase_seeds (user_id, updated_at desc, id);

create index if not exists paraphrase_seeds_user_source_idx
    on public.paraphrase_seeds (user_id, source_reference);

alter table public.paraphrase_seeds enable row level security;
alter table public.paraphrase_seeds force row level security;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'paraphrase_seeds'
          and policyname = 'paraphrase_seeds_select_own'
    ) then
        create policy paraphrase_seeds_select_own
        on public.paraphrase_seeds for select to authenticated
        using (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'paraphrase_seeds'
          and policyname = 'paraphrase_seeds_insert_own'
    ) then
        create policy paraphrase_seeds_insert_own
        on public.paraphrase_seeds for insert to authenticated
        with check (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'paraphrase_seeds'
          and policyname = 'paraphrase_seeds_update_own'
    ) then
        create policy paraphrase_seeds_update_own
        on public.paraphrase_seeds for update to authenticated
        using (auth.uid() = user_id)
        with check (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'paraphrase_seeds'
          and policyname = 'paraphrase_seeds_delete_own'
    ) then
        create policy paraphrase_seeds_delete_own
        on public.paraphrase_seeds for delete to authenticated
        using (auth.uid() = user_id);
    end if;
end
$$;

revoke all on table public.paraphrase_seeds from anon;
grant select, insert, update, delete on table public.paraphrase_seeds to authenticated;

commit;

-- Verification queries. Run these after the transaction if required.
-- select column_name, data_type, is_nullable, column_default
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'paraphrase_seeds'
-- order by ordinal_position;
--
-- select policyname, cmd, qual, with_check
-- from pg_policies
-- where schemaname = 'public' and tablename = 'paraphrase_seeds'
-- order by policyname;
