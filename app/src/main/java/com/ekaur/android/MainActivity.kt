package com.ekaur.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.SurfaceLav
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.ekaur.android.di.AppContainer
import com.ekaur.android.share.CardStats
import com.ekaur.android.share.ShareCardBuilder
import com.ekaur.android.ui.share.ShareScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.sync.Avatar
import com.ekaur.android.ui.account.AccountScreen
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.GradientNumber
import com.ekaur.android.ui.common.StatTile
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.debug.DiagnosticsScreen
import com.ekaur.android.ui.friends.FriendsScreen
import com.ekaur.android.ui.friends.UsernameScreen
import com.ekaur.android.ui.debug.EventInspectorScreen
import com.ekaur.android.ui.onboarding.SetupScreen
import com.ekaur.android.ui.stats.StatsScreen
import com.ekaur.android.ui.stats.formatDuration
import com.ekaur.android.ui.update.UpdatePopup
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.EkAurTheme
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.InkLine
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.instaGradient

private enum class Tab(val label: String, val icon: Int) {
    Home("Home", R.drawable.ic_nav_home),
    Stats("Stats", R.drawable.ic_nav_stats),
    Ranks("Ranks", R.drawable.ic_nav_ranks),
    Setup("Setup", R.drawable.ic_nav_setup),
}

private val BOTTOM_TABS = Tab.entries.toList()

/** The developer-only screens, shown as a full overlay above the tabs. */
private enum class DevScreen { Events, Status }

/** The three grants the app needs, re-read whenever the screen comes forward. */
private data class Permissions(
    val service: Boolean = false,
    val overlay: Boolean = false,
    val battery: Boolean = false,
) {
    val allGranted: Boolean get() = service && overlay && battery
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as EkAurApp).container

        setContent {
            EkAurTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppScaffold(container)
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(container: AppContainer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val username by container.settings.username.collectAsState()

    // The one gate: everyone is on one leaderboard, so a name is the whole
    // sign-up, and nothing else is reachable until there is one.
    if (username == null) {
        UsernameScreen(container, Modifier.systemBarsPadding())
        return
    }

    var permissions by remember { mutableStateOf(Permissions()) }
    var sharing by remember { mutableStateOf<CardStats?>(null) }
    var cardAvatar by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var dev by remember { mutableStateOf<DevScreen?>(null) }
    var account by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState { BOTTOM_TABS.size }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        permissions = Permissions(
            service = ServiceControl.isAccessibilityServiceEnabled(context),
            overlay = ServiceControl.canDrawOverlay(context),
            battery = ServiceControl.isIgnoringBatteryOptimisations(context),
        )
        onPauseOrDispose { }
    }

    // Look for a newer build on GitHub once the app is open (throttled inside).
    androidx.compose.runtime.LaunchedEffect(Unit) { container.updateManager.checkOnLaunch() }

    UpdatePopup(container.updateManager)

    val card = sharing
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            when {
                // The share card and the dev screens replace the tab area but keep
                // the bar below, so tapping any tab returns. The nav no longer
                // disappears on the share screen the way an early return made it.
                card != null -> ShareScreen(
                    stats = card,
                    avatar = cardAvatar,
                    onClose = { sharing = null },
                )
                account -> AccountScreen(container, onClose = { account = false })
                dev == DevScreen.Events -> EventInspectorScreen(container.eventLog)
                dev == DevScreen.Status -> DiagnosticsScreen(
                    status = container.serviceStatus,
                    eventLog = container.eventLog,
                    crashReporter = container.crashReporter,
                    serviceEnabled = permissions.service,
                )
                // The four main tabs live in a pager so switching slides natively
                // and each screen stays composed -- no repaint hitch, no refetch.
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (BOTTOM_TABS[page]) {
                        Tab.Home -> HomeScreen(
                            container = container,
                            permissions = permissions,
                            onOpenSetup = {
                                scope.launch { pagerState.animateScrollToPage(Tab.Setup.ordinal) }
                            },
                            onOpenAccount = { account = true },
                            onShare = { stats, avatar ->
                                cardAvatar = avatar
                                sharing = stats
                            },
                        )
                        Tab.Stats -> StatsScreen(container.counterRepository)
                        Tab.Ranks -> FriendsScreen(container)
                        Tab.Setup -> SetupScreen(
                            container = container,
                            serviceEnabled = permissions.service,
                            onOpenEvents = { dev = DevScreen.Events },
                            onOpenStatus = { dev = DevScreen.Status },
                        )
                    }
                }
            }
        }

        BottomBar(
            pagerState = pagerState,
            onSelect = { index ->
                dev = null
                sharing = null
                account = false
                scope.launch {
                    // A brisk, fixed-duration glide rather than the default spring,
                    // so a far jump (Home -> Setup) still lands fast and deliberate
                    // instead of drifting through the middle tabs.
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    )
                }
            },
        )
    }
}

@Composable
private fun BottomBar(pagerState: PagerState, onSelect: (Int) -> Unit) {
    // The live scroll position, so the gradient highlight glides between items as
    // you swipe or tap instead of snapping -- the "not laggy" feel.
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val haptic = LocalHapticFeedback.current
    Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 16.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BOTTOM_TABS.forEachIndexed { index, entry ->
                    // 1 on the active item, fading to 0 as the swipe moves away.
                    val t = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)

                    // A quick squish on touch so every tap feels answered.
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.86f else 1f,
                        animationSpec = tween(120, easing = FastOutSlowInEasing),
                        label = "press",
                    )

                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(index)
                            }
                            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(width = 46.dp, height = 30.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // The gradient pill grows in as the tab becomes active.
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        alpha = t
                                        val s = 0.7f + 0.3f * t
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .clip(RoundedCornerShape(50))
                                    .background(brush = instaGradient()),
                            )
                            Icon(
                                painter = painterResource(entry.icon),
                                contentDescription = entry.label,
                                tint = lerp(Ash, Ink, t),
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        // A gentle lift on the active icon.
                                        val s = 1f + 0.10f * t
                                        scaleX = s
                                        scaleY = s
                                        translationY = -2f * t
                                    },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = lerp(Smoke, Chalk, t),
                            fontWeight = if (t > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    container: AppContainer,
    permissions: Permissions,
    onOpenSetup: () -> Unit,
    onOpenAccount: () -> Unit,
    onShare: (CardStats, android.graphics.Bitmap?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var building by remember { mutableStateOf(false) }
    val count by container.counterRepository.observeTodayCount().collectAsState(initial = 0)
    val activeMs by container.counterRepository.observeTodayActiveMs().collectAsState(initial = 0L)
    val connected by container.serviceStatus.connected.collectAsState()
    val state by container.serviceStatus.detectorState.collectAsState()
    val username by container.settings.username.collectAsState()

    // A small week + best summary under the hero, so the home screen reads as a
    // dashboard rather than one lonely number on a lot of empty space.
    val dates = remember { container.counterRepository.lastDays(7) }
    val dayRows by remember(dates) { container.counterRepository.observeDaysSince(dates.first()) }
        .collectAsState(initial = emptyList())
    val weekTotal = com.ekaur.android.ui.stats.dailySeries(dayRows, dates).sumOf { it.reels }
    val best by remember { container.counterRepository.observeBestDay() }.collectAsState(initial = null)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))

        // Header: app name on the left, the account avatar on the right.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "EK AUR",
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = com.ekaur.android.ui.theme.Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            letterSpacing = 4.sp,
                            brush = instaGradient(),
                        ),
                    )
                    Spacer(Modifier.size(6.dp))
                    // A small superscript "beta" tag at the top-right of the mark.
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(color = SurfaceLav)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "BETA",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = com.ekaur.android.ui.theme.Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 1.5.sp,
                                brush = instaGradient(),
                            ),
                        )
                    }
                }
                Text(
                    text = "one more",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                    letterSpacing = 2.sp,
                )
            }
            Box(
                Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onOpenAccount),
            ) {
                UserAvatar(
                    username = username.orEmpty(),
                    url = Avatar.urlFor(
                        baseUrl = container.supabase.baseUrl,
                        userId = container.settings.userId.orEmpty(),
                        version = container.settings.avatarVersion,
                    ),
                    size = 44.dp,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // The hero number and its label together inside a soft glow.
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(260.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(SurfaceLav, Color.Transparent),
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GradientNumber(
                    text = count.toString(),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text("Reels today", style = MaterialTheme.typography.bodyLarge, color = Smoke)
                if (activeMs > 0) {
                    Text(
                        text = formatDuration(activeMs) + " watched",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ash,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // A compact summary row.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(label = "Today", value = count.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "7 days", value = weekTotal.toString(), modifier = Modifier.weight(1f))
            StatTile(
                label = "Best day",
                value = best?.total?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))

        FlatButton(
            text = if (building) "Making…" else "Share card",
            emphasised = count > 0 && !building,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (count <= 0 || building) return@FlatButton
                building = true
                scope.launch {
                    val ready = withContext(Dispatchers.IO) {
                        ShareCardBuilder.gather(container, context)
                    }
                    building = false
                    if (ready != null) onShare(ready.first, ready.second)
                }
            },
        )

        Spacer(Modifier.height(14.dp))

        StatusCard(
            permissions = permissions,
            connected = connected,
            state = state,
            onOpenSetup = onOpenSetup,
        )

        Spacer(Modifier.height(16.dp))
    }
}

/** The look of the counting-status card: a colour, a headline, and a plain line. */
private data class StatusLook(
    val color: Color,
    val title: String,
    val detail: String,
    val live: Boolean,
)

private fun statusLook(permissions: Permissions, connected: Boolean, state: String): StatusLook =
    when {
        !permissions.service ->
            StatusLook(Heat, "Counting is off", "Turn on accessibility to start counting.", false)
        !connected ->
            StatusLook(Heat, "Not connected yet", "It's on — open Instagram to wake it up.", false)
        !permissions.overlay ->
            StatusLook(Heat, "The pill is hidden", "Counting works. Allow overlay to see it float.", true)
        !permissions.battery ->
            StatusLook(Heat, "Battery may stop it", "Counting now, but battery saver can kill it.", true)
        else -> when (state) {
            "InReels" -> StatusLook(Good, "Counting", "You're watching reels right now.", true)
            "InApp" -> StatusLook(Good, "Ready", "Instagram's open — swipe into reels.", true)
            else -> StatusLook(Good, "Standing by", "Waiting for you to open Instagram.", false)
        }
    }

@Composable
private fun StatusCard(
    permissions: Permissions,
    connected: Boolean,
    state: String,
    onOpenSetup: () -> Unit,
) {
    val look = statusLook(permissions, connected, state)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveBadge(color = look.color, live = look.live)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = look.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Chalk,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = look.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
            }
        }

        if (!permissions.allGranted) {
            Spacer(Modifier.height(16.dp))
            FlatButton(
                text = "Finish setup",
                emphasised = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSetup,
            )
        }
    }
}

/** A tinted badge holding a dot that softly pulses while counting is live. */
@Composable
private fun LiveBadge(color: Color, live: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (live) {
            // An expanding, fading ring — a heartbeat behind the dot.
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        val s = 1f + ring * 1.4f
                        scaleX = s
                        scaleY = s
                        alpha = (1f - ring) * 0.5f
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
