package com.ekaur.android.ui.friends

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.data.remote.LeaderboardRow
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Avatar
import com.ekaur.android.ui.common.BannerTone
import com.ekaur.android.ui.common.ChipTone
import com.ekaur.android.ui.common.InfoBanner
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.ScreenHeader
import com.ekaur.android.ui.common.Skeleton
import com.ekaur.android.ui.common.StatusChip
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.common.pressScale
import com.ekaur.android.ui.common.rememberHaptics
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceBlush
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.buttonGradient
import com.ekaur.android.ui.theme.instaGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Podium metals -- the one place a categorical colour earns its keep, because
// gold/silver/bronze is a convention the eye already knows.
private val Gold = Color(0xFFF5B301)
private val Silver = Color(0xFFB6BECC)
private val Bronze = Color(0xFFCD7F45)

private fun medalFor(rank: Int): Color = when (rank) {
    1 -> Gold
    2 -> Silver
    else -> Bronze
}

/**
 * Everyone, ranked on today -- as a race, not a list.
 *
 * The top three stand on a podium that rises in; everyone else runs on a track
 * whose bar length is their reels against the leader's. Rows are keyed by
 * person, so after a refresh they slide into their new places rather than
 * reshuffling in place. Pull down to refresh. Offline, you still see yourself
 * among the pack, with a note saying so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    container: AppContainer,
    onOpenAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val me by container.settings.username.collectAsState()
    val hidden by container.settings.hidden.collectAsState()

    // Null until the first load finishes -- skeletons, not "nobody here".
    var rows by remember { mutableStateOf<List<LeaderboardRow>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var offline by remember { mutableStateOf(false) }
    var updatedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun avatarUrlOf(row: LeaderboardRow): String? =
        row.avatarUrl ?: Avatar.urlFor(
            baseUrl = container.supabase.baseUrl,
            userId = row.userId,
            version = row.avatarVersion,
        )

    suspend fun refresh() {
        loading = true
        // Push first, so the number next to your own name is the one the app is
        // showing you, not whatever was last uploaded half an hour ago.
        withContext(Dispatchers.IO) { runCatching { container.syncer.syncNow() } }
        val today = container.clock.today()
        val real = withContext(Dispatchers.IO) {
            runCatching { container.supabase.leaderboard(today) }
        }.getOrNull()
        offline = real == null

        // Make sure you're on your own board even offline or before your first
        // sync lands, with the count this phone knows.
        val name = me
        val withMe = if (name != null && (real ?: emptyList()).none { it.username == name }) {
            val mine = runCatching { container.counterRepository.observeTodayCount().first() }.getOrDefault(0)
            (real ?: emptyList()) + LeaderboardRow(
                userId = container.settings.userId ?: "me",
                username = name,
                reelCount = mine,
                activeMs = 0L,
                avatarVersion = container.settings.avatarVersion,
            )
        } else {
            real ?: emptyList()
        }
        // Blend in the seed users so the board is always lively; a real person
        // wins any name clash.
        // One row per person: the list is keyed by id, and a duplicate key
        // would crash it.
        rows = DemoLeaderboard.blend(withMe, today).distinctBy { it.userId }
        updatedAt = System.currentTimeMillis()
        now = updatedAt
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    // Keeps "updated 2m ago" honest without recomposing more than twice a minute.
    LaunchedEffect(updatedAt) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val board = rows
    val leader = board?.firstOrNull()?.reelCount?.coerceAtLeast(1) ?: 1
    val myIndex = board?.indexOfFirst { it.username == me } ?: -1

    PullToRefreshBox(
        isRefreshing = loading && board != null,
        onRefresh = {
            haptics.tick()
            scope.launch { refresh() }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = "Leaderboard",
                    subtitle = if (updatedAt == 0L) "Today's race · ranked on reels"
                    else "Today's race · updated ${agoLabel(now - updatedAt)}",
                    trailing = {
                        RefreshChip(loading = loading) {
                            if (!loading) scope.launch { refresh() }
                        }
                    },
                )
            }

            if (offline && board != null) {
                item(key = "offline") {
                    InfoBanner(
                        text = "You're offline. The others are a guess until you reconnect.",
                        glyph = "📡",
                        action = "Retry",
                        onAction = { scope.launch { refresh() } },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (board == null) {
                item(key = "skeleton") { BoardSkeleton() }
                return@LazyColumn
            }

            // Where you stand, pinned above the podium whenever you're not on it.
            if (myIndex >= 3) {
                item(key = "you") {
                    YouCard(
                        rank = myIndex + 1,
                        row = board[myIndex],
                        ahead = board[myIndex - 1],
                        avatarUrl = avatarUrlOf(board[myIndex]),
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "podium") {
                Podium(podium = board.take(3), me = me, avatarUrlOf = ::avatarUrlOf)
            }

            itemsIndexed(board.drop(3), key = { _, row -> row.userId }) { index, row ->
                RaceRow(
                    rank = index + 4,
                    row = row,
                    leader = leader,
                    isMe = row.username == me,
                    avatarUrl = avatarUrlOf(row),
                    modifier = Modifier.animateItem(),
                )
            }

            if (hidden) {
                item(key = "hidden") {
                    InfoBanner(
                        text = "You're hidden. Nobody else sees you on their list.",
                        glyph = "🙈",
                        action = "Change",
                        onAction = onOpenAccount,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "footer") {
                Text(
                    text = "Pull down to refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Smoke,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

private fun agoLabel(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ago"
    }
}

/** A soft pill with a refresh glyph that spins only while the board loads. */
@Composable
private fun RefreshChip(loading: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(interaction, 0.92f)
            .clip(RoundedCornerShape(50))
            .background(SurfaceLav)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !loading,
                role = Role.Button,
                onClickLabel = "Refresh",
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The infinite spin only exists while loading, so an idle chip -- and
        // an off-screen tab kept alive by the pager -- costs no frames.
        val spin = if (loading) {
            val transition = rememberInfiniteTransition(label = "refresh")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                label = "spin",
            )
        } else {
            null
        }
        Text(
            text = "↻",
            style = MaterialTheme.typography.titleLarge,
            color = Chalk,
            modifier = Modifier.graphicsLayer { rotationZ = spin?.value ?: 0f },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (loading) "Loading" else "Refresh",
            style = MaterialTheme.typography.labelLarge,
            color = Chalk,
        )
    }
}

@Composable
private fun BoardSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(230.dp), corner = 24.dp)
        repeat(5) { Skeleton(Modifier.fillMaxWidth().height(62.dp), corner = 18.dp) }
    }
}

/** "You · #7 · 12 behind @neel" -- the race, from your seat. */
@Composable
private fun YouCard(
    rank: Int,
    row: LeaderboardRow,
    ahead: LeaderboardRow,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    val gap = (ahead.reelCount - row.reelCount).coerceAtLeast(0) + 1
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(buttonGradient())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.White)
                .padding(2.dp),
        ) {
            UserAvatar(username = row.username, url = avatarUrl, size = 40.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "You're #$rank",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = "$gap more to pass @${ahead.username}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = row.reelCount.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
    }
}

/* --------------------------------- podium --------------------------------- */

@Composable
private fun Podium(
    podium: List<LeaderboardRow>,
    me: String?,
    avatarUrlOf: (LeaderboardRow) -> String?,
) {
    // Order the columns 2 · 1 · 3 so the winner stands in the middle.
    val first = podium.getOrNull(0)
    val second = podium.getOrNull(1)
    val third = podium.getOrNull(2)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(SurfaceLav, Color.White)))
            .padding(start = 10.dp, end = 10.dp, top = 18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Pedestals rise third, second, then first -- the winner lands last.
            second?.let {
                PodiumSpot(it, rank = 2, blockHeight = 62.dp, avatarSize = 54.dp, delayMs = 120,
                    isMe = it.username == me, avatarUrl = avatarUrlOf(it), modifier = Modifier.weight(1f))
            }
            first?.let {
                PodiumSpot(it, rank = 1, blockHeight = 92.dp, avatarSize = 68.dp, delayMs = 260,
                    isMe = it.username == me, avatarUrl = avatarUrlOf(it), modifier = Modifier.weight(1f))
            }
            third?.let {
                PodiumSpot(it, rank = 3, blockHeight = 44.dp, avatarSize = 54.dp, delayMs = 0,
                    isMe = it.username == me, avatarUrl = avatarUrlOf(it), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PodiumSpot(
    row: LeaderboardRow,
    rank: Int,
    blockHeight: Dp,
    avatarSize: Dp,
    delayMs: Long,
    isMe: Boolean,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    val medal = medalFor(rank)
    val rise = remember { Animatable(0f) }
    val crown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        rise.animateTo(1f, Motion.bouncy())
        if (rank == 1) crown.animateTo(1f, Motion.bouncy())
    }

    Column(
        modifier.graphicsLayer {
            alpha = rise.value.coerceIn(0f, 1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (rank == 1) {
            Text(
                "👑",
                fontSize = 20.sp,
                modifier = Modifier.graphicsLayer {
                    val c = crown.value
                    translationY = (1f - c) * -18.dp.toPx()
                    rotationZ = (1f - c) * -20f
                    alpha = c.coerceIn(0f, 1f)
                },
            )
            Spacer(Modifier.height(2.dp))
        }
        // Avatar with a medal-coloured ring and a rank badge.
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (rank == 1) Brush.linearGradient(listOf(Gold, Color(0xFFFF9D2E)))
                        else Brush.linearGradient(listOf(medal, medal))
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp),
            ) {
                UserAvatar(username = row.username, url = avatarUrl, size = avatarSize)
            }
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(medal),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rank.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = row.username,
            style = MaterialTheme.typography.titleSmall,
            color = if (isMe) Acid else Chalk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isMe) {
            Spacer(Modifier.height(2.dp))
            StatusChip("You", ChipTone.Accent)
        }
        Text(
            text = "${row.reelCount}",
            style = MaterialTheme.typography.titleLarge,
            color = Chalk,
            fontWeight = FontWeight.Bold,
        )
        Text("reels", style = MaterialTheme.typography.bodySmall, color = Smoke)

        Spacer(Modifier.height(8.dp))
        // The pedestal block rises from the floor; taller for the winner.
        Box(
            Modifier
                .fillMaxWidth()
                .height(blockHeight * rise.value.coerceIn(0f, 1.15f))
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(
                    if (rank == 1) instaGradient()
                    else Brush.verticalGradient(listOf(medal.copy(alpha = 0.55f), medal.copy(alpha = 0.30f))),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = rank.toString(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/* -------------------------------- the pack -------------------------------- */

@Composable
private fun RaceRow(
    rank: Int,
    row: LeaderboardRow,
    leader: Int,
    isMe: Boolean,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    val fraction = (row.reelCount.toFloat() / leader).coerceIn(0.04f, 1f)
    // Starts from the line on first show, so the pack visibly races off.
    val run = remember { Animatable(0f) }
    LaunchedEffect(fraction) { run.animateTo(fraction, tween(700, easing = Motion.Decelerate)) }

    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isMe) SurfaceBlush else Color.White)
            .then(if (isMe) Modifier.border(1.5.dp, instaGradient(), shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = Smoke,
            modifier = Modifier.width(26.dp),
        )
        UserAvatar(username = row.username, url = avatarUrl, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.username,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isMe) Acid else Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    StatusChip("You", ChipTone.Accent)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = row.reelCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Chalk,
                )
            }
            Spacer(Modifier.height(7.dp))
            // The race track: a lane, and how far along it this runner is.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isMe) Color.White else SurfaceLav),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(run.value.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isMe) buttonGradient() else instaGradient()),
                )
            }
        }
    }
}
