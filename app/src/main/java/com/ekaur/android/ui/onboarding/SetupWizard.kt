package com.ekaur.android.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.setup.OemHint
import com.ekaur.android.setup.OemHints
import com.ekaur.android.setup.RestrictedSetting
import com.ekaur.android.setup.SetupFlow
import com.ekaur.android.setup.SetupStep
import com.ekaur.android.setup.Verdict
import com.ekaur.android.ui.common.AppBadge
import com.ekaur.android.ui.common.AppWords
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Celebration
import com.ekaur.android.ui.common.EkIcon
import com.ekaur.android.ui.common.EkIcons
import com.ekaur.android.ui.common.Expandable
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.IconTile
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.PulseDot
import com.ekaur.android.ui.common.RoundIconButton
import com.ekaur.android.ui.common.mark
import com.ekaur.android.ui.common.markSoft
import com.ekaur.android.ui.common.pressScale
import com.ekaur.android.ui.common.rememberHaptics
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.InkLine
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.buttonGradient
import com.ekaur.android.ui.theme.instaGradient
import kotlinx.coroutines.delay

/**
 * The guided setup: one screen per switch, and a way through Android's
 * "Restricted setting" wall.
 *
 * People were installing the app, hitting that wall on the accessibility
 * switch, and deleting it -- the unblock lives in a menu that only exists
 * after the switch has been tapped once, on a page nobody thinks to open. So
 * this walks it: tells them the wall is coming and that it's normal, gives a
 * button for every hop, knows where the menu is on their brand of phone, and
 * notices each grant the moment they come back so the next step slides in on
 * its own.
 *
 * The step is never stored. It is derived from [permissions] on every pass
 * ([SetupFlow.nextStep]), so a switch flipped in Settings moves the flow
 * forward with nothing to fall out of sync. The only state kept is whether
 * the welcome has been pressed through and which recovery hops have been
 * pressed, saved across the process death MIUI and ColorOS inflict on a
 * backgrounded activity.
 */
@Composable
fun SetupWizard(
    permissions: PermissionState,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    startInRecovery: Boolean = false,
    onClose: (() -> Unit)? = null,
    apps: Set<TrackedApp> = setOf(TrackedApp.Instagram),
    onChooseApps: (Set<TrackedApp>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var welcomed by rememberSaveable { mutableStateOf(startInRecovery) }
    val step = SetupFlow.nextStep(welcomed, permissions.service, permissions.overlay)

    // Read once per showing: cheap, and neither changes while the wizard is up
    // except by the user going through Settings, which brings a resume.
    val hint = remember { ServiceControl.oemHint() }
    val verdict = remember(permissions.service) { ServiceControl.restrictedVerdict(context) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SETUP",
                style = MaterialTheme.typography.labelLarge,
                color = Smoke,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            ProgressDots(
                done = SetupFlow.requiredDone(permissions.service, permissions.overlay),
                current = step,
            )
            if (onClose != null) {
                Spacer(Modifier.width(14.dp))
                RoundIconButton(icon = EkIcons.Close, description = "Close setup", onClick = onClose)
            }
        }
        Spacer(Modifier.height(18.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { dir * it / 5 } +
                    fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { -dir * it / 5 } + fadeOut(tween(160)))
            },
            label = "setup-step",
        ) { current ->
            when (current) {
                SetupStep.Welcome -> WelcomeStep(
                    apps = apps,
                    onToggle = { app ->
                        val next = if (app in apps) apps - app else apps + app
                        if (next.isNotEmpty()) onChooseApps(next)
                    },
                    onStart = {
                        onChooseApps(apps)
                        welcomed = true
                    },
                    onSkip = onSkip,
                )
                SetupStep.Accessibility -> AccessibilityStep(
                    hint = hint,
                    verdict = verdict,
                    startInRecovery = startInRecovery,
                    apps = apps,
                    onSkip = onSkip,
                )
                SetupStep.Overlay -> OverlayStep(apps = apps, onSkip = onSkip)
                SetupStep.Done -> DoneStep(apps = apps, onDone = onDone)
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// Steps
// ---------------------------------------------------------------------------

@Composable
private fun WelcomeStep(
    apps: Set<TrackedApp>,
    onToggle: (TrackedApp) -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    Column {
        Headline("What do you scroll?")
        Spacer(Modifier.height(8.dp))
        Lead("Pick one or both. The app counts them and dresses to match.")
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrackedApp.entries.forEach { app ->
                AppChoice(
                    app = app,
                    selected = app in apps,
                    installed = remember(app) { ServiceControl.isInstalled(context, app) },
                    onClick = { onToggle(app) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Headline("Then two switches and you're counting.")
        Spacer(Modifier.height(8.dp))
        Lead("About a minute. Android will send you to Settings twice; this screen tells you exactly what to tap.")
        Spacer(Modifier.height(18.dp))

        Card {
            PreviewRow(1, "Accessibility", "How ${AppWords.unit(apps).lowercase()} get counted.")
            Spacer(Modifier.height(14.dp))
            PreviewRow(2, "Overlay", "So the counter can float over ${AppWords.appNames(apps)}.")
            Spacer(Modifier.height(14.dp))
            PreviewRow(3, "Done", "Open ${AppWords.appNames(apps)} and scroll.")
        }

        Spacer(Modifier.height(14.dp))
        Callout(
            title = "Heads-up",
            body = "Android will say “Restricted setting” once when you flip the first switch. " +
                "That's normal for any app not from the Play Store, and it takes three taps to clear. " +
                "We'll walk you through it.",
        )

        Spacer(Modifier.height(24.dp))
        FlatButton("Let's go", emphasised = true, modifier = Modifier.fillMaxWidth(), onClick = onStart)
        Spacer(Modifier.height(12.dp))
        TextLink("Skip for now", onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun AccessibilityStep(
    hint: OemHint,
    verdict: Verdict,
    startInRecovery: Boolean,
    apps: Set<TrackedApp>,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    // Pressed "Open Accessibility" at least once. Coming back without the
    // grant after that is the signal the wall was hit, so the recovery shows
    // itself rather than waiting to be found.
    var attempted by rememberSaveable { mutableStateOf(startInRecovery) }
    var recoveryOpen by rememberSaveable { mutableStateOf(startInRecovery) }
    val gateApplies = RestrictedSetting.applies(verdict)

    // Once the switch has been tried and the app is back without the grant,
    // the wall is the likeliest reason, so the recovery steps take the card
    // over by themselves (only where the gate exists). A return *with* the
    // grant never reaches here -- the step has already moved on.
    val showRecovery = recoveryOpen || (gateApplies && attempted)

    // Back steps out of the recovery steps first, rather than out of setup.
    BackHandler(enabled = showRecovery && !startInRecovery) {
        recoveryOpen = false
        attempted = false
    }

    Column {
        Headline("Turn on Ek Aur in Accessibility")
        Spacer(Modifier.height(8.dp))
        Lead("Counting works through an accessibility switch. It sees the swipe to the next video in ${AppWords.appNames(apps).replace(" or ", " and ")} and nothing else.")
        Spacer(Modifier.height(22.dp))

        if (!showRecovery) {
            Card {
                SettingsRowMock()
                Spacer(Modifier.height(14.dp))
                Body("Look under “${hint.listSection}”, tap Ek Aur, then turn the switch on.")
                Spacer(Modifier.height(16.dp))
                FlatButton(
                    text = "Open Accessibility",
                    emphasised = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        attempted = true
                        ServiceControl.openAccessibilityServiceDetails(context)
                    },
                )
            }
            if (gateApplies) {
                Spacer(Modifier.height(14.dp))
                TextLink(
                    text = "Saw “Restricted setting”?",
                    onClick = { recoveryOpen = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        } else {
            RecoveryCard(
                hint = hint,
                verdict = verdict,
                onOpenAccessibility = {
                    attempted = true
                    ServiceControl.openAccessibilityServiceDetails(context)
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        Waiting("Come back here once it's on — this moves on by itself.")
        Spacer(Modifier.height(10.dp))
        TextLink("Skip for now", onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/**
 * The three hops through the "Restricted setting" wall, each with its own
 * button, and a way out when the menu still isn't there.
 */
@Composable
private fun RecoveryCard(
    hint: OemHint,
    verdict: Verdict,
    onOpenAccessibility: () -> Unit,
) {
    val context = LocalContext.current
    var tapped by rememberSaveable { mutableStateOf(false) }
    var allowed by rememberSaveable { mutableStateOf(false) }

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(EkIcons.Lock, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "“Restricted setting”: the way through",
                style = MaterialTheme.typography.titleMedium,
                color = Chalk,
            )
        }
        Spacer(Modifier.height(6.dp))
        Body(
            "Android blocks this switch for apps not from the Play Store. " +
                "It's expected, and it clears in three taps.",
        )

        if (verdict == Verdict.Cleared) {
            Spacer(Modifier.height(16.dp))
            Callout(
                title = "Already allowed",
                body = "Your phone has already allowed restricted settings for Ek Aur. " +
                    "Go back to Accessibility and flip the switch.",
            )
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = "Open Accessibility",
                emphasised = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenAccessibility,
            )
            return@Card
        }

        Spacer(Modifier.height(18.dp))

        // An accordion: only the step you're on is open; finished ones fold
        // into a checked line (tap one to reopen it). Half the reading.
        val current = when {
            !tapped -> 1
            !allowed -> 2
            else -> 3
        }
        var focus by rememberSaveable { mutableStateOf<Int?>(null) }
        val open = focus ?: current

        RecoveryHop(
            n = 1,
            expanded = open == 1,
            onHeader = { focus = 1 },
            done = tapped,
            title = "Tap the switch once, press OK on the popup",
            detail = "It won't turn on yet — that tap is what unlocks the menu in step 2.",
            action = "Open Accessibility",
            onAction = {
                tapped = true
                focus = null
                onOpenAccessibility()
            },
        )
        Spacer(Modifier.height(12.dp))
        RecoveryHop(
            n = 2,
            expanded = open == 2,
            onHeader = { focus = 2 },
            done = allowed,
            title = "App info → ⋮ → Allow restricted settings",
            detail = buildString {
                hint.brand?.let { append("On $it: ") }
                append(hint.menuLine)
                append(' ')
                append(OemHints.MENU_RULE)
            },
            action = "Open App info",
            onAction = {
                allowed = true
                focus = null
                ServiceControl.openAppInfo(context)
            },
            mock = { MenuMock() },
        )
        Spacer(Modifier.height(12.dp))
        RecoveryHop(
            n = 3,
            expanded = open == 3,
            onHeader = { focus = 3 },
            done = false,
            title = "Back to Accessibility, switch on",
            detail = "This time it turns on, and this screen moves on by itself.",
            action = "Open Accessibility",
            onAction = onOpenAccessibility,
        )

        Spacer(Modifier.height(18.dp))
        Expandable("Still no ⋮ menu?") {
            Bullet("Go back and tap the Ek Aur switch once. Press OK. Then open App info again — the menu appears only after that tap.")
            Bullet("Close Settings from Recents first. The menu is worked out when App info opens, so a Settings screen already sitting in the background never gets it.")
            Bullet("Open App info the long way: ${hint.appInfoPath}. Some phones show a cut-down page from the shortcut and the full one from the list.")
            Spacer(Modifier.height(6.dp))
            FlatButton("Open the apps list", onClick = { ServiceControl.openAppsList(context) })
            Spacer(Modifier.height(12.dp))
            Bullet("Restart the phone and try steps 1–3 again.")
            Bullet("Still stuck? Email me — your phone model goes in automatically, so I can tell you exactly where it is.")
            Spacer(Modifier.height(6.dp))
            FlatButton(
                text = "Email me",
                onClick = {
                    ServiceControl.emailDeveloper(
                        context,
                        subject = "Ek Aur setup help",
                        body = "Hi Hakkan,\n\nI can't find “Allow restricted settings” on my phone.\n\n" +
                            "Phone: ${ServiceControl.deviceLine()}\n" +
                            "Gate: $verdict\n\nWhat I see: ",
                    )
                },
            )
        }
    }
}

@Composable
private fun OverlayStep(apps: Set<TrackedApp>, onSkip: () -> Unit) {
    val context = LocalContext.current
    Column {
        Headline("Let the counter float over ${AppWords.appNames(apps)}")
        Spacer(Modifier.height(8.dp))
        Lead("Counting is on. This lets the little pill sit on top while you scroll, so you can watch the number climb.")
        Spacer(Modifier.height(22.dp))

        Card {
            PillMock()
            Spacer(Modifier.height(14.dp))
            Body("Turn on “Allow display over other apps” and come back.")
            Spacer(Modifier.height(16.dp))
            FlatButton(
                text = "Allow overlay",
                emphasised = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { ServiceControl.openOverlaySettings(context) },
            )
        }

        Spacer(Modifier.height(14.dp))
        Waiting("This moves on by itself once it's allowed.")
        Spacer(Modifier.height(10.dp))
        TextLink("Skip for now", onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun DoneStep(apps: Set<TrackedApp>, onDone: () -> Unit) {
    val context = LocalContext.current
    val openable = remember(apps) {
        TrackedApp.entries.filter { it in apps && ServiceControl.isInstalled(context, it) }
    }
    // A short hold on the check before anything else, so the moment lands.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); shown = true }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = Motion.bouncy(),
        label = "pop",
    )

    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { haptics.confirm() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(96.dp)
                    .graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop }
                    .clip(CircleShape)
                    .background(brush = buttonGradient()),
                contentAlignment = Alignment.Center,
            ) {
                EkIcon(EkIcons.Check, tint = Ink, size = 48.dp)
            }
            Celebration(key = Unit, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "You're counting.",
            style = MaterialTheme.typography.headlineMedium,
            color = Chalk,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Go scroll. The pill shows up the moment you're in ${AppWords.unit(apps).replace(" + ", " or ")}.",
            style = MaterialTheme.typography.bodyLarge,
            color = Smoke,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        if (openable.isNotEmpty()) {
            openable.forEachIndexed { index, app ->
                FlatButton(
                    text = "Open ${app.appName}",
                    emphasised = index == 0,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDone()
                        ServiceControl.openApp(context, app)
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
            FlatButton("Go to Home", quiet = true, modifier = Modifier.fillMaxWidth(), onClick = onDone)
        } else {
            FlatButton("Go to Home", emphasised = true, modifier = Modifier.fillMaxWidth(), onClick = onDone)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Optional: Battery and Usage access live in Setup. They keep counting alive on Realme, Xiaomi and Vivo phones.",
            style = MaterialTheme.typography.bodySmall,
            color = Smoke,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

/**
 * One app to pick. Selected cards take the app's own mark (pink for Reels, red
 * for Shorts), so the choice reads before the words do.
 */
@Composable
private fun AppChoice(
    app: TrackedApp,
    selected: Boolean,
    installed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mark = app.mark
    val soft = app.markSoft
    val border by animateColorAsState(if (selected) mark else InkLine, Motion.quick(), label = "choice")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) soft else Color.White)
            .border(2.dp, border, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBadge(app, size = 40.dp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) mark else Color.Transparent)
                    .border(2.dp, if (selected) mark else Ash, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) EkIcon(EkIcons.Check, tint = Color.White, size = 13.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(app.items, style = MaterialTheme.typography.titleMedium, color = Chalk)
        Text(
            text = if (installed) app.appName else "${app.appName} · not installed",
            style = MaterialTheme.typography.bodySmall,
            color = Smoke,
        )
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = Chalk,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
    )
}

@Composable
private fun Lead(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = Smoke)
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Smoke)
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(bottom = 10.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(brush = instaGradient()),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Smoke)
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Smoke,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** A quiet "we're watching" line with a soft pulsing dot. */
@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulseDot(color = Acid, active = true)
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Smoke)
    }
}

@Composable
private fun Callout(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceLav)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Chalk,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = Smoke)
    }
}

@Composable
private fun PreviewRow(n: Int, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NumberBadge(n, done = false)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Chalk,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = Smoke)
        }
    }
}

@Composable
private fun NumberBadge(n: Int, done: Boolean) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .then(
                if (done) Modifier.background(Good)
                else Modifier.background(brush = buttonGradient())
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            EkIcon(EkIcons.Check, tint = Ink, size = 16.dp)
        } else {
            Text(
                text = n.toString(),
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecoveryHop(
    n: Int,
    expanded: Boolean,
    onHeader: () -> Unit,
    done: Boolean,
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    mock: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (expanded) SurfaceLav.copy(alpha = 0.55f) else Color.Transparent)
            .animateContentSize(Motion.standard())
            .padding(10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !expanded, onClick = onHeader),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBadge(n, done)
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (done && !expanded) Smoke else Chalk,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 42.dp)) {
                Spacer(Modifier.height(6.dp))
                Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = Smoke)
                if (mock != null) {
                    Spacer(Modifier.height(10.dp))
                    mock()
                }
                Spacer(Modifier.height(12.dp))
                FlatButton(action, emphasised = true, modifier = Modifier.fillMaxWidth(), onClick = onAction)
            }
        }
    }
}

/** Three dots: one per required switch, plus the finish. */
@Composable
private fun ProgressDots(done: Int, current: SetupStep) {
    val active = when (current) {
        SetupStep.Welcome -> -1
        SetupStep.Accessibility -> 0
        SetupStep.Overlay -> 1
        SetupStep.Done -> 2
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val filled = i < done || i == active
            val w by animateFloatAsState(
                targetValue = if (i == active) 22f else 8f,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "dot",
            )
            Box(
                Modifier
                    .size(width = w.dp, height = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (filled) Modifier.background(brush = instaGradient())
                        else Modifier.background(InkLine)
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Mocks of the system screens, so people know what they're looking for
// ---------------------------------------------------------------------------

/**
 * A stand-in for the Settings row: the app's name and a switch that flips on
 * by itself, over and over -- a two-second demo of exactly what to do.
 */
@Composable
private fun SettingsRowMock() {
    val loop = rememberInfiniteTransition(label = "switch-demo")
    val phase by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "switch-phase",
    )
    // Off for the first half, a quick flip, on for the rest.
    val on = ((phase - 0.45f) / 0.1f).coerceIn(0f, 1f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLav)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(brush = instaGradient()),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Ek Aur (One More)",
                style = MaterialTheme.typography.titleMedium,
                color = Chalk,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (on > 0.5f) "On" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = if (on > 0.5f) Good else Smoke,
            )
        }
        SwitchMock(on)
    }
}

@Composable
private fun SwitchMock(on: Float) {
    Box(
        Modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.lerp(Ash.copy(alpha = 0.5f), Good, on))
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = on * 18.dp.toPx() }
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** The App info overflow menu, with the item to tap. */
@Composable
private fun MenuMock() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLav)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "App info",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            EkIcon(EkIcons.MoreVert, tint = Chalk, size = 20.dp)
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.dp, InkLine, RoundedCornerShape(10.dp))
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = "Uninstall updates",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Text(
                text = "Allow restricted settings",
                style = MaterialTheme.typography.bodyMedium,
                color = Chalk,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(SurfaceLav)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** The floating counter, over a faux reel. */
@Composable
private fun PillMock() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF2A2233), Color(0xFF14111A)),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            Modifier
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xF00A0A0A))
                .border(1.dp, Color(0x33DD2A7B), RoundedCornerShape(50))
                .padding(horizontal = 13.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(brush = instaGradient()),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "1",
                color = Color(0xFFF2F2F2),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
