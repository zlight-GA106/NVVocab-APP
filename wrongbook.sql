create extension if not exists pgcrypto;

create table if not exists public.wrong_questions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid()
        references auth.users(id) on delete cascade,
    source text not null
        check (source in ('QUIZ', 'CONTRAST')),
    bank_id uuid null
        references public.titlelist(id) on delete set null,
    bank_name text not null,
    question_key text not null,
    question_text text not null,
    options jsonb not null default '[]'::jsonb,
    correct_answers jsonb not null default '[]'::jsonb,
    wrong_count integer not null default 1
        check (wrong_count >= 0),
    correct_count integer not null default 0
        check (correct_count >= 0),
    favorite boolean not null default false,
    ai_analysis text null,
    last_wrong_at timestamptz not null default now(),
    last_reviewed_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint wrong_questions_user_source_question_key
        unique (user_id, source, question_key),
    constraint wrong_questions_options_array
        check (jsonb_typeof(options) = 'array'),
    constraint wrong_questions_answers_array
        check (jsonb_typeof(correct_answers) = 'array')
);

create index if not exists wrong_questions_user_last_wrong_idx
    on public.wrong_questions (user_id, last_wrong_at desc);

create index if not exists wrong_questions_user_favorite_idx
    on public.wrong_questions (user_id, favorite, last_wrong_at desc);

create index if not exists wrong_questions_bank_idx
    on public.wrong_questions (bank_id);

alter table public.wrong_questions enable row level security;

do $$
begin
    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'wrong_questions'
          and policyname = 'wrong_questions_select_own'
    ) then
        create policy wrong_questions_select_own
        on public.wrong_questions
        for select
        to authenticated
        using (auth.uid() = user_id);
    end if;

    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'wrong_questions'
          and policyname = 'wrong_questions_insert_own'
    ) then
        create policy wrong_questions_insert_own
        on public.wrong_questions
        for insert
        to authenticated
        with check (auth.uid() = user_id);
    end if;

    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'wrong_questions'
          and policyname = 'wrong_questions_update_own'
    ) then
        create policy wrong_questions_update_own
        on public.wrong_questions
        for update
        to authenticated
        using (auth.uid() = user_id)
        with check (auth.uid() = user_id);
    end if;

    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'wrong_questions'
          and policyname = 'wrong_questions_delete_own'
    ) then
        create policy wrong_questions_delete_own
        on public.wrong_questions
        for delete
        to authenticated
        using (auth.uid() = user_id);
    end if;
end
$$;

grant select, insert, update, delete
on public.wrong_questions
to authenticated;

create or replace function public.set_wrong_questions_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'wrong_questions_set_updated_at'
          and tgrelid = 'public.wrong_questions'::regclass
          and not tgisinternal
    ) then
        create trigger wrong_questions_set_updated_at
        before update on public.wrong_questions
        for each row
        execute function public.set_wrong_questions_updated_at();
    end if;
end
$$;
