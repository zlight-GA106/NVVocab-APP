-- Optional server upgrade. Does not reset data or modify existing RLS policies.
begin;
alter table public.wordbase
  add column if not exists parts_of_speech text not null default '',
  add column if not exists pos_needs_review boolean not null default false,
  add column if not exists pos_explanation text not null default '';

alter table public.practice_attempts
  add column if not exists actual_pos text not null default '',
  add column if not exists selected_pos text not null default '',
  add column if not exists error_type text;

-- This is the check created by practice_attempts.sql; retain all previous modes.
alter table public.practice_attempts drop constraint if exists practice_attempts_mode_check;
alter table public.practice_attempts add constraint practice_attempts_mode_check check (mode in (
  'WORD_DICTATION','WORD_SPELLING','QUIZ_CHOICE','QUIZ_FILL_BLANK',
  'CHINESE_TO_ENGLISH','ENGLISH_TO_CHINESE','ENGLISH_DEFINITION_TO_ENGLISH',
  'WORD_POS_CLASSIFICATION','CONTEXT_POS_CLASSIFICATION','SLOT_POS_CLASSIFICATION','CET4_WORD_BANK'
)) not valid;
alter table public.practice_attempts validate constraint practice_attempts_mode_check;
commit;
