package com.ekaur.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.di.AppContainer
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.share.CardStats
import com.ekaur.android.ui.account.AccountScreen
import com.ekaur.android.ui.common.pressScale
import com.ekaur.android.ui.common.rememberHaptics
import com.ekaur.android.ui.debug.DiagnosticsScreen
import com.ekaur.android.ui.debug.EventInspectorScreen
import com.ekaur.android.ui.friends.FriendsScreen
import com.ekaur.android.ui.friends.UsernameScreen
import com.ekaur.android.ui.home.HomeScreen
import com.ekaur.android.ui.onboarding.PermissionState
import com.ekaur.android.ui.onboarding.SetupScreen
import com.ekaur.android.ui.onboarding.SetupGuide
import com.ekaur.android.ui.onboarding.SetupWizard
import com.ekaur.android.ui.share.ShareScreen
import com.ekaur.android.ui.stats.StatsScreen
import com.ekaur.android.ui.theme.AppLook
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Looks
import com.ekaur.android.ui.theme.Palette
import com.ekaur.android.ui.theme.Canvas
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.EkAurTheme
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.buttonGradient
import com.ekaur.android.ui.update.UpdatePopup
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Tab(val label: String, val icon: Int) {
    Home("Home", R.drawable.ic_nav_home),
    Stats("Stats", R.drawable.ic_nav_stats),
    Ranks("Ranks", R.drawable.ic_nav_ranks),
    Setup("Setup", R.drawable.ic_nav_setup),
}

private val BOTTOM_TABS = Tab.entries.toList()

/** A full screen shown over the tabs. The tabs stay alive underneath it. */
private sealed interface Overlay {
    data class Share(val stats: CardStats, val avatar: android.graphics.Bitmap?) : Overlay
    data object Account : Overlay
    data object Events : Overlay
    data object Status : Overlay
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as EkAurApp).container

        // The right look from the very first frame, not a flash of the default.
        Looks.palette = Palette.of(lookOf(container))

        setContent {
            val apps by container.settings.countedApps.collectAsState()
            val override by container.settings.lookOverride.collectAsState()
            EkAurTheme(look = Looks.lookFor(apps, override?.let(::lookNamed))) {
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

private fun lookNamed(name: String): AppLook? = AppLook.entries.firstOrNull { it.name == name }

private fun lookOf(container: AppContainer): AppLook = Looks.lookFor(
    container.settings.countedApps.value,
    container.settings.lookOverride.value?.let(::lookNamed),
)

@Composable
private fun AppScaffold(container: AppContainer) {
    val context = LocalContext.current
    val username by container.settings.username.collectAsState()

    // The one gate: everyone is on one leaderboard, so a name is the whole
    // sign-up, and nothing else is reachable until there is one.
    if (username == null) {
        UsernameScreen(container, Modifier.systemBarsPadding())
        return
    }

    fun readPermissions() = PermissionState.read(context, container.settings.autostartConfirmed)
    var permissions by remember { mutableStateOf(readPermissions()) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val pagerState = rememberPagerState { BOTTOM_TABS.size }
    val scope = rememberCoroutineScope()

    // Every grant, read in one go whenever the app comes forward -- the one
    // source of truth for Home, Setup and the wizard, so they never disagree.
    LifecycleResumeEffect(Unit) {
        permissions = readPermissions()
        // Back in the app: the floating guide over Settings has done its job.
        SetupGuide.stop()
        onPauseOrDispose { }
    }
    // The service starting (or stopping) changes what every screen should
    // say, and can happen while the app is open -- re-read when it does.
    val serviceUp by container.serviceStatus.connected.collectAsState()
    LaunchedEffect(serviceUp) {
        permissions = readPermissions()
        // The system's bound list can lag the connect by a moment.
        if (serviceUp && !permissions.running) {
            kotlinx.coroutines.delay(700)
            permissions = readPermissions()
        }
    }

    // The guided setup fronts the app until accessibility is on or it has been
    // skipped once; after that it is one tap away from Home and Setup.
    var wizardSeen by remember { mutableStateOf(container.settings.setupWizardSeen) }
    var wizardOpen by remember { mutableStateOf(false) }
    var wizardRecovery by remember { mutableStateOf(false) }
    if (wizardOpen || (!permissions.allGranted && !wizardSeen)) {
        val apps by container.settings.countedApps.collectAsState()
        // A first run starts with whatever the phone has installed ticked, so
        // most people just press on.
        LaunchedEffect(Unit) {
            if (!container.settings.appsChosen) {
                val installed = TrackedApp.entries.filter { ServiceControl.isInstalled(context, it) }.toSet()
                container.settings.setCountedApps(installed.ifEmpty { setOf(TrackedApp.Instagram) })
            }
        }
        val close = {
            container.settings.setupWizardSeen = true
            wizardSeen = true
            wizardOpen = false
            wizardRecovery = false
        }
        // Back leaves the wizard: on first run that counts as "skip for now",
        // opened by hand it returns to where it was opened from.
        BackHandler(onBack = close)
        SetupWizard(
            permissions = permissions,
            onAutostartVisited = {
                container.settings.autostartConfirmed = true
                permissions = readPermissions()
            },
            onReturnAfterConnect = { container.settings.returnAfterConnectAtMs = System.currentTimeMillis() },
            startInRecovery = wizardRecovery,
            onDone = close,
            onSkip = close,
            onClose = if (wizardOpen) close else null,
            apps = apps,
            onChooseApps = { chosen ->
                container.settings.setCountedApps(chosen)
                container.settings.appsChosen = true
                container.settings.shortsAsked = true
            },
            modifier = Modifier.systemBarsPadding(),
        )
        return
    }

    // Look for a newer build on GitHub once the app is open (throttled inside).
    LaunchedEffect(Unit) { container.updateManager.checkOnLaunch() }

    UpdatePopup(container.updateManager)

    fun goToTab(index: Int) {
        overlay = null
        scope.launch {
            // A brisk, fixed-duration glide rather than the default spring,
            // so a far jump (Home -> Setup) still lands fast and deliberate
            // instead of drifting through the middle tabs.
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            )
        }
    }

    // System back closes a full screen first; from a tab other than Home it
    // returns Home; only from Home does it leave the app.
    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = overlay == null && pagerState.currentPage != Tab.Home.ordinal) {
        goToTab(Tab.Home.ordinal)
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            // All four tabs stay composed, so every tab keeps its scroll and
            // state however far you jump -- no repaint hitch, no refetch.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = BOTTOM_TABS.size - 1,
            ) { page ->
                when (BOTTOM_TABS[page]) {
                    Tab.Home -> HomeScreen(
                        container = container,
                        permissions = permissions,
                        onOpenWizard = { wizardOpen = true },
                        onOpenSetupTab = { goToTab(Tab.Setup.ordinal) },
                        onOpenAccount = { overlay = Overlay.Account },
                        onShare = { stats, avatar -> overlay = Overlay.Share(stats, avatar) },
                    )
                    Tab.Stats -> {
                        val apps by container.settings.countedApps.collectAsState()
                        StatsScreen(container.counterRepository, apps = apps)
                    }
                    Tab.Ranks -> FriendsScreen(
                        container = container,
                        onOpenAccount = { overlay = Overlay.Account },
                    )
                    Tab.Setup -> SetupScreen(
                        container = container,
                        permissions = permissions,
                        onGuidedSetup = { recovery ->
                            wizardRecovery = recovery
                            wizardOpen = true
                        },
                        onOpenEvents = { overlay = Overlay.Events },
                        onOpenStatus = { overlay = Overlay.Status },
                    )
                }
            }

            // Full screens slide up over the tabs rather than snapping in, and
            // slide away again on close or back.
            AnimatedContent(
                targetState = overlay,
                transitionSpec = {
                    (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 8 } +
                        fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(240)) { it / 10 } + fadeOut(tween(180)))
                },
                contentKey = { it?.javaClass },
                label = "overlay",
            ) { current ->
                if (current != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Canvas),
                    ) {
                        when (current) {
                            is Overlay.Share -> ShareScreen(
                                stats = current.stats,
                                avatar = current.avatar,
                                onClose = { overlay = null },
                            )
                            Overlay.Account -> AccountScreen(container, onClose = { overlay = null })
                            Overlay.Events -> EventInspectorScreen(container.eventLog)
                            Overlay.Status -> DiagnosticsScreen(
                                status = container.serviceStatus,
                                eventLog = container.eventLog,
                                crashReporter = container.crashReporter,
                                serviceEnabled = permissions.service,
                                countedApps = container.settings.countedApps.collectAsState().value,
                                container = container,
                            )
                        }
                    }
                }
            }
        }

        BottomBar(pagerState = pagerState, onSelect = ::goToTab)
    }
}

/**
 * The floating tab bar.
 *
 * The highlight follows the pager's live scroll position, so it glides as you
 * swipe. That position is only ever read inside draw-layer lambdas, never in
 * composition, so a swipe animates the bar without recomposing it each frame.
 * The active and inactive looks are two stacked layers cross-faded by that
 * same position, which keeps the label width fixed -- no mid-swipe jump.
 */
@Composable
private fun BottomBar(pagerState: PagerState, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
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
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BOTTOM_TABS.forEachIndexed { index, entry ->
                    // 1 on the active item, fading to 0 as the swipe moves away.
                    // A lambda, so it is evaluated in the draw phase only.
                    val t = {
                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        (1f - abs(position - index)).coerceIn(0f, 1f)
                    }
                    val interaction = remember { MutableInteractionSource() }

                    Column(
                        Modifier
                            .weight(1f)
                            .semantics {
                                role = Role.Tab
                                selected = pagerState.currentPage == index
                            }
                            .pressScale(interaction, 0.86f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(interactionSource = interaction, indication = null) {
                                if (pagerState.currentPage != index) haptics.tick()
                                onSelect(index)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(width = 50.dp, height = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // The gradient pill grows in as the tab becomes active.
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        val v = t()
                                        alpha = v
                                        val s = 0.7f + 0.3f * v
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .clip(RoundedCornerShape(50))
                                    .background(brush = buttonGradient()),
                            )
                            // Two icon layers: grey fading out, white fading in.
                            Icon(
                                painter = painterResource(entry.icon),
                                contentDescription = null,
                                tint = Ash,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer { alpha = 1f - t() },
                            )
                            Icon(
                                painter = painterResource(entry.icon),
                                contentDescription = null,
                                tint = Ink,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        val v = t()
                                        alpha = v
                                        // A gentle lift on the active icon.
                                        val s = 1f + 0.08f * v
                                        scaleX = s
                                        scaleY = s
                                        translationY = -1.5f * v
                                    },
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Smoke,
                                modifier = Modifier.graphicsLayer { alpha = 1f - t() },
                            )
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Chalk,
                                modifier = Modifier.graphicsLayer { alpha = t() },
                            )
                        }
                    }
                }
            }
        }
    }
}
