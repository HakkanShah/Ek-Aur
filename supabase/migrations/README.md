# Schema

Applied to the `Ek-Aur` Supabase project (`vgfsuwhhuuodvoyntqbf`, ap-south-1).

- `0001_ek_aur_init.sql` — profiles, friendships, daily totals, row level
  security, and the `add_friend` function.
- `0002_hide_friend_code_generator.sql` — moves the code generator into a schema
  PostgREST does not expose, so it stops being a public REST endpoint.

## One setting that is not in here

Anonymous sign-in has to be switched on by hand: **Authentication → Sign In /
Providers → Anonymous sign-ins**. It is project configuration, not schema, so no
migration can carry it. Until it is on, the app's join button reports exactly
that and stays opted out.

## Why `add_friend` is a `SECURITY DEFINER` function

Row level security deliberately makes a stranger's profile unreadable — that is
what stops the whole table being read. So resolving a friend code cannot be a
select, and the lookup has to run as the definer. It was checked: as a signed-in
user, `select … from profiles where friend_code = '<someone else's>'` returns
**zero rows**.
