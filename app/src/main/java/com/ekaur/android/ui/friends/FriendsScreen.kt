package com.ekaur.android.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ekaur.android.ui.theme.instaGradient
import com.ekaur.android.ui.theme.Surface
import com.ekaur.android.ui.theme.Ink
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekaur.android.data.remote.LeaderboardRow
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Avatar
import com.ekaur.android.sync.SyncResult
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.stats.Hairline
import com.ekaur.android.ui.stats.formatDuration
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.InkRaised
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everyone, ranked on today.
 *
 * There is nothing to join and nobody to add: the username taken on first
 * launch put this device on the list. Ranked on today's reels so it matches the
 * number the pill and the home screen already show -- a leaderboard that
 * disagreed with the app's own headline would just look broken.
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
    var problem by remember { mutableStateOf<String?>(null) }

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
        problem = null
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Card {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Today's race")
                FlatButton(
                    text = if (loading) "..." else "refresh",
                    onClick = { if (!loading) scope.launch { refresh() } },
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                problem != null ->
                    Text(problem!!, style = MaterialTheme.typography.bodyMedium, color = Heat)

                rows.isEmpty() && loading ->
                    Text("Loading...", style = MaterialTheme.typography.bodyMedium, color = Smoke)

                rows.isEmpty() ->
                    Text(
                        text = "Nobody has scrolled yet today. you're first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Smoke,
                    )

                else -> rows.forEachIndexed { index, row ->
                    if (index > 0) Hairline()
                    LeaderRow(
                        rank = index + 1,
                        row = row,
                        isMe = row.username == me,
                        avatarUrl = Avatar.urlFor(
                            baseUrl = container.supabase.baseUrl,
                            userId = row.userId,
                            version = row.avatarVersion,
                        ),
                    )
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

@Composable
private fun LeaderRow(rank: Int, row: LeaderboardRow, isMe: Boolean, avatarUrl: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                color = if (isMe) InkRaised else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(vertical = 10.dp, horizontal = if (isMe) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A gradient chip for the podium, a plain number for the rest.
        Box(
            Modifier.width(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (rank <= 3) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(brush = instaGradient()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                }
            } else {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // Stories-style gradient ring around every avatar.
        Box(
            Modifier
                .clip(CircleShape)
                .background(brush = instaGradient())
                .padding(2.5.dp)
                .clip(CircleShape)
                .background(Surface)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            UserAvatar(username = row.username, url = avatarUrl, size = 36.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.username + if (isMe) "  (you)" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isMe) Acid else Chalk,
            )
            if (row.activeMs > 0) {
                Text(
                    text = formatDuration(row.activeMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
            }
        }
        if (rank == 1) {
            com.ekaur.android.ui.common.GradientNumber(
                text = row.reelCount.toString(),
                style = MaterialTheme.typography.titleLarge,
            )
        } else {
            Text(
                text = row.reelCount.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Chalk,
            )
        }
    }
}
