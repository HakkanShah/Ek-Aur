# Schema

Applied to the `Ek-Aur` Supabase project (`vgfsuwhhuuodvoyntqbf`, ap-south-1).

- `0001_ek_aur_init.sql` — profiles, friendships, daily totals, row level
  security, and the `add_friend` function.
- `0002_hide_friend_code_generator.sql` — moves the code generator into a schema
  PostgREST does not expose.
- `0003_global_leaderboard_usernames.sql` — the design change: one global
  leaderboard. Drops `friendships`, `add_friend` and the code generator;
  renames `display_name` to `username` with a shape check and a unique index on
  `lower(username)`; adds `hidden`; re-points `daily_counts` at `profiles` so
  the leaderboard is one query.

## One setting that is not in here

Anonymous sign-in has to be switched on by hand: **Authentication → Sign In /
Providers → Anonymous sign-ins**. It is project configuration, not schema, so no
migration can carry it. Until it is on, the app's join button reports exactly
that and stays opted out.

## Why `username_available` is a `SECURITY DEFINER` function

A hidden user's profile row is invisible under RLS, but their username is still
taken. An invoker function would report it free and the insert would then fail
on the unique index for no reason the user could understand. Checked on the live
database: as a signed-in user, `username_available('shy_one')` returns **false**
for a hidden account whose row that user cannot see.

## What the index, not the app, decides

The availability check is advisory. Two people can pass it in the same second,
so `profiles_username_unique` is the real guarantee and a `23505` is an ordinary
outcome the screen phrases as "pick another". Verified: an exact duplicate, a
hidden user's name, and a differently-cased duplicate all raise it.
