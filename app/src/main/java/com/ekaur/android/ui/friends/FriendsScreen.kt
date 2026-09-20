package com.ekaur.android.ui.friends

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.FriendCode
import com.ekaur.android.sync.SyncResult
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The leaderboard's front door.
 *
 * Off by default, and off means off: until the button here is pressed, no
 * account exists, no request is made, and nothing has left the phone. The
 * README makes that promise and this screen is where it is kept.
 */
@Composable
fun FriendsScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val joined by container.settings.joined.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (joined) {
            JoinedPanel(container)
        } else {
            JoinPanel(container)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun JoinPanel(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Card {
        SectionLabel("leaderboard")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "dosto ke saath ginti compare karni hai? ek naam rakho aur " +
                "join kar lo. code milega, wo share karna.",
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )

        Spacer(Modifier.height(14.dp))

        NameField(value = name, onChange = { name = it.take(24) })

        Spacer(Modifier.height(14.dp))

        FlatButton(
            text = if (busy) "ruko..." else "join karo",
            emphasised = name.isNotBlank() && !busy,
            onClick = {
                if (name.isBlank() || busy) return@FlatButton
                busy = true
                error = null
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        runCatching {
                            container.supabase.signInAnonymously()
                            container.supabase.createProfile(name.trim())
                        }
                    }
                    busy = false
                    outcome.onSuccess { code ->
                        container.settings.saveProfile(name.trim(), code)
                        container.settings.setJoined(true)
                        com.ekaur.android.data.work.SyncWorker.schedule(context)
                    }.onFailure { thrown ->
                        // Named cases, because there is no logcat to read off
                        // this phone -- whatever the screen says is the only
                        // diagnosis anyone gets.
                        val cause = (thrown as? SyncException)?.error
                        error = when (cause) {
                            SyncError.SignupDisabled ->
                                "server pe anonymous sign-in abhi band hai. " +
                                    "Supabase dashboard me chalu karna padega."
                            SyncError.Offline ->
                                "internet nahi mila. baad me try karo."
                            else ->
                                "join nahi hua. baad me try karo."
                        }
                    }
                }
            },
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Heat)
        }
    }

    Spacer(Modifier.height(12.dp))

    Card {
        SectionLabel("kya upload hota hai")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "sirf har din ka total — tareekh, kitne reels, kitna time. " +
                "kaunsi reel dekhi, kab scroll kiya, kuch bhi raw — wo phone se " +
                "bahar jaata hi nahi.\n\nyeh switch band hai to koi account nahi " +
                "banta aur koi request nahi jaati.",
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
    }
}

@Composable
private fun JoinedPanel(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val code = container.settings.friendCode.orEmpty()
    var status by remember { mutableStateOf<String?>(null) }

    // A sync on open, so the number a friend sees is never a day behind just
    // because the periodic job has not come round yet.
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { container.syncer.syncNow() } }
        status = when (val r = result.getOrNull()) {
            is SyncResult.Uploaded -> if (r.days > 0) "${r.days} din upload hue" else "sab up to date"
            is SyncResult.NothingToDo -> "sab up to date"
            is SyncResult.Failed -> "sync nahi hua, baad me apne aap hoga"
            else -> null
        }
    }

    Card {
        SectionLabel("tumhara code")
        Spacer(Modifier.height(10.dp))
        Text(
            text = FriendCode.forDisplay(code),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 34.sp,
                letterSpacing = 4.sp,
            ),
            color = Acid,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = container.settings.displayName.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton(
                text = "code share karo",
                emphasised = true,
                onClick = { shareCode(context, code) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Card {
        SectionLabel("dost")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "dost jodna aur leaderboard agle update me aa raha hai. " +
                "abhi ke liye apna code share kar ke rakho.",
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
    }

    Spacer(Modifier.height(12.dp))

    Card {
        SectionLabel("sync")
        Spacer(Modifier.height(10.dp))
        Text(
            text = status ?: "dekh raha hoon...",
            style = MaterialTheme.typography.bodyMedium,
            color = Chalk,
        )
        Spacer(Modifier.height(14.dp))
        FlatButton(
            text = "leaderboard chhod do",
            onClick = {
                com.ekaur.android.data.work.SyncWorker.cancel(context)
                container.settings.leave()
                scope.launch { }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "chhodne par account aur code bhool jaate hain. tumhari ginti " +
                "phone me waisi hi rehti hai.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Chalk),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Acid),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        decorationBox = { inner ->
            Column(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = "naam (dost isi se pehchaanenge)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ash,
                    )
                }
                inner()
            }
        },
    )
}

private fun shareCode(context: Context, code: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Ek Aur pe mera code: ${FriendCode.forDisplay(code)}\n" +
                "add karo, dekhte hain kaun zyada scroll karta hai.",
        )
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "code share karo")) }
}
