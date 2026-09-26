package com.ekaur.android.milestone

/**
 * The milestones the app watches for.
 *
 * **Order is the priority.** When more than one becomes eligible on the same
 * reel, the first listed fires and the rest stay eligible for later today, so
 * two never land on top of each other. Listed most notable first, which is why
 * the small hours outrank a merely large number.
 */
object MilestoneRules {

    val DEFAULT: List<Milestone> = listOf(
        // The joke numbers come first: each is only true on its own reel, so
        // on a tie it goes now and the round number waits for the next one.
        exactly(7), exactly(18), exactly(49), exactly(69), exactly(99),
        exactly(111), exactly(143), exactly(175), exactly(183), exactly(264),
        exactly(404), exactly(420), exactly(666), exactly(973), exactly(2011),

        // The count ladder is dense on purpose, so a heavy day earns a fresh
        // line often rather than going quiet for hundreds of reels between the
        // round hundreds.
        Milestone("reels_2000", Trigger.CountReached(2000), "reels_2000"),
        Milestone("reels_1500", Trigger.CountReached(1500), "reels_1500"),
        Milestone("reels_1250", Trigger.CountReached(1250), "reels_1250"),
        Milestone("reels_1000", Trigger.CountReached(1000), "reels_1000"),
        Milestone("reels_800", Trigger.CountReached(800), "reels_800"),
        Milestone("reels_750", Trigger.CountReached(750), "reels_750"),
        Milestone("reels_600", Trigger.CountReached(600), "reels_600"),
        Milestone("reels_500", Trigger.CountReached(500), "reels_500"),
        Milestone("session_120", Trigger.SessionMinutes(120), "session_120"),
        Milestone("night_3am", Trigger.ClockBetween(hour = 3), "night_3am"),
        Milestone("reels_400", Trigger.CountReached(400), "reels_400"),
        Milestone("reels_300", Trigger.CountReached(300), "reels_300"),
        Milestone("reels_250", Trigger.CountReached(250), "reels_250"),
        Milestone("reels_200", Trigger.CountReached(200), "reels_200"),
        Milestone("session_60", Trigger.SessionMinutes(60), "session_60"),
        // Ends where the 3am one begins. Overlapping windows would let the 1am
        // line fire at three in the morning, when it is simply untrue.
        Milestone("night_1am", Trigger.ClockBetween(hour = 1, untilHour = 3), "night_1am"),
        // The midnight hour, ending where the 1am window opens.
        Milestone("night_12am", Trigger.ClockBetween(hour = 0, untilHour = 1), "night_12am"),
        Milestone("reels_150", Trigger.CountReached(150), "reels_150"),
        Milestone("reels_100", Trigger.CountReached(100), "reels_100"),
        Milestone("session_30", Trigger.SessionMinutes(30), "session_30"),
        Milestone("reels_50", Trigger.CountReached(50), "reels_50"),
        Milestone("reels_25", Trigger.CountReached(25), "reels_25"),
    )

    private fun exactly(n: Int) = Milestone("exact_$n", Trigger.CountExactly(n), "exact_$n")
}
