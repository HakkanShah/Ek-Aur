package com.ekaur.android.ui.friends

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.data.remote.LeaderboardRow
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Avatar
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.InkRaised
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.instaGradient
import kotlinx.coroutines.Dispatchers
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
 * The top three stand on a podium; everyone else runs on a track whose bar length
 * is their reels against the leader's, so the screen reads as a race at a glance.
 * There is nothing to join and nobody to add: the username taken on first launch
 * put this device on the list, alongside the seed users that keep it lively.
 */
@Composable
fun FriendsScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val me by container.settings.username.collectAsState()
    val hidden by container.settings.hidden.collectAsState()

    var rows by remember { mutableStateOf<List<LeaderboardRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

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
        // Blend in the seed users so the board is always lively; a real person
        // wins any name clash. Offline just means the board is all seeds + you.
        rows = DemoLeaderboard.blend(real ?: emptyList(), today)
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val leader = rows.firstOrNull()?.reelCount?.coerceAtLeast(1) ?: 1
    val podium = rows.take(3)
    val pack = rows.drop(3)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Title + refresh.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Leaderboard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Chalk,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Today's race · ranked on reels",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
            }
            FlatButton(
                text = if (loading) "..." else "refresh",
                onClick = { if (!loading) scope.launch { refresh() } },
            )
        }

        Spacer(Modifier.height(16.dp))

        when {
            rows.isEmpty() && loading ->
                Text("Warming up the track…", style = MaterialTheme.typography.bodyMedium, color = Smoke)

            rows.isEmpty() ->
                Text(
                    text = "Nobody has scrolled yet today. you're first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )

            else -> {
                if (podium.isNotEmpty()) {
                    Podium(podium = podium, me = me, avatarUrlOf = ::avatarUrlOf)
                    Spacer(Modifier.height(16.dp))
                }
                if (pack.isNotEmpty()) {
                    Card(contentPadding = 8.dp) {
                        pack.forEachIndexed { index, row ->
                            RaceRow(
                                rank = index + 4,
                                row = row,
                                leader = leader,
                                isMe = row.username == me,
                                avatarUrl = avatarUrlOf(row),
                            )
                        }
                    }
                }
            }
        }

        if (hidden) {
            Spacer(Modifier.height(12.dp))
            Card {
                Text(
                    text = "You're hidden — you don't show on other people's list. " +
                        "turn it back on in Setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
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
            .background(
                Brush.verticalGradient(listOf(SurfaceLav, Color(0xFFFFFFFF))),
            )
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            second?.let {
                PodiumSpot(it, rank = 2, blockHeight = 62.dp, avatarSize = 54.dp,
                    isMe = it.username == me, avatarUrl = avatarUrlOf(it), modifier = Modifier.weight(1f))
            }
            first?.let {
                PodiumSpot(it, rank = 1, blockHeight = 92.dp, avatarSize = 68.dp,
                    isMe = it.username == me, avatarUrl = avatarUrlOf(it), modifier = Modifier.weight(1f))
            }
            third?.let {
                PodiumSpot(it, rank = 3, blockHeight = 44.dp, avatarSize = 54.dp,
                    isMe = it.username == me, avatarUrl = avatarUrlOf(it), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PodiumSpot(
    row: LeaderboardRow,
    rank: Int,
    blockHeight: androidx.compose.ui.unit.Dp,
    avatarSize: androidx.compose.ui.unit.Dp,
    isMe: Boolean,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    val medal = medalFor(rank)
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (rank == 1) {
            Text("👑", fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
        }
        // Avatar with a medal-coloured ring and a rank badge.
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (rank == 1) Brush.linearGradient(listOf(Gold, Color(0xFFFF9D2E))) else Brush.linearGradient(listOf(medal, medal)))
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
            text = row.username + if (isMe) " (you)" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isMe) Acid else Chalk,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${row.reelCount}",
            style = MaterialTheme.typography.titleLarge,
            color = Chalk,
            fontWeight = FontWeight.Black,
        )
        Text("reels", style = MaterialTheme.typography.bodyMedium, color = Smoke)

        Spacer(Modifier.height(8.dp))
        // The pedestal block: taller for the winner.
        Box(
            Modifier
                .fillMaxWidth()
                .height(blockHeight)
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
) {
    val fraction = (row.reelCount.toFloat() / leader).coerceIn(0.04f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "race",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) InkRaised else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = Ash,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )
        UserAvatar(username = row.username, url = avatarUrl, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.username + if (isMe) " (you)" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isMe) Acid else Chalk,
                    fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = row.reelCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Chalk,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            // The race track: a lane, and how far along it this runner is.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceLav),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isMe) Brush.linearGradient(listOf(Acid, Acid)) else instaGradient()),
                )
            }
        }
    }
}
