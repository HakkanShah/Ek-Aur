package com.ekaur.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.EkAurTheme
import com.ekaur.android.ui.theme.Smoke

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EkAurTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "EK AUR",
            style = MaterialTheme.typography.labelLarge,
            color = Acid,
        )

        Spacer(Modifier.height(20.dp))

        // Placeholder. The real counter arrives with the detection pipeline.
        Text(
            text = "0",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "reels aaj",
            style = MaterialTheme.typography.bodyLarge,
            color = Smoke,
        )

        Spacer(Modifier.height(36.dp))

        StatusRow(
            label = "counting abhi chalu nahi hai",
            detail = "detection agle build me aayega",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "v${BuildConfig.VERSION_NAME}  ·  build ${BuildConfig.VERSION_CODE}",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatusRow(label: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(7.dp)
                .background(Ash, CircleShape)
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
        }
    }
}

@Preview
@Composable
private fun HomePreview() {
    EkAurTheme { Surface(color = MaterialTheme.colorScheme.background) { HomeScreen() } }
}
