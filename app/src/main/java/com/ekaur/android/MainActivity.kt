package com.ekaur.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ekaur.android.di.AppContainer
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Dot
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.debug.DiagnosticsScreen
import com.ekaur.android.ui.debug.EventInspectorScreen
import com.ekaur.android.ui.onboarding.SetupScreen
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.EkAurTheme
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke

private enum class Tab(val label: String) {
    Home("ginti"),
    Setup("setup"),
    Events("events"),
    Status("status"),
}

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
    var permissions by remember { mutableStateOf(Permissions()) }
    var tab by remember { mutableStateOf(Tab.Home) }

    // None of these fire a callback when they change -- the user grants them in
    // system settings and comes back -- so they are re-read on every resume.
    LifecycleResumeEffect(Unit) {
        permissions = Permissions(
            service = ServiceControl.isAccessibilityServiceEnabled(context),
            overlay = ServiceControl.canDrawOverlay(context),
            battery = ServiceControl.isIgnoringBatteryOptimisations(context),
        )
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Tab.entries.forEach { entry ->
                FlatButton(
                    text = entry.label,
                    emphasised = entry == tab,
                    onClick = { tab = entry },
                )
            }
        }

        when (tab) {
            Tab.Home -> HomeScreen(container, permissions) { tab = Tab.Setup }
            Tab.Setup -> SetupScreen(serviceEnabled = permissions.service)
            Tab.Events -> EventInspectorScreen(container.eventLog)
            Tab.Status -> DiagnosticsScreen(
                status = container.serviceStatus,
                eventLog = container.eventLog,
                crashReporter = container.crashReporter,
                serviceEnabled = permissions.service,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    container: AppContainer,
    permissions: Permissions,
    onOpenSetup: () -> Unit,
) {
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
        Spacer(Modifier.height(48.dp))

        Text("EK AUR", style = MaterialTheme.typography.labelLarge, color = Acid)

        Spacer(Modifier.height(16.dp))

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text("reels aaj", style = MaterialTheme.typography.bodyLarge, color = Smoke)

        if (activeMs > 0) {
            Text(
                text = activeMs.asDuration() + " scroll kiya",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
        }

        Spacer(Modifier.height(32.dp))

        Card {
            SectionLabel("abhi")
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Dot(if (permissions.allGranted && connected) Acid else Heat)
                Text(
                    text = when {
                        !permissions.service -> "service band hai"
                        !connected -> "service on hai, connect nahi hua"
                        !permissions.overlay -> "ginti chalu, counter dikhega nahi"
                        !permissions.battery -> "chalu hai, par battery maar sakti hai"
                        else -> "chalu hai  ·  $state"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!permissions.allGranted) {
                Spacer(Modifier.height(14.dp))
                FlatButton(text = "setup poora karo", emphasised = true, onClick = onOpenSetup)
            }
        }
    }
}

private fun Long.asDuration(): String {
    val totalMinutes = this / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        totalMinutes > 0 -> "${totalMinutes}m"
        else -> "${this / 1000}s"
    }
}
