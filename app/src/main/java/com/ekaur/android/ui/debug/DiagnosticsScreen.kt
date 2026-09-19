package com.ekaur.android.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.BuildConfig
import com.ekaur.android.diagnostics.CrashReporter
import com.ekaur.android.diagnostics.EventLog
import com.ekaur.android.diagnostics.ServiceStatus
import com.ekaur.android.diagnostics.TextExport
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.StatRow
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke

/**
 * Plain-language runtime state, readable out loud.
 *
 * Exists because there is no ADB on the test device: this is how "why isn't it
 * counting?" gets answered without any tooling.
 */
@Composable
fun DiagnosticsScreen(
    status: ServiceStatus,
    eventLog: EventLog,
    crashReporter: CrashReporter,
    serviceEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connected by status.connected.collectAsState()
    val detectorState by status.detectorState.collectAsState()
    val lastEventAt by status.lastEventAtMs.collectAsState()
    val lastPackage by status.lastEventPackage.collectAsState()
    val eventsSeen by status.eventsSeen.collectAsState()
    val liveCount by eventLog.liveCount.collectAsState()
    val writeFailures by eventLog.writeFailures.collectAsState()
    val lastWriteError by eventLog.lastWriteError.collectAsState()
    val crash = crashReporter.pendingReport()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Card {
            SectionLabel("service")
            Spacer(Modifier.height(10.dp))
            StatRow(
                "settings me on hai",
                if (serviceEnabled) "haan" else "nahi",
                if (serviceEnabled) Acid else Heat,
            )
            StatRow(
                "service connected",
                if (connected) "haan" else "nahi",
                if (connected) Acid else Heat,
            )
            StatRow("detector state", detectorState)
            StatRow("events aaye", eventsSeen.toString())
            StatRow("last event", lastEventAt.asAgo())
            StatRow("last package", lastPackage ?: "--")
            StatRow("live count", liveCount.toString(), Acid)
            if (writeFailures > 0) {
                StatRow("db write fail", writeFailures.toString(), Heat)
                StatRow("last error", lastWriteError ?: "--", Heat)
            }

            if (!serviceEnabled) {
                Spacer(Modifier.height(14.dp))
                FlatButton(
                    text = "accessibility settings kholo",
                    emphasised = true,
                    onClick = { ServiceControl.openAccessibilitySettings(context) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("build")
            Spacer(Modifier.height(10.dp))
            StatRow("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            StatRow("android", android.os.Build.VERSION.SDK_INT.toString())
            StatRow("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = "diagnostics share karo",
                onClick = {
                    TextExport.share(
                        context = context,
                        fileName = "ekaur-diagnostics.txt",
                        content = snapshot(
                            serviceEnabled = serviceEnabled,
                            connected = connected,
                            detectorState = detectorState,
                            eventsSeen = eventsSeen,
                            lastEventAt = lastEventAt,
                            lastPackage = lastPackage,
                            liveCount = liveCount,
                        ),
                        chooserTitle = "Send diagnostics",
                    )
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        if (crash != null) {
            Card {
                SectionLabel("pichla crash")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = crash.lineSequence().take(6).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Chalk,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlatButton(
                        text = "crash bhejo",
                        emphasised = true,
                        onClick = {
                            TextExport.share(
                                context = context,
                                fileName = "ekaur-crash.txt",
                                content = crash,
                                chooserTitle = "Send crash report",
                            )
                        },
                    )
                    FlatButton(text = "hata do", onClick = { crashReporter.clear() })
                }
            }
        } else {
            Text(
                text = "koi crash nahi hua. abhi tak.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun Long.asAgo(): String {
    if (this == 0L) return "kabhi nahi"
    val seconds = (System.currentTimeMillis() - this) / 1000
    return when {
        seconds < 2 -> "abhi"
        seconds < 60 -> "${seconds}s pehle"
        seconds < 3600 -> "${seconds / 60}m pehle"
        else -> "${seconds / 3600}h pehle"
    }
}

private fun snapshot(
    serviceEnabled: Boolean,
    connected: Boolean,
    detectorState: String,
    eventsSeen: Long,
    lastEventAt: Long,
    lastPackage: String?,
    liveCount: Int,
): String = buildString {
    append("EK AUR diagnostics\n")
    append("version=").append(BuildConfig.VERSION_NAME)
    append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
    append("device=").append(android.os.Build.MANUFACTURER)
    append(' ').append(android.os.Build.MODEL)
    append(" android=").append(android.os.Build.VERSION.SDK_INT).append('\n')
    append("serviceEnabledInSettings=").append(serviceEnabled).append('\n')
    append("serviceConnected=").append(connected).append('\n')
    append("detectorState=").append(detectorState).append('\n')
    append("eventsSeen=").append(eventsSeen).append('\n')
    append("lastEventAt=").append(lastEventAt).append('\n')
    append("lastPackage=").append(lastPackage).append('\n')
    append("liveCount=").append(liveCount).append('\n')
}
