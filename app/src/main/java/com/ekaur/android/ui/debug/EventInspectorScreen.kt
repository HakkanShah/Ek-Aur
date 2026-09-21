package com.ekaur.android.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ekaur.android.diagnostics.CapturedEvent
import com.ekaur.android.diagnostics.EventLog
import com.ekaur.android.diagnostics.TextExport
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Smoke

/**
 * Live dump of everything the service sees.
 *
 * This is the detection-tuning loop made visible: scroll Reels, share the dump,
 * and the real Instagram view ids get read off it and written into
 * `DetectorRules`. Kept permanently -- it is how detection gets repaired when an
 * Instagram update breaks it, instead of debugging from scratch.
 */
@Composable
fun EventInspectorScreen(eventLog: EventLog, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val events by eventLog.events.collectAsState()
    val capturing by eventLog.capturing.collectAsState()
    val counted = events.count { it.counted }

    Column(modifier.fillMaxSize()) {
        Card(Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("event dump")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${events.size} events  ·  $counted counted",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Text(
                text = "open reels, scroll a bit, then hit share",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatButton(
                    text = "share",
                    emphasised = true,
                    onClick = {
                        TextExport.share(
                            context = context,
                            fileName = "ekaur-events.txt",
                            content = eventLog.exportText(),
                            chooserTitle = "Send event dump",
                        )
                    },
                )
                FlatButton(
                    text = if (capturing) "pause" else "resume",
                    onClick = { eventLog.setCapturing(!capturing) },
                )
                FlatButton(text = "clear", onClick = { eventLog.clear() })
            }
        }

        Spacer(Modifier.height(12.dp))

        if (events.isEmpty()) {
            Text(
                text = "nothing yet.\nservice on? open Instagram.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(events) { event -> EventRow(event) }
        }
    }
}

@Composable
private fun EventRow(event: CapturedEvent) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = buildString {
                append(event.eventType)
                append("  ")
                append(event.state)
                if (event.counted) append("  ← COUNT")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (event.counted) Acid else Smoke,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        event.viewId?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = Chalk,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        event.className?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
        if (event.fromIndex >= 0 || event.scrollDeltaY != 0) {
            Text(
                text = "from=${event.fromIndex} to=${event.toIndex} " +
                    "items=${event.itemCount} dy=${event.scrollDeltaY}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }
}
