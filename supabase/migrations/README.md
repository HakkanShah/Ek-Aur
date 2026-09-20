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

## Account recovery

`account_keys` holds the device key and the recovery code. It has RLS enabled
and **no policies**, which is the point: profiles is readable by every signed-in
user so the leaderboard can be built, and a credential stored there would be
readable by everyone who installs the app. Only the `SECURITY DEFINER` functions
touch it. Verified: a signed-in user selecting from `account_keys` gets zero
rows, including for their own row.

`recover_account` repoints the profile's id at the new auth user; the daily
counts follow through `ON UPDATE CASCADE` rather than being copied, so the move
cannot be half-done. Verified end to end: an account with 412 counts was
recovered onto a fresh user with the counts intact and nothing orphaned.

`change_username` enforces the 14-day cooldown server-side. Verified: change,
then refused with "cooldown: 14 days left", then allowed again after the
timestamp was moved back 15 days.

## Avatars

`0005_avatars.sql` creates the `avatars` bucket, public-read with a 256KB
ceiling and `image/webp` as the only accepted type, so the size limit does not
depend on the client behaving. Write policies allow exactly one path per user.
Verified: a signed-in user may write `<their-id>.webp`, and is refused another
user's path, `other.webp/../mine.webp`, and any other name in the bucket.

## What the index, not the app, decides

The availability check is advisory. Two people can pass it in the same second,
so `profiles_username_unique` is the real guarantee and a `23505` is an ordinary
outcome the screen phrases as "pick another". Verified: an exact duplicate, a
hidden user's name, and a differently-cased duplicate all raise it.
