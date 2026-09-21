package com.ekaur.android.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.InkLine
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.instaGradient

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Smoke,
        modifier = modifier,
    )
}

/**
 * The primary action: a gradient pill with white text. The one loud thing on a
 * card. [emphasised] false gives the quiet outlined version for anything that is
 * not the main thing to do.
 */
@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    if (emphasised) {
        Box(
            modifier
                .clip(shape)
                .background(brush = instaGradient(), shape = shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = Ink)
        }
    } else {
        Box(
            modifier
                .clip(shape)
                .border(BorderStroke(1.dp, InkLine), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = Chalk)
        }
    }
}

/** A number painted with the Instagram gradient. The app's one hero figure. */
@Composable
fun GradientNumber(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style.copy(brush = instaGradient()),
        maxLines = 1,
        modifier = modifier,
    )
}

/** A label/value pair. The workhorse of the diagnostics screen. */
@Composable
fun StatRow(label: String, value: String, tint: Color = Chalk) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Smoke)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).background(color, CircleShape))
}

/** One headline number with its name under it. Three sit side by side. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Card(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = Chalk,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Smoke, maxLines = 1)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = Ash, maxLines = 1)
        }
    }
}

/** A white card with a soft shadow, floating on the tinted canvas. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape, clip = false, spotColor = Chalk),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
