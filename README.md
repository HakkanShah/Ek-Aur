# Ek Aur

Counts how many Instagram Reels you scroll, and cheers you on while doing it.

A small floating counter sits over Instagram while you're in Reels. Every ten
reels it widens to congratulate you. It never suggests stopping — the number
does that on its own.

Android only. Sideloaded, not on the Play Store. Everything stays on the phone:
no account, no server, no analytics.

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
overlay/     the floating counter, its placement maths, and the milestone copy timing
data/        Room: raw events (7 days), hourly and daily totals, sessions
copy/        the Hinglish lines
ui/          app screens: counter, setup, event inspector, diagnostics
```

`detect/` is deliberately free of Android types. Detection is the part most
likely to break when Instagram ships a new build, so it's the part that's
cheapest to test and re-derive.

### When Instagram breaks detection

The **events** tab records every accessibility event with its view ids and
positions, and exports the lot through the share sheet. That dump is how the
matching rules get rebuilt in minutes instead of from scratch.
