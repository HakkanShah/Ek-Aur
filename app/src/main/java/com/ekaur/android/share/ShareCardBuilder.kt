package com.ekaur.android.share

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Avatar
import com.ekaur.android.ui.stats.dailySeries
import com.ekaur.android.ui.stats.hourLabel
import com.ekaur.android.ui.stats.hourlySeries
import com.ekaur.android.ui.stats.peakIndex
import kotlinx.coroutines.flow.first

/**
 * Collects everything a card needs, in one place, off the main thread.
 *
 * All of it is read from Room rather than the server, so a card can be made and
 * posted with no signal at all -- which is the moment someone is most likely to
 * be scrolling.
 */
object ShareCardBuilder {

    suspend fun gather(container: AppContainer, context: Context): Pair<CardStats, Bitmap?>? {
        val repo = container.counterRepository
        val dates = repo.lastDays(7)

        val stats = runCatching {
            CardStats(
                username = container.settings.username.value.orEmpty(),
                reelsToday = repo.observeTodayCount().first(),
                activeMsToday = repo.observeTodayActiveMs().first(),
                week = dailySeries(repo.observeDaysSince(dates.first()).first(), dates)
                    .map { it.reels },
                bestEver = repo.observeBestDay().first()?.total ?: 0,
                peakHour = hourlySeries(repo.observeTodayHours().first())
                    .let { hours -> peakIndex(hours)?.let(::hourLabel) },
                // Instagram-only keeps the classic "Reels today" (with its
                // singular); anyone counting Shorts gets their own words.
                label = container.settings.countedApps.value
                    .takeIf { it != setOf(com.ekaur.android.detect.TrackedApp.Instagram) }
                    ?.let { com.ekaur.android.ui.common.AppWords.today(it) },
                split = com.ekaur.android.ui.common.AppWords.split(repo.observeTodayByApp().first()),
            )
        }.getOrNull() ?: return null

        return stats to avatarFor(container, context)
    }

    /**
     * The user's own picture, from cache only, so the card is ready instantly.
     *
     * The avatar is already warm in Coil's cache from the leaderboard and setup
     * screens -- read through the app's one shared loader, so its memory cache
     * is actually hit -- so a cache read costs nothing; disabling the network keeps a card
     * from ever waiting on a download. A miss just falls back to the initial,
     * which still looks like a card.
     */
    private suspend fun avatarFor(container: AppContainer, context: Context): Bitmap? {
        val avatar = container.settings.avatar.value
        val url = Avatar.urlFor(
            baseUrl = container.supabase.baseUrl,
            userId = avatar?.owner.orEmpty(),
            version = avatar?.version,
        ) ?: return null

        return runCatching {
            val result = SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context)
                    .data(url)
                    // A software bitmap: the card is drawn on a software Canvas,
                    // and drawing a hardware bitmap there throws. This is what
                    // left the card stuck on "getting it ready" for anyone with a
                    // photo set.
                    .allowHardware(false)
                    .networkCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
            (result as? SuccessResult)?.image?.toBitmap()
        }.getOrNull()
    }
}
