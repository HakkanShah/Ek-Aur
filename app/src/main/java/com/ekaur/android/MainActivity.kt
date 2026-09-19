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
import androidx.compose.ui.platform.LocalContext
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
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.EkAurTheme
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke

private enum class Tab(val label: String) {
    Home("ginti"),
    Events("events"),
    Status("status"),
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
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Home) }

    // The accessibility toggle lives in system settings and fires no callback,
    // so the state is re-read every time this screen comes back to the front.
    var serviceEnabled by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = ServiceControl.isAccessibilityServiceEnabled(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Tab.Home -> HomeScreen(container, serviceEnabled)
            Tab.Events -> EventInspectorScreen(container.eventLog)
            Tab.Status -> DiagnosticsScreen(
                status = container.serviceStatus,
                eventLog = container.eventLog,
                crashReporter = container.crashReporter,
                serviceEnabled = serviceEnabled,
            )
        }
    }
}

@Composable
private fun HomeScreen(container: AppContainer, serviceEnabled: Boolean) {
    val context = LocalContext.current
    val count by container.eventLog.liveCount.collectAsState()
    val connected by container.serviceStatus.connected.collectAsState()
    val state by container.serviceStatus.detectorState.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = "EK AUR",
            style = MaterialTheme.typography.labelLarge,
            color = Acid,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "is session me",
            style = MaterialTheme.typography.bodyLarge,
            color = Smoke,
        )

        Spacer(Modifier.height(32.dp))

        Card {
            SectionLabel("abhi")
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Dot(if (connected) Acid else Heat)
                Text(
                    text = when {
                        !serviceEnabled -> "service band hai"
                        !connected -> "service on hai, connect nahi hua"
                        else -> "chalu hai  ·  $state"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!serviceEnabled) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "settings me Ek Aur ko on karo, tabhi ginti chalu hogi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
                Spacer(Modifier.height(12.dp))
                FlatButton(
                    text = "settings kholo",
                    emphasised = true,
                    onClick = { ServiceControl.openAccessibilitySettings(context) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "count abhi memory me hai, save nahi hota.\nkal ke build me database aayega.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )
    }
}
