package com.ekaur.android.ui.account

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ekaur.android.ui.common.BannerTone
import com.ekaur.android.ui.common.ChipTone
import com.ekaur.android.ui.common.InfoBanner
import com.ekaur.android.ui.common.ScreenHeader
import com.ekaur.android.ui.common.Spinner
import com.ekaur.android.ui.common.StatusChip
import com.ekaur.android.ui.common.pressScale
import com.ekaur.android.ui.common.reveal
import com.ekaur.android.ui.friends.NameState
import com.ekaur.android.ui.friends.NameStatusLine
import com.ekaur.android.ui.friends.rememberNameCheck
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.buttonGradient
import com.ekaur.android.ui.theme.instaGradient
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import com.ekaur.android.di.AppContainer
import com.ekaur.android.photo.AvatarPhoto
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.sync.Avatar
import com.ekaur.android.sync.Username
import com.ekaur.android.ui.avatar.AvatarCropScreen
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Expandable
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.ToggleRow
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything about the person: their photo, name, recovery code, and whether they
 * show on the leaderboard.
 *
 * Split out of Setup and reached from the avatar in the home header, so Setup is
 * only the getting-it-working steps and the account lives on its own.
 */
@Composable
fun AccountScreen(
    container: AppContainer,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }

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
        // Back from the crop returns to the account, not out of it.
        BackHandler { pendingPhoto = null; avatarNote = null }
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

    var codeLoading by remember { mutableStateOf(false) }
    fun fetchCode() {
        if (codeLoading) return
        codeLoading = true
        scope.launch {
            val fetched = withContext(Dispatchers.IO) {
                runCatching { container.supabase.registerDevice(container.deviceKey) }.getOrNull()
            }
            fetched?.let { container.settings.saveRecoveryCode(it) }
            recoveryCode = fetched ?: recoveryCode
            codeLoading = false
        }
    }
    LaunchedEffect(Unit) { if (recoveryCode == null) fetchCode() }

    val nameCheck = rememberNameCheck(container, newName, current = username)
    var hideError by remember { mutableStateOf<String?>(null) }
    val pickPhoto = {
        if (!uploading) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        ScreenHeader(
            title = "Account",
            subtitle = "Your name, your photo, your way back in.",
            onBack = onClose,
        )

        // Who you are
        Card(Modifier.reveal(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .pressScale(interaction, 0.94f)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "Change photo",
                                onClick = pickPhoto,
                            )
                            .background(brush = instaGradient())
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        UserAvatar(
                            username = username.orEmpty(),
                            url = Avatar.urlFor(
                                baseUrl = container.supabase.baseUrl,
                                userId = container.settings.userId.orEmpty(),
                                version = avatarVersion,
                            ),
                            size = 72.dp,
                        )
                        if (uploading) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center,
                            ) { Spinner(size = 26.dp, stroke = 3.dp) }
                        }
                    }
                    // The edit badge, so the picture reads as tappable.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(brush = buttonGradient()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✎", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "@" + username.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusChip(
                        text = when {
                            uploading -> "Uploading photo…"
                            hidden -> "Hidden from the leaderboard"
                            else -> "On the leaderboard"
                        },
                        tone = if (hidden) ChipTone.Neutral else ChipTone.Good,
                    )
                }
            }
            AnimatedVisibility(visible = avatarNote != null) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(
                        text = avatarNote.orEmpty(),
                        tone = if (avatarOk) BannerTone.Good else BannerTone.Warn,
                        glyph = if (avatarOk) "✓" else "⚠️",
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlatButton(
                    text = if (avatarVersion == null) "Add photo" else "New photo",
                    icon = "🖼️",
                    enabled = !uploading,
                    modifier = Modifier.weight(1f),
                    onClick = pickPhoto,
                )
                FlatButton(
                    text = "From files",
                    icon = "📁",
                    enabled = !uploading,
                    modifier = Modifier.weight(1f),
                    onClick = { if (!uploading) files.launch("image/*") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Name
        Card(Modifier.reveal(1)) {
            SectionLabel("Name")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "You can change it once every 14 days.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceLav)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("@", style = MaterialTheme.typography.titleLarge, color = Smoke)
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = Username.normalise(it).take(Username.MAX) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = Chalk),
                    cursorBrush = SolidColor(Acid),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (newName.isEmpty()) {
                            Text("new name", style = MaterialTheme.typography.titleLarge, color = Ash)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            NameStatusLine(nameCheck.state)
            AnimatedVisibility(visible = renameNote != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(
                        text = renameNote.orEmpty(),
                        tone = if (renameOk) BannerTone.Good else BannerTone.Warn,
                        glyph = if (renameOk) "✓" else "⚠️",
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlatButton(
                text = "Change name",
                emphasised = nameCheck.state is NameState.Free,
                enabled = nameCheck.state is NameState.Free,
                loading = renaming,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (nameCheck.state !is NameState.Free || renaming) return@FlatButton
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
                            renameNote = "Done. You're @$applied now."
                        }.onFailure { thrown ->
                            renameOk = false
                            renameNote = when (val cause = (thrown as? SyncException)?.error) {
                                is SyncError.Cooldown -> "Not yet — ${cause.daysLeft} days to go."
                                SyncError.NameTaken -> {
                                    nameCheck.markTaken(newName)
                                    "Someone just took it."
                                }
                                SyncError.Offline -> "No internet. Try again when you're online."
                                else -> "That didn't work. Try again."
                            }
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Leaderboard visibility
        Card(Modifier.reveal(2)) {
            SectionLabel("Leaderboard")
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Hide me",
                subtitle = if (hidden) {
                    "Hidden. Your name and counts show on nobody's list."
                } else {
                    "You're on the list. Only your name and daily total show."
                },
                checked = hidden,
                busy = hideBusy,
                onCheckedChange = { target ->
                    if (hideBusy) return@ToggleRow
                    hideBusy = true
                    hideError = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { container.supabase.setHidden(target) }.isSuccess
                        }
                        if (ok) container.settings.setHidden(target)
                        else hideError = "Couldn't save that. Check your internet and try again."
                        hideBusy = false
                    }
                },
            )
            AnimatedVisibility(visible = hideError != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(text = hideError.orEmpty(), tone = BannerTone.Warn, glyph = "⚠️")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Recovery code
        Card(Modifier.reveal(3)) {
            SectionLabel("Recovery code")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Reinstall on this phone and your account comes back by itself. " +
                    "On a new phone you'll need this code.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(10.dp))
            Expandable("Show my code") {
                val code = recoveryCode
                when {
                    code != null -> {
                        Text(
                            text = code.chunked(4).joinToString(" "),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                letterSpacing = 4.sp,
                                brush = instaGradient(),
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceLav)
                                .padding(vertical = 16.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlatButton(
                                text = "Copy",
                                icon = "📋",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Ek Aur recovery code", code))
                                    // Android 13+ confirms a copy itself.
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                            FlatButton(
                                text = "Share",
                                icon = "↗",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    ServiceControl.shareText(
                                        context,
                                        "Ek Aur recovery code: " + code +
                                            "\n(to get your account back on a new phone)",
                                    )
                                },
                            )
                        }
                    }
                    codeLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Spinner()
                        Spacer(Modifier.width(10.dp))
                        Text("Getting your code…", style = MaterialTheme.typography.bodyMedium, color = Smoke)
                    }
                    else -> InfoBanner(
                        text = "Couldn't get your code right now.",
                        tone = BannerTone.Warn,
                        glyph = "⚠️",
                        action = "Retry",
                        onAction = ::fetchCode,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Only your name and daily total ever leave the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Smoke,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
    }
}
