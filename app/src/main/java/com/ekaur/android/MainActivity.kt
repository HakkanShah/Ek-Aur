package com.ekaur.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Dot
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.GradientNumber
import com.ekaur.android.ui.debug.DiagnosticsScreen
import com.ekaur.android.ui.friends.FriendsScreen
import com.ekaur.android.ui.friends.UsernameScreen
import com.ekaur.android.ui.debug.EventInspectorScreen
import com.ekaur.android.ui.onboarding.SetupScreen
import com.ekaur.android.ui.stats.StatsScreen
import com.ekaur.android.ui.stats.formatDuration
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
    Events("Events", R.drawable.ic_nav_setup),
    Status("Status", R.drawable.ic_nav_setup),
}

private val BOTTOM_TABS = listOf(Tab.Home, Tab.Stats, Tab.Ranks, Tab.Setup)

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
    var tab by remember { mutableStateOf(Tab.Home) }
    var sharing by remember { mutableStateOf<CardStats?>(null) }
    var cardAvatar by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LifecycleResumeEffect(Unit) {
        permissions = Permissions(
            service = ServiceControl.isAccessibilityServiceEnabled(context),
            overlay = ServiceControl.canDrawOverlay(context),
            battery = ServiceControl.isIgnoringBatteryOptimisations(context),
        )
        onPauseOrDispose { }
    }

    val card = sharing
    if (card != null) {
        ShareScreen(
            stats = card,
            avatar = cardAvatar,
            onClose = { sharing = null },
            modifier = Modifier.systemBarsPadding(),
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Crossfade(
            targetState = tab,
            animationSpec = tween(230),
            modifier = Modifier.weight(1f),
            label = "Tab",
        ) { shown ->
            when (shown) {
                Tab.Home -> HomeScreen(
                    container = container,
                    permissions = permissions,
                    onOpenSetup = { tab = Tab.Setup },
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
                    onOpenEvents = { tab = Tab.Events },
                    onOpenStatus = { tab = Tab.Status },
                )
                Tab.Events -> EventInspectorScreen(container.eventLog)
                Tab.Status -> DiagnosticsScreen(
                    status = container.serviceStatus,
                    eventLog = container.eventLog,
                    crashReporter = container.crashReporter,
                    serviceEnabled = permissions.service,
                )
            }
        }

        BottomBar(current = tab, onSelect = { tab = it })
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    // A dev screen keeps Setup lit, so the bar always shows one active item.
    val active = if (current in BOTTOM_TABS) current else Tab.Setup
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
            BOTTOM_TABS.forEach { entry ->
                val selected = entry == active
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(entry) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(width = 46.dp, height = 30.dp)
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (selected) Modifier.background(brush = instaGradient())
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(entry.icon),
                            contentDescription = entry.label,
                            tint = if (selected) Ink else Ash,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) Chalk else Smoke,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
    onShare: (CardStats, android.graphics.Bitmap?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var building by remember { mutableStateOf(false) }
    val count by container.counterRepository.observeTodayCount().collectAsState(initial = 0)
    val activeMs by container.counterRepository.observeTodayActiveMs().collectAsState(initial = 0L)
    val connected by container.serviceStatus.connected.collectAsState()
    val state by container.serviceStatus.detectorState.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(44.dp))

        Text(
            text = "EK AUR",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = com.ekaur.android.ui.theme.Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = 6.sp,
                brush = instaGradient(),
            ),
        )

        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            // A soft gradient halo behind the number, so the hero figure glows
            // off the pale canvas instead of just sitting on it.
            Box(
                Modifier
                    .size(300.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(SurfaceLav, Color.Transparent),
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    )
            )
            GradientNumber(
                text = count.toString(),
                style = MaterialTheme.typography.displayLarge,
            )
        }
        Text("Reels today", style = MaterialTheme.typography.bodyLarge, color = Smoke)

        if (activeMs > 0) {
            Text(
                text = formatDuration(activeMs) + " watched",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
        }

        Spacer(Modifier.height(22.dp))

        FlatButton(
            text = if (building) "making..." else "share card",
            emphasised = count > 0 && !building,
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

        Spacer(Modifier.height(22.dp))

        Card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Dot(if (permissions.allGranted && connected) Good else Heat)
                Text(
                    text = when {
                        !permissions.service -> "Counting is off"
                        !connected -> "On, but not connected yet"
                        !permissions.overlay -> "Counting, but the pill is hidden"
                        !permissions.battery -> "On, but the battery may kill it"
                        else -> "Counting  ·  $state"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Chalk,
                )
            }

            if (!permissions.allGranted) {
                Spacer(Modifier.height(14.dp))
                FlatButton(text = "Finish setup", emphasised = true, onClick = onOpenSetup)
            }
        }
    }
}
