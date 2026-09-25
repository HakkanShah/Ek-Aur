package com.ekaur.android.ui.friends

import com.ekaur.android.data.remote.LeaderboardRow
import kotlin.random.Random

/**
 * Seed users, so the board is lively before the friend circle fills it in.
 *
 * A brand-new install would otherwise show a leaderboard of one, which reads as
 * broken. These stand-ins keep it populated. Their numbers change **every day**
 * -- seeded by the date, so they hold steady within a day but reshuffle the next
 * -- and each name carries a rough personality so the ranking isn't pure noise.
 *
 * Pure Kotlin (no Android), so the day-to-day behaviour is unit-testable.
 */
object DemoLeaderboard {

    private data class Seed(
        val id: String,
        val name: String,
        val heat: Double,
        /** How much of their scrolling is YouTube Shorts: 0 all Reels, 1 all Shorts. */
        val shorts: Double = 0.0,
    )

    // Short Bengali daak-naam (nicknames). `heat` is a rough 0..1 baseline for
    // how cooked each one usually is, so ranks feel like people, not dice.
    private val seeds = listOf(
        Seed("demo-rana", "rana", 0.82, shorts = 0.0),
        Seed("demo-riju", "riju", 0.90, shorts = 0.3),
        Seed("demo-tuki", "tuki", 0.70, shorts = 1.0),
        Seed("demo-neel", "neel", 0.75, shorts = 0.0),
        Seed("demo-shuvo", "shuvo", 0.65, shorts = 0.5),
        Seed("demo-bubai", "bubai", 0.60, shorts = 1.0),
        Seed("demo-joy", "joy", 0.55, shorts = 0.2),
        Seed("demo-tias", "tias", 0.50, shorts = 0.0),
        Seed("demo-mou", "mou", 0.42, shorts = 0.7),
        Seed("demo-piu", "piu", 0.35, shorts = 1.0),
    )

    /** Demo rows for a given local date string (yyyy-MM-dd). */
    fun rowsFor(date: String): List<LeaderboardRow> = seeds.map { s ->
        val rnd = Random((date + "|" + s.id).hashCode().toLong())
        // Personality sets the centre; the day wobbles it hard so ranks shuffle.
        val wobble = rnd.nextDouble()                     // 0..1, new each day
        val magnitude = 0.45 * s.heat + 0.55 * wobble
        val count = (25 + magnitude * 1150).toInt()       // ~25..1200 reels
        val secsPerReel = 9 + rnd.nextInt(9)              // 9..17s each
        // A habit, not a coin flip: the share wobbles a little day to day, but
        // a Reels person stays a Reels person.
        val share = when (s.shorts) {
            0.0, 1.0 -> s.shorts
            else -> (s.shorts + (rnd.nextDouble() - 0.5) * 0.2).coerceIn(0.05, 0.95)
        }
        val shorts = (count * share).toInt()
        LeaderboardRow(
            userId = s.id,
            username = s.name,
            reelCount = count,
            reelsCount = count - shorts,
            shortsCount = shorts,
            activeMs = count.toLong() * secsPerReel * 1000L,
            avatarVersion = null,
            avatarUrl = avatarUrlFor(s.name),
        )
    }

    /**
     * A generated face for a seed user, stable per name.
     *
     * DiceBear serves a deterministic avatar for a seed, so each demo name gets a
     * consistent, distinct picture without shipping any image or touching the
     * Supabase avatar store. Real people are unaffected -- theirs still come from
     * their upload.
     */
    fun avatarUrlFor(name: String): String =
        "https://api.dicebear.com/9.x/adventurer/png?size=120&seed=$name"

    /**
     * Blend demo rows with the real ones and rank by today's reels. A real row
     * wins any username clash, so a real person is never shadowed by a seed.
     */
    fun blend(real: List<LeaderboardRow>, date: String): List<LeaderboardRow> {
        val realNames = real.mapTo(HashSet()) { it.username.lowercase() }
        val demo = rowsFor(date).filterNot { it.username.lowercase() in realNames }
        return (real + demo).sortedByDescending { it.reelCount }
    }
}
