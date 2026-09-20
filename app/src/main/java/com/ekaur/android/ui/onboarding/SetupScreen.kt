package com.ekaur.android.ui.onboarding

import android.os.Build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import android.graphics.Bitmap
import android.net.Uri
import com.ekaur.android.photo.AvatarPhoto
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Avatar
import com.ekaur.android.sync.Username
import com.ekaur.android.overlay.OverlayPrefs
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.ui.avatar.AvatarCropScreen
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Dot
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The three things that have to be granted before any of this works.
 *
 * Written out in order with live status, because two of the three fail in ways
 * that give the user nothing to act on: Android blocks the accessibility toggle
 * for sideloaded apps behind a dialog with only an OK button, and battery
 * optimisation kills the service silently.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    serviceEnabled: Boolean,
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

    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }

    // Decoded the moment a photo is chosen rather than at upload time, so a
    // file the phone cannot read says so immediately -- and so the user sees
    // what they are sending before anything is sent.
    fun openForCrop(uri: Uri?) {
        if (uri == null) return
        avatarNote = null
        avatarOk = false
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { AvatarPhoto.decode(context, uri) }
            }.onSuccess {
                pendingPhoto = it
            }.onFailure { thrown ->
                // Three different faults used to share one message, which said
                // nothing and cost a round trip to work out. There is no logcat
                // on this phone; the screen is the only instrument.
                avatarNote = when ((thrown as? AvatarPhoto.PhotoException)?.failure) {
                    AvatarPhoto.Failure.CannotOpen ->
                        "ye file khuli nahi. gallery se dusri chuno."
                    AvatarPhoto.Failure.NotAnImage ->
                        "is photo ka format phone padh nahi paaya."
                    AvatarPhoto.Failure.TooBig ->
                        "photo bahut badi hai, memory kam pad gayi."
                    null -> "photo kholne me dikkat aayi. dusri try karo."
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
                avatarNote = "ho gaya (${bytes / 1024} KB)"
            }.onFailure { thrown ->
                // The crop screen stays open on a failure, so pressing lagao
                // again retries without re-picking and re-framing the photo.
                avatarOk = false
                avatarNote = when (val cause = (thrown as? SyncException)?.error) {
                    SyncError.Offline -> "internet nahi mila."
                    is SyncError.Refused -> "server ne mana kiya (${cause.status})."
                    else -> "photo nahi bhej paaya."
                }
            }
        }
    }

    // The gallery picker: no storage permission is asked for, and the app only
    // ever receives the one image the user chose.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> openForCrop(uri) }

    // The way back for a photo the gallery does not list -- a jpg sitting in
    // Downloads, or anything a chat app saved where the media scanner never
    // looked. Same decoder, so it accepts whatever the phone can display.
    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> openForCrop(uri) }

    val cropping = pendingPhoto
    if (cropping != null) {
        AvatarCropScreen(
            photo = cropping,
            busy = uploading,
            error = if (avatarOk) null else avatarNote,
            onCancel = {
                pendingPhoto = null
                avatarNote = null
            },
            onConfirm = { crop -> upload(cropping, crop) },
            modifier = modifier,
        )
        return
    }

    // Fetched once if the device has a session but no stored code -- an account
    // made before recovery existed, or one restored onto a new phone.
    LaunchedEffect(Unit) {
        if (recoveryCode == null) {
            recoveryCode = withContext(Dispatchers.IO) {
                runCatching {
                    container.supabase.registerDevice(container.deviceKey)
                }.getOrNull()
            }
            recoveryCode?.let { container.settings.saveRecoveryCode(it) }
        }
    }
    val canOverlay = ServiceControl.canDrawOverlay(context)
    val batteryExempt = ServiceControl.isIgnoringBatteryOptimisations(context)
    val allDone = serviceEnabled && canOverlay && batteryExempt

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (allDone) {
            Card {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Dot(Acid)
                    Text(
                        text = "sab set hai. ab bas scroll karo.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Chalk,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        SetupStep(
            index = "01",
            title = "accessibility",
            why = "reels ginne ke liye. iske bina kuch nahi hoga.",
            done = serviceEnabled,
            actionLabel = "accessibility kholo",
            onAction = { ServiceControl.openAccessibilitySettings(context) },
            extra = {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "\"Restricted setting\" wala popup aaye to Android sideloaded app ko " +
                        "rok raha hai. app info → ⋮ (upar dayein) → Allow restricted settings, " +
                        "phir wapas yahan aake on karo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "app info kholo",
                    onClick = { ServiceControl.openAppInfo(context) },
                )
            },
        )

        Spacer(Modifier.height(12.dp))

        SetupStep(
            index = "02",
            title = "overlay",
            why = "instagram ke upar counter dikhane ke liye.",
            done = canOverlay,
            actionLabel = "overlay permission do",
            onAction = { ServiceControl.openOverlaySettings(context) },
        )

        Spacer(Modifier.height(12.dp))

        SetupStep(
            index = "03",
            title = "battery",
            why = "realme/oppo/xiaomi background me app ko maar dete hain. " +
                "tab ginti chupchap band ho jaati hai.",
            done = batteryExempt,
            actionLabel = "battery se chhoot do",
            onAction = { ServiceControl.openBatterySettings(context) },
        )

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("payment / UPI app")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "koi bank ya UPI app is app ko \"suspicious\" bata sakti hai aur " +
                    "payment rok sakti hai. ghabrao mat — ye har us app pe hota hai jo " +
                    "Play Store se nahi aayi. ChatGPT bhi screen padhta hai par usko " +
                    "chhoot isliye milti hai kyunki wo Play Store se hai. app me kuch " +
                    "kharab nahi.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "iska ek hi hal hai: payment ke waqt Ek Aur ko band karo, baad me " +
                    "on. sabse tez tarika niche — ek tap me on/off, kahin se bhi.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "1. floating button (sabse tez)",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "niche wale button se Ek Aur ka page kholo → \"shortcut\" ya " +
                    "\"accessibility button\" on karo. phir screen pe ek chhota button " +
                    "aayega jise dabate hi Ek Aur band/on ho jayega — payment app ke " +
                    "upar bhi.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            Spacer(Modifier.height(10.dp))
            FlatButton(
                text = "shortcut set karo",
                emphasised = true,
                onClick = { ServiceControl.openAccessibilityServiceDetails(context) },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "2. Quick Settings tile",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "notification wale parde me \"Ek Aur\" ka tile laga lo, phir ek " +
                    "tap me band, ek tap me on.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "tile add karo",
                    emphasised = false,
                    onClick = { ServiceControl.requestAddPauseTile(context) },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "3. yahin se band karo",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            if (paymentPaused) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "band kar diya. payment ke baad wapas on karne ke liye " +
                        "accessibility settings kholo (ya floating button dabao).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Acid,
                )
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "wapas on karo",
                    emphasised = false,
                    onClick = { ServiceControl.openAccessibilitySettings(context) },
                )
            } else {
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "payment ke liye abhi band karo",
                    emphasised = false,
                    onClick = {
                        // If the service is not actually running, treat it as
                        // already off rather than claiming a pause that did
                        // nothing -- either way, nothing is left for a UPI app
                        // to warn about.
                        ServiceControl.pauseForPayment()
                        paymentPaused = true
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("counter kahin kho gaya?")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "counter ko screen ke bilkul kinare drag kiya ho aur wo " +
                    "dikh na raha ho, to yahan se wapas beech me le aao.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = if (reset) "ho gaya \u2713" else "counter wapas laao",
                onClick = {
                    OverlayPrefs(context).clearPosition()
                    reset = true
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("photo")
            Spacer(Modifier.height(12.dp))
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
                    size = 64.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (uploading) {
                            "bhej raha hoon..."
                        } else {
                            "leaderboard pe naam ke saath dikhegi. chhoti kar ke " +
                                "bheji jaati hai, poori photo nahi."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Smoke,
                    )
                    if (avatarNote != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = avatarNote!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (avatarOk) Acid else Heat,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = if (avatarVersion == null) "photo chuno" else "photo badlo",
                emphasised = !uploading,
                onClick = {
                    if (!uploading) {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            FlatButton(
                text = "file se chuno",
                emphasised = false,
                onClick = { if (!uploading) files.launch("image/*") },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("naam badlo")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "abhi: " + username.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "naam 14 din me ek baar badal sakte ho. purana naam turant " +
                    "kisi aur ko mil sakta hai.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = newName,
                onValueChange = { newName = Username.normalise(it).take(Username.MAX) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Chalk),
                cursorBrush = SolidColor(Acid),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (newName.isEmpty()) {
                        Text("naya naam", style = MaterialTheme.typography.bodyLarge, color = Ash)
                    }
                    inner()
                },
            )
            if (renameNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = renameNote!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (renameOk) Acid else Heat,
                )
            }
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = if (renaming) "ruko..." else "naam badlo",
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
                            renameNote = "ho gaya"
                        }.onFailure { thrown ->
                            renameOk = false
                            renameNote = when (val cause = (thrown as? SyncException)?.error) {
                                // Enforced by the server, so a reinstall does
                                // not reset it.
                                is SyncError.Cooldown ->
                                    "abhi nahi \u2014 ${cause.daysLeft} din aur ruko."
                                SyncError.NameTaken -> "ye naam le liya gaya hai."
                                SyncError.Offline -> "internet nahi mila."
                                else -> "nahi hua. baad me try karo."
                            }
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("recovery code")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "app delete karke wapas install karoge to isi phone pe " +
                    "account apne aap mil jayega. naye phone pe ye code chahiye " +
                    "hoga \u2014 kahin likh ke rakh lo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = recoveryCode ?: "\u2014",
                style = MaterialTheme.typography.headlineMedium,
                color = Acid,
            )
            Spacer(Modifier.height(12.dp))
            FlatButton(
                text = "code share karo",
                onClick = {
                    val code = recoveryCode ?: return@FlatButton
                    ServiceControl.shareText(
                        context,
                        "Ek Aur recovery code: " + code + "\n(naye phone pe account wapas lene ke liye)",
                    )
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("leaderboard")
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (hidden) {
                    "abhi tum chhupe ho. doosron ki list me tumhara naam aur " +
                        "ginti nahi dikhti."
                } else {
                    "tum \"" + username.orEmpty() + "\" naam se list me ho. " +
                        "sirf naam aur har din ka total dikhta hai."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = when {
                    hideBusy -> "ruko..."
                    hidden -> "wapas list me aao"
                    else -> "chhup jao"
                },
                onClick = {
                    if (hideBusy) return@FlatButton
                    hideBusy = true
                    val target = !hidden
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { container.supabase.setHidden(target) }.isSuccess
                        }
                        // Only mirrored locally once the server agreed, so the
                        // switch never claims something the database did not do.
                        if (ok) container.settings.setHidden(target)
                        hideBusy = false
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("install karte waqt")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "\u2022 Play Protect \"app blocked\" bole to: Play Store \u2192 profile " +
                    "\u2192 Play Protect \u2192 \u2699 \u2192 scanning band karo, install karo, " +
                    "phir wapas chalu kar do. Sideloaded app jo accessibility maangta hai, " +
                    "usko wo hamesha flag karega \u2014 app me kuch galat nahi hai.\n\n" +
                    "\u2022 \"App not installed\" aaye to purani APK install karne ki koshish " +
                    "ho rahi hai. Android purane version ko naye ke upar nahi chadhne deta \u2014 " +
                    "sabse nayi wali file install karo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "sab kuch phone me hi rehta hai. koi account nahi, koi server nahi.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SetupStep(
    index: String,
    title: String,
    why: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    extra: @Composable (() -> Unit)? = null,
) {
    Card {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Dot(if (done) Acid else Heat)
            SectionLabel("$index  $title")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = why,
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
        if (!done) {
            Spacer(Modifier.height(14.dp))
            FlatButton(text = actionLabel, emphasised = true, onClick = onAction)
            extra?.invoke()
        }
    }
}
