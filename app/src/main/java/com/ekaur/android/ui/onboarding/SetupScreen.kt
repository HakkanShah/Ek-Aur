package com.ekaur.android.ui.onboarding

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import com.ekaur.android.di.AppContainer
import com.ekaur.android.overlay.OverlayPrefs
import com.ekaur.android.photo.AvatarPhoto
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.sync.Avatar
import com.ekaur.android.sync.Username
import com.ekaur.android.ui.avatar.AvatarCropScreen
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Dot
import com.ekaur.android.ui.common.Expandable
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.ToggleRow
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.stats.Hairline
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.instaGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the user sets up, grouped: permissions, payments, account, help.
 *
 * Kept short on purpose -- one line per thing, detail tucked away -- because the
 * old version was a wall of text nobody read.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    serviceEnabled: Boolean,
    onOpenEvents: () -> Unit = {},
    onOpenStatus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reset by remember { mutableStateOf(false) }
    val username by container.settings.username.collectAsState()
    val hidden by container.settings.hidden.collectAsState()
    var hideBusy by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var renameNote by remember { mutableStateOf<String?>(null) }
    var renameOk by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf(container.settings.recoveryCode) }
    var avatarVersion by remember { mutableStateOf(container.settings.avatarVersion) }
    var uploading by remember { mutableStateOf(false) }
    var avatarNote by remember { mutableStateOf<String?>(null) }
    var avatarOk by remember { mutableStateOf(false) }
    var paymentPaused by remember { mutableStateOf(false) }
    var usageOk by remember { mutableStateOf(ServiceControl.hasUsageAccess(context)) }
    var autoOff by remember { mutableStateOf(container.settings.autoOffOnLeave) }
    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }

    LifecycleResumeEffect(Unit) {
        usageOk = ServiceControl.hasUsageAccess(context)
        onPauseOrDispose { }
    }

    fun openForCrop(uri: Uri?) {
        if (uri == null) return
        avatarNote = null
        avatarOk = false
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { AvatarPhoto.decode(context, uri) } }
                .onSuccess { pendingPhoto = it }
                .onFailure { thrown ->
                    avatarNote = when ((thrown as? AvatarPhoto.PhotoException)?.failure) {
                        AvatarPhoto.Failure.CannotOpen -> "Couldn't open that file. Pick another."
                        AvatarPhoto.Failure.NotAnImage -> "Your phone couldn't read this format."
                        AvatarPhoto.Failure.TooBig -> "Photo too big, ran out of memory."
                        null -> "Couldn't open that photo. Try another."
                    }
                }
        }
    }

    fun upload(photo: Bitmap, crop: Avatar.Crop) {
        uploading = true
        avatarNote = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = AvatarPhoto.encode(photo, crop)
                    container.supabase.uploadAvatar(bytes) to bytes.size
                }
            }
            uploading = false
            result.onSuccess { (version, bytes) ->
                pendingPhoto = null
                avatarVersion = version
                container.settings.saveAvatarVersion(version)
                avatarOk = true
                avatarNote = "Done (${bytes / 1024} KB)"
            }.onFailure { thrown ->
                avatarOk = false
                avatarNote = when (val cause = (thrown as? SyncException)?.error) {
                    SyncError.Offline -> "No internet."
                    is SyncError.Refused -> "Server refused it (${cause.status})."
                    else -> "Couldn't upload the photo."
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> openForCrop(uri) }

    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> openForCrop(uri) }

    val cropping = pendingPhoto
    if (cropping != null) {
        AvatarCropScreen(
            photo = cropping,
            busy = uploading,
            error = if (avatarOk) null else avatarNote,
            onCancel = { pendingPhoto = null; avatarNote = null },
            onConfirm = { crop -> upload(cropping, crop) },
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(Unit) {
        if (recoveryCode == null) {
            recoveryCode = withContext(Dispatchers.IO) {
                runCatching { container.supabase.registerDevice(container.deviceKey) }.getOrNull()
            }
            recoveryCode?.let { container.settings.saveRecoveryCode(it) }
        }
    }

    val canOverlay = ServiceControl.canDrawOverlay(context)
    val batteryExempt = ServiceControl.isIgnoringBatteryOptimisations(context)
    val allDone = serviceEnabled && canOverlay && batteryExempt && usageOk
    val stepsLeft = listOf(serviceEnabled, canOverlay, batteryExempt, usageOk).count { !it }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // 1 — Status + permissions, one card
        Card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Dot(if (allDone) Good else Heat)
                Text(
                    text = if (allDone) "You're all set. Go scroll." else "$stepsLeft steps left.",
                    style = MaterialTheme.typography.titleLarge,
                    color = Chalk,
                )
            }
            Spacer(Modifier.height(14.dp))
            ProgressBar(done = 4 - stepsLeft, total = 4)
            Spacer(Modifier.height(18.dp))
            SectionLabel("Permissions")
            Spacer(Modifier.height(6.dp))
            PermRow("Accessibility", "Counts your reels. Nothing works without it.", serviceEnabled,
                "Open", { ServiceControl.openAccessibilitySettings(context) }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Blocked by a \"Restricted setting\" popup? App info → ⋮ " +
                        "→ Allow restricted settings, then come back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(8.dp))
                FlatButton("Open app info", onClick = { ServiceControl.openAppInfo(context) })
            }
            PermRow("Overlay", "Shows the counter over Instagram.", canOverlay,
                "Allow", { ServiceControl.openOverlaySettings(context) })
            PermRow("Battery", "Some phones kill background apps and counting stops.", batteryExempt,
                "Allow", { ServiceControl.openBatterySettings(context) })
            PermRow("Usage access", "Lets the app tell when you've left Instagram.", usageOk,
                "Allow", { ServiceControl.openUsageAccessSettings(context) })
        }

        Spacer(Modifier.height(12.dp))

        // Payments
        Card {
            SectionLabel("Payments")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "A bank or UPI app may block a payment while any accessibility service " +
                    "is on. It happens to any app not from the Play Store — nothing's wrong here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            Spacer(Modifier.height(16.dp))
            ToggleRow(
                title = "Turn off when I leave Instagram",
                subtitle = if (autoOff) {
                    "Ek Aur turns itself off when you leave, so payments stay clean. " +
                        "Tap it on to scroll."
                } else {
                    "You'll turn it off yourself before each payment."
                },
                checked = autoOff,
                onCheckedChange = {
                    autoOff = it
                    container.settings.autoOffOnLeave = it
                },
            )
            if (autoOff && !usageOk) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Needs \"Usage access\" above to work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Heat,
                )
            }

            Spacer(Modifier.height(16.dp))
            Expandable("Pause it yourself") {
                Text(
                    text = "Floating button — the fastest. Set up a shortcut and a small " +
                        "button floats on any screen, even over a payment app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(10.dp))
                FlatButton("Set up shortcut", emphasised = true,
                    onClick = { ServiceControl.openAccessibilityServiceDetails(context) })

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Quick Settings tile — add the \"Ek Aur\" tile to your shade, " +
                        "then one tap off, one tap on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(10.dp))
                    FlatButton("Add tile", onClick = { ServiceControl.requestAddPauseTile(context) })
                }

                Spacer(Modifier.height(16.dp))
                if (paymentPaused) {
                    Text(
                        text = "Turned off. Turn it back on in accessibility settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Acid,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlatButton("Turn back on",
                        onClick = { ServiceControl.openAccessibilitySettings(context) })
                } else {
                    FlatButton("Turn off for a payment", onClick = {
                        ServiceControl.pauseForPayment()
                        paymentPaused = true
                    })
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 3 — Account: photo, name, recovery, visibility in one card
        Card {
            SectionLabel("Account")
            Spacer(Modifier.height(16.dp))

            // Header: avatar + name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                UserAvatar(
                    username = username.orEmpty(),
                    url = Avatar.urlFor(
                        baseUrl = container.supabase.baseUrl,
                        userId = container.settings.userId.orEmpty(),
                        version = avatarVersion,
                    ),
                    size = 56.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = username.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Chalk,
                    )
                    Text(
                        text = when {
                            uploading -> "Uploading photo..."
                            hidden -> "Hidden from the leaderboard"
                            else -> "On the leaderboard"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Smoke,
                    )
                }
            }
            if (avatarNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = avatarNote!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (avatarOk) Acid else Heat,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlatButton(
                    text = if (avatarVersion == null) "Choose photo" else "Change photo",
                    emphasised = !uploading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!uploading) picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
                FlatButton(
                    text = "From files",
                    modifier = Modifier.weight(1f),
                    onClick = { if (!uploading) files.launch("image/*") },
                )
            }

            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))

            // Name
            Text(
                text = "Name — now \"" + username.orEmpty() + "\"",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Change once every 14 days. The old name is freed right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = newName,
                onValueChange = { newName = Username.normalise(it).take(Username.MAX) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = Chalk),
                cursorBrush = SolidColor(Acid),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (newName.isEmpty()) {
                        Text("New name", style = MaterialTheme.typography.titleLarge, color = Ash)
                    }
                    inner()
                },
            )
            if (renameNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = renameNote!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (renameOk) Good else Heat,
                )
            }
            Spacer(Modifier.height(12.dp))
            FlatButton(
                text = if (renaming) "Saving..." else "Change name",
                emphasised = Username.isValid(newName) && !renaming,
                onClick = {
                    if (!Username.isValid(newName) || renaming) return@FlatButton
                    renaming = true
                    renameNote = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { container.supabase.changeUsername(newName) }
                        }
                        renaming = false
                        result.onSuccess { applied ->
                            container.settings.saveUsername(applied)
                            newName = ""
                            renameOk = true
                            renameNote = "Done."
                        }.onFailure { thrown ->
                            renameOk = false
                            renameNote = when (val cause = (thrown as? SyncException)?.error) {
                                is SyncError.Cooldown -> "Not yet — ${cause.daysLeft} days to go."
                                SyncError.NameTaken -> "That name is taken."
                                SyncError.Offline -> "No internet."
                                else -> "Didn't work. Try again."
                            }
                        }
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))

            // Recovery code, tucked away
            Expandable("Recovery code") {
                Text(
                    text = "Reinstall on this phone and your account comes back on its own. " +
                        "On a new phone you'll need this code — write it down.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = recoveryCode ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Acid,
                )
                Spacer(Modifier.height(12.dp))
                FlatButton("Share code", onClick = {
                    val code = recoveryCode ?: return@FlatButton
                    ServiceControl.shareText(
                        context,
                        "Ek Aur recovery code: " + code +
                            "\n(to get your account back on a new phone)",
                    )
                })
            }

            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))

            // Leaderboard visibility
            ToggleRow(
                title = "Hide me",
                subtitle = if (hidden) {
                    "You're hidden — your name and counts show on nobody's list."
                } else {
                    "You're on the list. Only your name and daily total show."
                },
                checked = hidden,
                onCheckedChange = { target ->
                    if (hideBusy) return@ToggleRow
                    hideBusy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { container.supabase.setHidden(target) }.isSuccess
                        }
                        if (ok) container.settings.setHidden(target)
                        hideBusy = false
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Counter reset + install help + developer, all tucked away
        Card {
            SectionLabel("More")
            Spacer(Modifier.height(12.dp))
            Expandable("Counter missing?") {
                Text(
                    text = "If you dragged it to the edge and it vanished, bring it back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(12.dp))
                FlatButton(
                    text = if (reset) "Done ✓" else "Reset counter",
                    onClick = { OverlayPrefs(context).clearPosition(); reset = true },
                )
            }
            Spacer(Modifier.height(16.dp))
            Expandable("Trouble installing?") {
                Text(
                    text = "• \"App blocked\" by Play Protect: Play Store → profile " +
                        "→ Play Protect → ⚙ → turn off scanning, install, turn it " +
                        "back on.\n\n• \"App not installed\" means an older APK over a newer " +
                        "one — install the newest file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
            }
            Spacer(Modifier.height(16.dp))
            Expandable("Developer") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlatButton("Events", onClick = onOpenEvents)
                    FlatButton("Status", onClick = onOpenStatus)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Only your name and daily total ever leave the phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )

        Spacer(Modifier.height(24.dp))
    }
}

/** One permission: a status dot, a title, and -- only if it's off -- a line and a button. */
@Composable
private fun PermRow(
    title: String,
    why: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    extra: @Composable (() -> Unit)? = null,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Dot(if (done) Good else Ash)
            Text(title, style = MaterialTheme.typography.titleLarge, color = Chalk)
            Spacer(Modifier.weight(1f))
            if (done) Text("✓", style = MaterialTheme.typography.titleLarge, color = Good)
        }
        if (!done) {
            Spacer(Modifier.height(6.dp))
            Text(why, style = MaterialTheme.typography.bodyMedium, color = Smoke)
            Spacer(Modifier.height(10.dp))
            FlatButton(actionLabel, emphasised = true, onClick = onAction)
            extra?.invoke()
        }
    }
}

/** A slim gradient progress bar: how many of the setup steps are done. */
@Composable
private fun ProgressBar(done: Int, total: Int) {
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(color = SurfaceLav),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(brush = instaGradient()),
            )
        }
    }
}
