create extension if not exists pgcrypto;

create table if not exists public.titlelist (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid()
        references auth.users(id) on delete cascade,
    name text not null,
    source text not null default 'XML'
        check (source in ('XML', 'AI')),
    practice_type text null,
    difficulty text null,
    questions jsonb not null default '[]'::jsonb
        check (jsonb_typeof(questions) = 'array'),
    imported_at timestamptz not null default now()
);

create index if not exists titlelist_user_imported_idx
    on public.titlelist (user_id, imported_at desc);

alter table public.titlelist enable row level security;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'titlelist'
          and policyname = 'titlelist_select_own'
    ) then
        create policy titlelist_select_own
        on public.titlelist for select to authenticated
        using (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'titlelist'
          and policyname = 'titlelist_insert_own'
    ) then
        create policy titlelist_insert_own
        on public.titlelist for insert to authenticated
        with check (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'titlelist'
          and policyname = 'titlelist_update_own'
    ) then
        create policy titlelist_update_own
        on public.titlelist for update to authenticated
        using (auth.uid() = user_id)
        with check (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'titlelist'
          and policyname = 'titlelist_delete_own'
    ) then
        create policy titlelist_delete_own
        on public.titlelist for delete to authenticated
        using (auth.uid() = user_id);
    end if;
end
$$;

grant select, insert, update, delete
on public.titlelist
to authenticated;

create table if not exists public.wrong_questions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid()
        references auth.users(id) on delete cascade,
    source text not null
        check (source in ('QUIZ', 'CONTRAST')),
    bank_id uuid null,
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
    question_type text not null default 'MULTIPLE_CHOICE'
        check (question_type in ('MULTIPLE_CHOICE', 'FILL_BLANK')),
    reference_answer text null,
    accepted_answers jsonb not null default '[]'::jsonb,
    explanation text null,
    category text null,
    source_reference text null,
    last_user_answer text null,
    hint_used_count integer not null default 0
        check (hint_used_count >= 0),
    constraint wrong_questions_user_source_question_key
        unique (user_id, source, question_key),
    constraint wrong_questions_options_array
        check (jsonb_typeof(options) = 'array'),
    constraint wrong_questions_answers_array
        check (jsonb_typeof(correct_answers) = 'array')
);

alter table public.wrong_questions
    add column if not exists question_type text not null default 'MULTIPLE_CHOICE',
    add column if not exists reference_answer text,
    add column if not exists accepted_answers jsonb not null default '[]'::jsonb,
    add column if not exists explanation text,
    add column if not exists category text,
    add column if not exists source_reference text,
    add column if not exists last_user_answer text,
    add column if not exists hint_used_count integer not null default 0;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'wrong_questions_question_type_check'
          and conrelid = 'public.wrong_questions'::regclass
    ) then
        alter table public.wrong_questions
            add constraint wrong_questions_question_type_check
            check (question_type in ('MULTIPLE_CHOICE', 'FILL_BLANK'));
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'wrong_questions_accepted_answers_array'
          and conrelid = 'public.wrong_questions'::regclass
    ) then
        alter table public.wrong_questions
            add constraint wrong_questions_accepted_answers_array
            check (jsonb_typeof(accepted_answers) = 'array');
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'wrong_questions_hint_used_count_check'
          and conrelid = 'public.wrong_questions'::regclass
    ) then
        alter table public.wrong_questions
            add constraint wrong_questions_hint_used_count_check
            check (hint_used_count >= 0);
    end if;
end
$$;

comment on column public.wrong_questions.question_type is
'MULTIPLE_CHOICE or FILL_BLANK.';

comment on column public.wrong_questions.accepted_answers is
'Accepted fill-blank answers stored as a JSON array.';

create index if not exists wrong_questions_user_last_wrong_idx
    on public.wrong_questions (user_id, last_wrong_at desc);

create index if not exists wrong_questions_user_favorite_idx
    on public.wrong_questions (user_id, favorite, last_wrong_at desc);

create index if not exists wrong_questions_bank_idx
    on public.wrong_questions (bank_id);

create index if not exists wrong_questions_user_category_time_idx
    on public.wrong_questions (user_id, category, last_wrong_at desc);

create or replace function public.normalize_wrong_question_bank_id()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if new.bank_id is not null and not exists (
        select 1
        from public.titlelist
        where id = new.bank_id
          and user_id = new.user_id
    ) then
        new.bank_id = null;
    end if;
    return new;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'wrong_questions_normalize_bank_id'
          and tgrelid = 'public.wrong_questions'::regclass
          and not tgisinternal
    ) then
        create trigger wrong_questions_normalize_bank_id
        before insert or update on public.wrong_questions
        for each row
        execute function public.normalize_wrong_question_bank_id();
    end if;
end
$$;

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

notify pgrst, 'reload schema';

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
