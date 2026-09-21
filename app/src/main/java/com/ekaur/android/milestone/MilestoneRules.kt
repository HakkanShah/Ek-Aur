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
        Milestone("reels_1000", Trigger.CountReached(1000), "reels_1000"),
        Milestone("reels_750", Trigger.CountReached(750), "reels_750"),
        Milestone("reels_500", Trigger.CountReached(500), "reels_500"),
        Milestone("session_120", Trigger.SessionMinutes(120), "session_120"),
        Milestone("night_3am", Trigger.ClockBetween(hour = 3), "night_3am"),
        Milestone("reels_300", Trigger.CountReached(300), "reels_300"),
        Milestone("reels_200", Trigger.CountReached(200), "reels_200"),
        Milestone("session_60", Trigger.SessionMinutes(60), "session_60"),
        // Ends where the 3am one begins. Overlapping windows would let the 1am
        // line fire at three in the morning, when it is simply untrue.
        Milestone("night_1am", Trigger.ClockBetween(hour = 1, untilHour = 3), "night_1am"),
        // The midnight hour, ending where the 1am window opens.
        Milestone("night_12am", Trigger.ClockBetween(hour = 0, untilHour = 1), "night_12am"),
        Milestone("reels_100", Trigger.CountReached(100), "reels_100"),
        Milestone("session_30", Trigger.SessionMinutes(30), "session_30"),
        Milestone("reels_50", Trigger.CountReached(50), "reels_50"),
        Milestone("reels_25", Trigger.CountReached(25), "reels_25"),
    )
}
