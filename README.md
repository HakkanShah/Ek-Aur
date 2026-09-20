# Ek Aur

Counts how many Instagram Reels you scroll, and cheers you on while doing it.

A small floating counter sits over Instagram while you're in Reels. Every ten
reels it widens to congratulate you, and it has more to say at round numbers, on
a long sitting, and in the small hours. It never suggests stopping — the number
does that on its own.

Android only. Sideloaded, not on the Play Store.

Everything stays on the phone by default: no account, no server, no analytics.
The friends leaderboard is opt-in and off until you switch it on — until then
the app creates no account and makes no network request at all. Switch it on and
the only thing that ever leaves the phone is a daily total: a date, a count, a
duration. Which reels, when, and every raw event stay local, and the server has
nowhere to put them.

---

## Installing

Two things will go wrong on a fresh install. Both are Android being careful
about sideloaded apps, not the app misbehaving.

### "App blocked to protect your device" (Play Protect)

Play Protect flags any sideloaded app that asks for accessibility access — it's
the same pattern stalkerware uses, so the warning is doing its job. To install
anyway:

1. Play Store → your profile picture → **Play Protect**
2. Gear icon (settings) → turn off **Scan apps with Play Protect**
3. Install the APK
4. Turn it back on

### "App not installed"

You're installing an **older** build over a newer one. Android refuses to
downgrade. Install the APK with the highest version number, or uninstall first
(which wipes your counts).

### Then, in the app's **setup** tab

Three permissions, in order:

1. **Accessibility** — how reels get counted. Nothing works without it.
   Android 13+ blocks this for sideloaded apps with a *"Restricted setting"*
   dialog that offers only an OK button. The way out: App info → ⋮ (top right)
   → **Allow restricted settings**, then go back and enable it.
2. **Overlay** — draws the counter over Instagram.
3. **Battery** — Realme, Oppo, Xiaomi and Vivo kill background services hard,
   and a killed accessibility service stops counting with no visible sign.

---

## Counter lost off the edge of the screen?

It can't be dragged off any more, but if it ever goes missing there's a
**"counter wapas laao"** button in the setup tab that returns it to the middle.

---

## How counting works

Instagram's Reels player is a pager showing exactly one full-screen item, so a
scroll event reports the same start and end position. The main feed is an
ordinary list and reports a span of several visible items. That structural
difference is what separates them — not view names, which turned out to be
unreliable in both directions (the Reels screen contains "comment", the feed
contains a tab literally labelled "Reels").

Counting is position-based: an advance in the pager's position is exactly one
swipe, however fast you fling. A timing fallback covers events that report no
position.

Only Instagram is tracked. Events from other apps are discarded on arrival and
never inspected or recorded.

---

## The dashboard

The **hisaab** tab reads everything back out of the database: today, the last
seven days, and the heaviest day so far; a column per hour of today; a column per
day over 7, 14 or 30; and the last eight sittings with how long each ran.

Tapping any column names it — `3pm · 24 reels` — so no number is locked behind a
gesture. A day nobody scrolled is a gap in the series rather than a day that
quietly disappears and shifts every later column along.

There is one series on that screen, so there is one colour. The column's height
carries the count; shading them darker-where-taller would say the same thing
twice. Only the tallest column is labelled. The screen states numbers and never
comments on them.

---

## When it says something

A line every ten reels, and on top of that a set of milestones that fire **at
most once a day**: 50 / 100 / 200 / 500 reels, a sitting crossing 30 minutes /
1 hour / 2 hours, and still scrolling after 1am or after 3am.

Only one line shows at a time, so when several land together the rest wait for
later reels. Which ones have fired is stored, not held in memory — the
accessibility service gets restarted often enough that it would otherwise repeat
itself all day.

The rules live in `milestone/MilestoneRules.kt` and the wording in
`copy/SarcasmCatalogue.kt`, keyed separately so a rule never carries its own
text.

---

## The leaderboard

Off until you switch it on in the **dost** tab. Joining asks for a name, creates
an anonymous account (no email, no password) and hands you a six-character
friend code to share.

Only daily totals are uploaded, and only ever your own. What a friend can see is
enforced by the database, not by the app: row level security means a signed-in
user reading someone else's counts gets **zero rows**, and a friend code cannot
be resolved by a query at all — that is why adding a friend goes through a single
`SECURITY DEFINER` function that returns nothing but the matched name.

Schema and the one manual setting are in `supabase/migrations/`.

---

## Building

```bash
./gradlew assembleDebug     # APK at app/build/outputs/apk/debug/
./gradlew test              # JVM + Robolectric suite
```

Requires the Android SDK with platform 37 and build-tools 37. `local.properties`
points at it via `sdk.dir`.

The project signs debug builds with a committed keystore (`keystore/ekaur.jks`)
rather than the per-machine default, so builds from anywhere install over each
other without forcing an uninstall. Fine for a sideloaded hobby app; it would
need replacing if this ever went near the Play Store.

---

## Layout

```
detect/      pure Kotlin, zero Android imports — the counting logic, fully unit tested
service/     accessibility service; translates Android events into plain signals
overlay/     the floating counter, its placement maths, and the announcement timing
milestone/   pure Kotlin — which milestone fires, and when
data/        Room: raw events (7 days), hourly and daily totals, sessions
copy/        the Hinglish lines
ui/          app screens: counter, dashboard, friends, setup, event inspector, diagnostics
sync/        pure Kotlin — what to upload, when a token expires, friend codes
data/remote/ the five REST calls the app makes, on OkHttp
ui/stats/    the dashboard; its series maths is plain Kotlin and unit tested
```

`detect/` is deliberately free of Android types. Detection is the part most
likely to break when Instagram ships a new build, so it's the part that's
cheapest to test and re-derive.

### When Instagram breaks detection

The **events** tab records every accessibility event with its view ids and
positions, and exports the lot through the share sheet. That dump is how the
matching rules get rebuilt in minutes instead of from scratch.
