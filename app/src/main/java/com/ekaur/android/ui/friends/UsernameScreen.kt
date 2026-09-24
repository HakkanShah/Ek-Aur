package com.ekaur.android.ui.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Username
import com.ekaur.android.ui.common.BannerTone
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.InfoBanner
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.Spinner
import com.ekaur.android.ui.common.reveal
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Poppins
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.instaGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The first thing anyone sees, and the only gate in the app.
 *
 * Everyone who uses Ek Aur is on one leaderboard, so there is nothing to join
 * and no code to swap -- a name is the entire sign-up. Availability is checked
 * live as they type ([rememberNameCheck]).
 */
@Composable
fun UsernameScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    var typed by remember { mutableStateOf("") }
    var claiming by remember { mutableStateOf(false) }
    var claimError by remember { mutableStateOf<String?>(null) }

    var restoring by remember { mutableStateOf(true) }
    var showCodeEntry by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var recovering by remember { mutableStateOf(false) }
    var recoverError by remember { mutableStateOf<String?>(null) }

    // Before asking for a name, see whether this phone has been here before.
    // An uninstall wipes the session but not the device key, so a reinstall
    // finds its own account and the user never sees this screen at all.
    LaunchedEffect(Unit) {
        val recovered = withContext(Dispatchers.IO) {
            runCatching {
                container.supabase.ensureSignedIn()
                container.supabase.recoverAccount(container.deviceKey, null)
            }.getOrNull()
        }
        if (recovered != null) {
            container.settings.saveUsername(recovered)
            withContext(Dispatchers.IO) {
                runCatching { container.supabase.registerDevice(container.deviceKey) }
                    .getOrNull()
                    ?.let { container.settings.saveRecoveryCode(it) }
            }
            com.ekaur.android.data.work.SyncWorker.schedule(appContext)
        }
        restoring = false
    }

    if (restoring) {
        Column(
            modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("EK AUR", style = Wordmark)
            Spacer(Modifier.height(24.dp))
            Spinner(size = 28.dp, stroke = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Checking if you've been here before…",
                style = MaterialTheme.typography.bodyLarge,
                color = Smoke,
            )
        }
        return
    }

    val name = Username.normalise(typed)
    val check = rememberNameCheck(container, typed)
    val state = check.state
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(name) { claimError = null }

    fun claim() {
        if (state !is NameState.Free || claiming) return
        keyboard?.hide()
        claiming = true
        claimError = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    container.supabase.ensureSignedIn()
                    container.supabase.claimUsername(name)
                }
            }
            claiming = false
            outcome.onSuccess {
                container.settings.saveUsername(name)
                // Registered straight away, so the very first uninstall is
                // already recoverable.
                withContext(Dispatchers.IO) {
                    runCatching { container.supabase.registerDevice(container.deviceKey) }
                        .getOrNull()
                        ?.let { container.settings.saveRecoveryCode(it) }
                }
                com.ekaur.android.data.work.SyncWorker.schedule(appContext)
            }.onFailure { thrown ->
                val cause = (thrown as? SyncException)?.error
                claimError = when (cause) {
                    // The check is advisory; the unique index decides. Two
                    // people can pass the check in the same second.
                    SyncError.NameTaken -> {
                        check.markTaken(name)
                        "Someone just took it. Pick another."
                    }
                    is SyncError.Cooldown -> "Wait ${cause.daysLeft} days."
                    SyncError.StaleSession -> "Couldn't find your old account. Try again."
                    SyncError.SignupDisabled -> "Sign-up is switched off on the server."
                    SyncError.Offline -> "No internet. Try again when you're online."
                    is SyncError.Refused -> "That didn't work (${cause.status}). Try again."
                    null -> "That didn't work. Try again."
                }
            }
        }
    }

    fun recover() {
        if (code.length != 8 || recovering) return
        keyboard?.hide()
        recovering = true
        recoverError = null
        scope.launch {
            val recovered = withContext(Dispatchers.IO) {
                runCatching {
                    container.supabase.ensureSignedIn()
                    container.supabase.recoverAccount(null, code)
                }.getOrNull()
            }
            recovering = false
            if (recovered == null) {
                recoverError = "That code didn't work. Check it and try again."
            } else {
                container.settings.saveUsername(recovered)
                withContext(Dispatchers.IO) {
                    runCatching {
                        container.supabase.registerDevice(container.deviceKey)
                    }.getOrNull()?.let { container.settings.saveRecoveryCode(it) }
                }
                com.ekaur.android.data.work.SyncWorker.schedule(appContext)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("EK AUR", style = Wordmark, modifier = Modifier.reveal(0))
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Pick a name",
            style = MaterialTheme.typography.displayMedium,
            color = Chalk,
            modifier = Modifier.reveal(1),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "It's your name on the leaderboard. Everyone who has the app is on one list.",
            style = MaterialTheme.typography.bodyLarge,
            color = Smoke,
            modifier = Modifier.reveal(2),
        )

        Spacer(Modifier.height(24.dp))

        Card(Modifier.reveal(3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@", style = FieldStyle.copy(color = Ash))
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = typed,
                    // Folded as they type: the database rejects capitals
                    // outright, so accepting one here would be a lie to undo.
                    onValueChange = { typed = Username.normalise(it).take(Username.MAX) },
                    singleLine = true,
                    textStyle = FieldStyle,
                    cursorBrush = SolidColor(Acid),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { claim() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        if (typed.isEmpty()) {
                            Text(text = "yourname", style = FieldStyle.copy(color = Ash))
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            NameStatusLine(state)
        }

        Spacer(Modifier.height(16.dp))

        FlatButton(
            text = "Let's go",
            emphasised = true,
            enabled = state is NameState.Free,
            loading = claiming,
            modifier = Modifier
                .fillMaxWidth()
                .reveal(4),
            onClick = ::claim,
        )

        AnimatedVisibility(visible = claimError != null) {
            Column {
                Spacer(Modifier.height(10.dp))
                InfoBanner(text = claimError.orEmpty(), tone = BannerTone.Warn, glyph = "⚠️")
            }
        }

        Spacer(Modifier.height(18.dp))

        AnimatedVisibility(
            visible = showCodeEntry,
            enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
            exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.quick()),
        ) {
            Card {
                SectionLabel("Recover an old account")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "On a new phone? Enter the recovery code from your old phone's Account screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                    singleLine = true,
                    textStyle = CodeStyle,
                    cursorBrush = SolidColor(Acid),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { recover() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceLav)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (code.isEmpty()) Text("XXXXXXXX", style = CodeStyle.copy(color = Ash))
                        inner()
                    },
                )
                Spacer(Modifier.height(12.dp))
                FlatButton(
                    text = "Recover",
                    enabled = code.length == 8,
                    loading = recovering,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ::recover,
                )
                if (recoverError != null) {
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(text = recoverError.orEmpty(), tone = BannerTone.Warn, glyph = "⚠️")
                }
            }
        }
        if (!showCodeEntry) {
            FlatButton(
                text = "Have an old account? Use a code",
                quiet = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showCodeEntry = true },
            )
        }

        Spacer(Modifier.height(18.dp))

        Card {
            SectionLabel("What gets shared")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Just your name and your daily total. Which reels you watched, and when, " +
                    "never leave the phone. \"Hide me\" in Account takes you off the list.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

private val Wordmark = TextStyle(
    fontFamily = Poppins,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    letterSpacing = 4.sp,
    brush = instaGradient(),
)

private val FieldStyle = TextStyle(
    fontFamily = Poppins,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    color = Chalk,
)

private val CodeStyle = TextStyle(
    fontFamily = Poppins,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    letterSpacing = 4.sp,
    color = Chalk,
)
