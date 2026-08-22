begin;

create table if not exists public.practice_attempts (
    id uuid primary key,
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    session_id uuid not null,
    item_id text not null,
    source_id text,
    mode text not null check (
        mode in (
            'WORD_DICTATION',
            'WORD_SPELLING',
            'QUIZ_CHOICE',
            'QUIZ_FILL_BLANK',
            'CHINESE_TO_ENGLISH',
            'ENGLISH_TO_CHINESE',
            'ENGLISH_DEFINITION_TO_ENGLISH'
        )
    ),
    sequence_index integer not null check (sequence_index >= 0),
    question text not null,
    options jsonb not null default '[]'::jsonb check (jsonb_typeof(options) = 'array'),
    first_answer text not null default '',
    final_answer text not null default '',
    reference_answer text not null default '',
    accepted_answers jsonb not null default '[]'::jsonb check (jsonb_typeof(accepted_answers) = 'array'),
    explanation text,
    correct boolean not null,
    first_answer_correct boolean not null,
    active_time_ms bigint not null check (active_time_ms >= 0),
    hint_used boolean not null default false,
    answered_at timestamptz not null,
    created_at timestamptz not null default now(),
    unique (user_id, session_id, sequence_index)
);

alter table public.practice_attempts add column if not exists source_id text;

create index if not exists practice_attempts_user_time_idx
    on public.practice_attempts (user_id, answered_at desc);

create index if not exists practice_attempts_item_mode_time_idx
    on public.practice_attempts (user_id, item_id, mode, answered_at asc);

create index if not exists practice_attempts_session_idx
    on public.practice_attempts (user_id, session_id, sequence_index);

create index if not exists practice_attempts_source_time_idx
    on public.practice_attempts (user_id, source_id, answered_at desc);

alter table public.practice_attempts enable row level security;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public' and tablename = 'practice_attempts'
          and policyname = 'practice_attempts_select_own'
    ) then
        create policy practice_attempts_select_own
            on public.practice_attempts for select to authenticated
            using (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public' and tablename = 'practice_attempts'
          and policyname = 'practice_attempts_insert_own'
    ) then
        create policy practice_attempts_insert_own
            on public.practice_attempts for insert to authenticated
            with check (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public' and tablename = 'practice_attempts'
          and policyname = 'practice_attempts_update_own'
    ) then
        create policy practice_attempts_update_own
            on public.practice_attempts for update to authenticated
            using (auth.uid() = user_id)
            with check (auth.uid() = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public' and tablename = 'practice_attempts'
          and policyname = 'practice_attempts_delete_own'
    ) then
        create policy practice_attempts_delete_own
            on public.practice_attempts for delete to authenticated
            using (auth.uid() = user_id);
    end if;
end
$$;

grant select, insert, update, delete on public.practice_attempts to authenticated;

commit;
