package com.ekaur.android.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.di.AppContainer
import com.ekaur.android.milestone.NextMilestone
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.share.CardStats
import com.ekaur.android.share.ShareCardBuilder
import com.ekaur.android.sync.Avatar
import com.ekaur.android.ui.common.AnimatedCount
import com.ekaur.android.ui.common.AppBadge
import com.ekaur.android.ui.common.AppBadges
import com.ekaur.android.ui.common.AppWords
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.ChipTone
import com.ekaur.android.ui.common.EkIcon
import com.ekaur.android.ui.common.EkIcons
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.GradientProgress
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.PulseDot
import com.ekaur.android.ui.common.Skeleton
import com.ekaur.android.ui.common.StatusChip
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.common.mark
import com.ekaur.android.ui.common.pressScale
import com.ekaur.android.ui.common.rememberToday
import com.ekaur.android.ui.common.reveal
import com.ekaur.android.ui.onboarding.PermissionState
import com.ekaur.android.ui.stats.formatDuration
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Poppins
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceBlush
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.instaGradient
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The first thing anyone sees: today's number, big.
 *
 * Everything else earns its place under it -- how far to the next roast, a
 * three-tile summary, the share card, and whether counting is actually live.
 * While a required switch is off, that status jumps to the top, because
 * nothing else on the screen means anything until it's fixed.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    permissions: PermissionState,
    onOpenWizard: () -> Unit,
    onOpenSetupTab: () -> Unit,
    onOpenAccount: () -> Unit,
    onShare: (CardStats, android.graphics.Bitmap?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = container.counterRepository
    val today = rememberToday()

    // Keyed on the day so the queries re-subscribe at midnight. Remembered, so
    // a recomposition never rebuilds a Room flow.
    val count by remember(today) { repo.observeTodayCount() }.collectAsState(initial = null)
    val activeMs by remember(today) { repo.observeTodayActiveMs() }.collectAsState(initial = 0L)
    val connected by container.serviceStatus.connected.collectAsState()
    val state by container.serviceStatus.detectorState.collectAsState()
    val username by container.settings.username.collectAsState()
    val apps by container.settings.countedApps.collectAsState()
    val lastPackage by container.serviceStatus.lastEventPackage.collectAsState()
    val byApp by remember(today) { repo.observeTodayByApp() }.collectAsState(initial = emptyMap())
    // Existing users are asked once whether to count Shorts too; never
    // switched on behind their back by an update.
    var askShorts by remember {
        mutableStateOf(
            !container.settings.shortsAsked &&
                TrackedApp.YouTube !in container.settings.countedApps.value &&
                ServiceControl.isInstalled(context, TrackedApp.YouTube),
        )
    }

    val avatar by container.settings.avatar.collectAsState()
    val shown = count ?: 0
    val live = state == "InReels" && permissions.service

    var building by remember { mutableStateOf(false) }
    val needsSetup = !permissions.allGranted

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Header(
            username = username.orEmpty(),
            avatarUrl = Avatar.urlFor(
                baseUrl = container.supabase.baseUrl,
                userId = avatar?.owner.orEmpty(),
                version = avatar?.version,
            ),
            onOpenAccount = onOpenAccount,
            modifier = Modifier.reveal(0),
        )

        AnimatedVisibility(
            visible = askShorts,
            exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.quick()),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                ShortsPrompt(
                    onYes = {
                        container.settings.setCountedApps(apps + TrackedApp.YouTube)
                        container.settings.appsChosen = true
                        container.settings.shortsAsked = true
                        askShorts = false
                    },
                    onNo = {
                        container.settings.appsChosen = true
                        container.settings.shortsAsked = true
                        askShorts = false
                    },
                )
            }
        }

        if (needsSetup) {
            Spacer(Modifier.height(16.dp))
            StatusCard(
                permissions = permissions,
                connected = connected,
                state = state,
                apps = apps,
                lastPackage = lastPackage,
                onOpenWizard = onOpenWizard,
                onOpenSetupTab = onOpenSetupTab,
                modifier = Modifier.reveal(1),
            )
        }

        Spacer(Modifier.height(10.dp))
        Hero(
            count = count,
            activeMs = activeMs,
            live = live,
            label = AppWords.today(apps),
            apps = apps,
            modifier = Modifier.reveal(2),
        )

        val reelsToday = byApp[TrackedApp.Instagram] ?: 0
        val shortsToday = byApp[TrackedApp.YouTube] ?: 0
        AnimatedVisibility(visible = reelsToday > 0 && shortsToday > 0) {
            Column {
                Spacer(Modifier.height(12.dp))
                SplitBar(reels = reelsToday, shorts = shortsToday)
            }
        }

        Spacer(Modifier.height(14.dp))
        NextRoastChip(count = shown, apps = apps, modifier = Modifier.reveal(3))

        Spacer(Modifier.height(12.dp))
        ReminderCard(
            container = container,
            count = shown,
            today = today,
            overlayAllowed = permissions.overlay,
            modifier = Modifier.reveal(3),
        )

        Spacer(Modifier.height(18.dp))
        Column(Modifier.reveal(5)) {
            FlatButton(
                text = if (building) "Making your card…" else "Share today's card",
                icon = EkIcons.Share,
                emphasised = !needsSetup && shown > 0,
                enabled = shown > 0,
                loading = building,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (building) return@FlatButton
                    building = true
                    scope.launch {
                        val ready = withContext(Dispatchers.IO) {
                            runCatching { ShareCardBuilder.gather(container, context) }.getOrNull()
                        }
                        building = false
                        if (ready != null) {
                            onShare(ready.first, ready.second)
                        } else {
                            Toast.makeText(context, "Couldn't make the card. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
            if (shown == 0 && count != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Scroll a few ${AppWords.unit(apps).lowercase()} to unlock your card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Smoke,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (!needsSetup) {
            Spacer(Modifier.height(16.dp))
            StatusCard(
                permissions = permissions,
                connected = connected,
                state = state,
                apps = apps,
                lastPackage = lastPackage,
                onOpenWizard = onOpenWizard,
                onOpenSetupTab = onOpenSetupTab,
                modifier = Modifier.reveal(6),
            )
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Header(
    username: String,
    avatarUrl: String?,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Text(text = "EK AUR", style = WordmarkStyle)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "BETA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Acid,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(SurfaceBlush)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Text(
                text = "one more",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
                letterSpacing = 2.sp,
            )
        }
        // A stories-style gradient ring, matching the leaderboard avatars.
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressScale(interaction, 0.9f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Account",
                    onClick = onOpenAccount,
                )
                .background(brush = instaGradient())
                .padding(2.5.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(2.dp),
        ) {
            UserAvatar(username = username, url = avatarUrl, size = 42.dp)
        }
    }
}

private val WordmarkStyle: TextStyle get() = TextStyle(
    fontFamily = Poppins,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    letterSpacing = 4.sp,
    brush = instaGradient(),
)

/**
 * The hero: today's count on a soft glow. The number counts up; the glow
 * breathes only while you're actually in Reels, so a quiet day is quiet.
 */
@Composable
private fun Hero(
    count: Int?,
    activeMs: Long,
    live: Boolean,
    label: String,
    apps: Set<TrackedApp>,
    modifier: Modifier = Modifier,
) {
    val breathe = if (live) {
        val t = rememberInfiniteTransition(label = "glow")
        t.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
            label = "glow-scale",
        )
    } else {
        null
    }
    Column(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                val radius = size.width * 0.42f
                val middle = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val brush = Brush.radialGradient(
                    colors = listOf(SurfaceLav, SurfaceBlush.copy(alpha = 0.4f), Color.Transparent),
                    center = middle,
                    radius = radius * 1.1f,
                )
                onDrawBehind {
                    val s = breathe?.value ?: 1f
                    drawCircle(brush = brush, radius = radius * s, center = middle)
                }
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (count == null) {
            Skeleton(Modifier.size(width = 120.dp, height = 84.dp), corner = 20.dp)
        } else {
            AnimatedCount(
                value = count,
                style = MaterialTheme.typography.displayLarge.copy(brush = instaGradient()),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBadges(apps, size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = Smoke,
                fontWeight = FontWeight.Medium,
            )
        }
        if (activeMs > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = formatDuration(activeMs) + " watched",
                style = MaterialTheme.typography.labelMedium,
                color = Smoke,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** How far to the next roast, on the real milestone ladder. */
@Composable
private fun NextRoastChip(count: Int, apps: Set<TrackedApp>, modifier: Modifier = Modifier) {
    val roast = remember(count) { NextMilestone.forCount(count) }
    val line = when {
        count == 0 -> "Nothing yet. ${AppWords.appNames(apps)} is waiting."
        roast.next == null -> "Past every milestone. Legend."
        else -> "Next roast at ${roast.next} · ${roast.toGo} to go"
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EkIcon(EkIcons.Target, tint = Acid, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = line,
                transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.quick()) },
                label = "roast-line",
                modifier = Modifier.weight(1f),
            ) { text ->
                Text(text = text, style = MaterialTheme.typography.titleSmall, color = Chalk)
            }
        }
        Spacer(Modifier.height(10.dp))
        GradientProgress(fraction = roast.fraction, height = 6.dp, modifier = Modifier.fillMaxWidth())
    }
}

/** The look of the counting-status card: a colour, a headline, and a plain line. */
private data class StatusLook(
    val color: Color,
    val title: String,
    val detail: String,
    val live: Boolean,
)

private fun statusLook(
    permissions: PermissionState,
    connected: Boolean,
    state: String,
    apps: Set<TrackedApp>,
    lastPackage: String?,
): StatusLook {
    val names = AppWords.appNames(apps)
    // The app actually in use right now, for "YouTube's open -- swipe into Shorts".
    val current = TrackedApp.forPackage(lastPackage)?.takeIf { it in apps }
    return when {
        !permissions.service ->
            StatusLook(Heat, "Counting is off", "Finish setup to start counting.", false)
        !permissions.running && !connected ->
            StatusLook(Heat, "Not counting yet", "Your phone didn't start it. Tap below to fix it.", false)
        !permissions.overlay ->
            StatusLook(Heat, "The pill is hidden", "Counting works. Allow overlay to see it float.", true)
        !permissions.keepAliveDone ->
            StatusLook(Heat, "Your phone may stop it", "Counting now, but battery saver can kill it.", true)
        else -> when (state) {
            "InReels" -> StatusLook(
                Good, "Counting",
                "You're watching ${current?.items ?: AppWords.unit(apps)} right now.", true,
            )
            "InApp" -> StatusLook(
                Good, "Ready",
                if (current != null) "${current.appName}'s open — swipe into ${current.items}."
                else "Open — swipe into ${AppWords.unit(apps)}.",
                true,
            )
            else -> StatusLook(Good, "Standing by", "Waiting for you to open $names.", false)
        }
    }
}

@Composable
private fun StatusCard(
    permissions: PermissionState,
    connected: Boolean,
    state: String,
    apps: Set<TrackedApp>,
    lastPackage: String?,
    onOpenWizard: () -> Unit,
    onOpenSetupTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = statusLook(permissions, connected, state, apps, lastPackage)
    Card(modifier) {
        AnimatedContent(
            targetState = look,
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.quick()) },
            contentKey = { it.title },
            label = "status",
        ) { current ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveBadge(color = current.color, live = current.live)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Chalk,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = current.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Smoke,
                    )
                }
            }
        }

        when {
            !permissions.allGranted -> {
                Spacer(Modifier.height(16.dp))
                FlatButton(
                    text = if (permissions.notRunning) "Fix it" else "Finish setup",
                    emphasised = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenWizard,
                )
            }
            !permissions.keepAliveDone -> {
                Spacer(Modifier.height(14.dp))
                FlatButton(
                    text = "Keep it running reliably",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSetupTab,
                )
            }
        }
    }
}

/** A tinted badge holding a dot that softly pulses while counting is live. */
@Composable
private fun LiveBadge(color: Color, live: Boolean) {
    val tint by animateColorAsState(color, Motion.standard(), label = "badge")
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        PulseDot(color = tint, active = live, size = 10.dp)
    }
}

/**
 * Today's split when both apps were used: each side's badge, count and share,
 * over one thin two-colour bar that eases as the numbers move.
 */
@Composable
private fun SplitBar(reels: Int, shorts: Int, modifier: Modifier = Modifier) {
    val total = (reels + shorts).coerceAtLeast(1)
    val share by animateFloatAsState(reels.toFloat() / total, Motion.emphasised(), label = "split")
    val reelsPct = (reels * 100f / total).roundToInt()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SplitSide(TrackedApp.Instagram, reels, reelsPct, alignEnd = false)
            Spacer(Modifier.weight(1f))
            SplitSide(TrackedApp.YouTube, shorts, 100 - reelsPct, alignEnd = true)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                Modifier
                    .weight(share.coerceIn(0.04f, 0.96f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(TrackedApp.Instagram.mark),
            )
            Box(
                Modifier
                    .weight((1f - share).coerceIn(0.04f, 0.96f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(TrackedApp.YouTube.mark),
            )
        }
    }
}

@Composable
private fun SplitSide(app: TrackedApp, count: Int, percent: Int, alignEnd: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!alignEnd) {
            AppBadge(app, size = 32.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = Chalk)
            Text(
                text = "${if (count == 1) app.item else app.items} · $percent%",
                style = MaterialTheme.typography.bodySmall,
                color = Smoke,
                maxLines = 1,
            )
        }
        if (alignEnd) {
            Spacer(Modifier.width(10.dp))
            AppBadge(app, size = 32.dp)
        }
    }
}

/** The one-time question for people who installed before Shorts existed. */
@Composable
private fun ShortsPrompt(onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBadge(TrackedApp.YouTube, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("YouTube Shorts", style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Spacer(Modifier.width(8.dp))
                    StatusChip("New", ChipTone.Accent)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Count your Shorts too, in the same number. Switch it off any time in Setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton("Not now", quiet = true, onClick = onNo, modifier = Modifier.weight(1f))
            FlatButton("Count Shorts", emphasised = true, onClick = onYes, modifier = Modifier.weight(1f))
        }
    }
}
