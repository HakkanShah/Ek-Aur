package com.ekaur.android.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.instaGradient

/**
 * A section heading: a short gradient bar, then the title. The gradient bar is
 * the app's signature mark, repeated so every card is unmistakably part of the
 * same thing.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(brush = instaGradient()),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = Chalk,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * A pill button. [emphasised] fills it with the gradient (the one main action);
 * otherwise a soft lavender fill for everything secondary -- no outlines
 * anywhere, the whole app is soft-filled shapes now.
 */
@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .clip(shape)
            .then(
                if (emphasised) Modifier.background(brush = instaGradient())
                else Modifier.background(color = SurfaceLav)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasised) Ink else Chalk,
            maxLines = 1,
            softWrap = false,
        )
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

/** One headline number with its name under it, a gradient tick before the name. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    // Compact padding and a title-sized number: three of these share one screen
    // width, so heavy card padding or a 26sp value pushes the row off the edge.
    Card(modifier = modifier, contentPadding = 14.dp) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = Chalk,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(brush = instaGradient()),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                caption,
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** A soft rounded card, floating on the tinted canvas. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = shape, clip = false, spotColor = Chalk),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** A tap-to-open section, so long detail can hide until it's wanted. */
@Composable
fun Expandable(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().clickable { open.value = !open.value },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Chalk)
            Text(if (open.value) "–" else "+", style = MaterialTheme.typography.titleLarge, color = Smoke)
        }
        if (open.value) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * A compact segmented switch: one soft pill split into tight segments, the
 * active one filled with the gradient. Fixed, small, and single-line, so it can
 * sit beside a title without crushing or wrapping the way three separate pill
 * buttons did.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .clip(shape)
            .background(color = SurfaceLav)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .clip(shape)
                    .then(
                        if (selected) Modifier.background(brush = instaGradient())
                        else Modifier
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Ink else Smoke,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * A labelled switch row: a title (and optional description) on the left, a real
 * Material switch on the right. Reads as an on/off setting, where a pill that
 * said "Auto-off: on" read as a button.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Chalk)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Smoke)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Acid,
                uncheckedThumbColor = Smoke,
                uncheckedTrackColor = SurfaceLav,
                uncheckedBorderColor = Ash,
            ),
        )
    }
}
