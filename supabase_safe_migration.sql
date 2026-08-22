begin;

create extension if not exists pgcrypto;

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
        where conrelid = 'public.wrong_questions'::regclass
          and conname = 'wrong_questions_question_type_check'
    ) then
        alter table public.wrong_questions
            add constraint wrong_questions_question_type_check
            check (question_type in ('MULTIPLE_CHOICE', 'FILL_BLANK')) not valid;
    end if;

    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.wrong_questions'::regclass
          and conname = 'wrong_questions_accepted_answers_array'
    ) then
        alter table public.wrong_questions
            add constraint wrong_questions_accepted_answers_array
            check (jsonb_typeof(accepted_answers) = 'array') not valid;
    end if;

    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.wrong_questions'::regclass
          and conname = 'wrong_questions_hint_used_count_check'
    ) then
        alter table public.wrong_questions
            add constraint wrong_questions_hint_used_count_check
            check (hint_used_count >= 0) not valid;
    end if;
end
$$;

alter table public.wrong_questions
    validate constraint wrong_questions_question_type_check;
alter table public.wrong_questions
    validate constraint wrong_questions_accepted_answers_array;
alter table public.wrong_questions
    validate constraint wrong_questions_hint_used_count_check;

create index if not exists wrong_questions_user_category_time_idx
    on public.wrong_questions (user_id, category, last_wrong_at desc);

create index if not exists wrong_questions_user_source_time_idx
    on public.wrong_questions (user_id, source_reference, last_wrong_at desc);

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

-- The mobile app intentionally treats wrong_questions.bank_id as an optional
-- local-to-cloud reference. Do not add a foreign key to titlelist(id), because
-- a locally created bank can be synchronized after its wrong-question rows.
