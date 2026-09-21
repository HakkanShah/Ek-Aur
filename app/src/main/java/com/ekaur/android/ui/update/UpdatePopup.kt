package com.ekaur.android.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ekaur.android.update.UpdateManager
import com.ekaur.android.update.UpdateState
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.instaGradient

/**
 * The update popup, shown over everything when there is something to say.
 *
 * Nothing to install silently -- Android forbids it for a sideloaded app -- so
 * the dialog's job is to make the one tap obvious: download when a release is
 * found, then Install once it's on disk.
 */
@Composable
fun UpdatePopup(manager: UpdateManager) {
    val state by manager.state.collectAsState()
    val context = LocalContext.current

    fun openPage() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(manager.pageUrl()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val current = state
    val visible = current is UpdateState.Available ||
        current is UpdateState.Downloading ||
        current is UpdateState.Ready ||
        current is UpdateState.Failed
    if (!visible) return

    Dialog(onDismissRequest = { manager.dismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(22.dp)) {
                when (current) {
                    is UpdateState.Available -> {
                        SectionLabel("Update available")
                        Version(current.release.versionName)
                        Notes(current.release.notes)
                        Spacer(Modifier.height(16.dp))
                        FlatButton(
                            text = "Update now",
                            emphasised = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { manager.startDownload(current.release) },
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryRow(onLater = { manager.dismiss() }, onPage = ::openPage)
                    }

                    is UpdateState.Downloading -> {
                        SectionLabel("Downloading update")
                        Version(current.release.versionName)
                        Spacer(Modifier.height(16.dp))
                        ProgressBar(current.progress)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${(current.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Smoke,
                        )
                    }

                    is UpdateState.Ready -> {
                        SectionLabel("Update ready")
                        Version(current.release.versionName)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap install, then confirm on the system screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Smoke,
                        )
                        Spacer(Modifier.height(16.dp))
                        FlatButton(
                            text = "Install now",
                            emphasised = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { manager.install() },
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryRow(onLater = { manager.dismiss() }, onPage = ::openPage)
                    }

                    is UpdateState.Failed -> {
                        SectionLabel("Update")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = current.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Chalk,
                        )
                        Spacer(Modifier.height(16.dp))
                        SecondaryRow(onLater = { manager.dismiss() }, onPage = ::openPage)
                    }

                    UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> Unit
                }
            }
        }
    }
}

@Composable
private fun Version(name: String) {
    if (name.isBlank()) return
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Version $name",
        style = MaterialTheme.typography.titleLarge,
        color = Chalk,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Notes(notes: String?) {
    if (notes.isNullOrBlank()) return
    Spacer(Modifier.height(8.dp))
    Text(
        text = notes.lineSequence().take(5).joinToString("\n"),
        style = MaterialTheme.typography.bodyMedium,
        color = Smoke,
    )
}

@Composable
private fun SecondaryRow(onLater: () -> Unit, onPage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlatButton(text = "Later", modifier = Modifier.weight(1f), onClick = onLater)
        FlatButton(text = "On GitHub", modifier = Modifier.weight(1f), onClick = onPage)
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
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
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(brush = instaGradient()),
            )
        }
    }
}
