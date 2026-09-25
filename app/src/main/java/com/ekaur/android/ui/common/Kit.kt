package com.ekaur.android.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.GoodSoft
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.HeatSoft
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.Poppins
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceBlush
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.buttonGradient
import com.ekaur.android.ui.theme.instaGradient
import com.ekaur.android.ui.theme.instaGradientSoft

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
 * The top of every tab and full screen: a title, an optional line under it,
 * an optional back button, and an optional trailing slot. One header means
 * every screen starts at the same height with the same rhythm.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIconButton(icon = EkIcons.Back, description = "Back", onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A 40dp round soft button holding a single icon: back, close. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(40.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(SurfaceLav)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        EkIcon(icon, tint = Chalk, size = 20.dp)
    }
}

/**
 * A pill button.
 *
 * - [emphasised] (primary) fills it with the gradient -- the one main action on
 *   a screen -- and lifts it on a soft shadow.
 * - Otherwise it's secondary: a soft lavender fill.
 * - [quiet] drops the fill entirely, for "Skip" and "Later".
 *
 * [loading] swaps the label for a spinner without changing the width, and
 * disables the button; [enabled] false fades it. [icon] is an optional
 * leading icon. Every tap answers with a quick squish.
 */
@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    quiet: Boolean = false,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    val interaction = remember { MutableInteractionSource() }
    val active = enabled && !loading
    val fade by animateFloatAsState(if (enabled) 1f else 0.45f, Motion.quick(), label = "enabled")
    Box(
        modifier
            .heightIn(min = 46.dp)
            .pressScale(interaction, 0.95f)
            .alpha(fade)
            .then(
                if (emphasised && enabled) Modifier.shadow(12.dp, shape, clip = false, spotColor = Acid)
                else Modifier
            )
            .clip(shape)
            .then(
                when {
                    quiet -> Modifier
                    emphasised -> Modifier.background(brush = buttonGradient())
                    else -> Modifier.background(color = SurfaceLav)
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = when {
            emphasised -> Ink
            quiet -> Smoke
            else -> Chalk
        }
        Row(
            Modifier.alpha(if (loading) 0f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                EkIcon(icon, tint = contentColor, size = 18.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = ButtonTextStyle,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (loading) {
            Spinner(size = 18.dp, color = if (emphasised) Ink else Acid)
        }
    }
}

private val ButtonTextStyle = TextStyle(
    fontFamily = Poppins,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    letterSpacing = 0.1.sp,
)

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
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = tint, textAlign = TextAlign.End)
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).background(color, CircleShape))
}

/**
 * One headline number with its name under it, a gradient tick before the name.
 *
 * Pass [count] to have the number count up instead of [value] snapping in.
 * [delta] adds a small up/down chip (compared with yesterday, say). In a row,
 * give the Row `IntrinsicSize.Min` height and each tile `fillMaxHeight()` so
 * they line up whatever their captions.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    count: Int? = null,
    delta: Int? = null,
) {
    Card(modifier = modifier, contentPadding = 14.dp) {
        val numberStyle = MaterialTheme.typography.headlineSmall
        if (count != null) {
            AnimatedCount(value = count, style = numberStyle, color = Chalk)
        } else {
            Text(
                text = value,
                style = numberStyle,
                color = Chalk,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                style = MaterialTheme.typography.bodySmall,
                color = Smoke,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (delta != null && delta != 0) {
            Spacer(Modifier.height(6.dp))
            DeltaChip(delta)
        } else if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = Smoke,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** An up or down arrow and the difference, in a small tinted pill. Up is magenta: more is the joke. */
@Composable
fun DeltaChip(delta: Int, modifier: Modifier = Modifier) {
    val up = delta > 0
    val color = if (up) Acid else Smoke
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (up) SurfaceBlush else SurfaceLav)
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EkIcon(if (up) EkIcons.ArrowUp else EkIcons.ArrowDown, tint = color, size = 12.dp)
        Spacer(Modifier.width(3.dp))
        Text(
            text = kotlin.math.abs(delta).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
        )
    }
}

/** A soft rounded card, floating on the tinted canvas. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
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

/**
 * A tap-to-open section, so long detail can hide until it's wanted. Opens with
 * an eased height change and a turning chevron, and remembers whether it was
 * open across a rotation.
 */
@Composable
fun Expandable(
    title: String,
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val haptics = rememberHaptics()
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.standard(), label = "chevron")
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) {
                    haptics.tick()
                    open = !open
                }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Chalk,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(SurfaceLav),
                contentAlignment = Alignment.Center,
            ) {
                EkIcon(EkIcons.ChevronDown, tint = Smoke, size = 16.dp, modifier = Modifier.rotate(turn))
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
            exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.quick()),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

/**
 * A compact segmented switch: one soft pill split into equal segments, with a
 * gradient indicator that slides to the chosen one.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segmentWidth: Dp = 52.dp,
) {
    val shape = RoundedCornerShape(percent = 50)
    val haptics = rememberHaptics()
    val indicatorX by animateDpAsState(
        targetValue = segmentWidth * selectedIndex,
        animationSpec = Motion.bouncy(),
        label = "segment",
    )
    Box(
        modifier
            .clip(shape)
            .background(color = SurfaceLav)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = indicatorX)
                .size(width = segmentWidth, height = 36.dp)
                .clip(shape)
                .background(brush = buttonGradient()),
        )
        Row {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val color by animateColorAsState(if (selected) Ink else Smoke, Motion.quick(), label = "seg-text")
                Box(
                    Modifier
                        .size(width = segmentWidth, height = 36.dp)
                        .clip(shape)
                        .clickable(role = Role.Tab) {
                            if (!selected) haptics.tick()
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * A labelled switch row: a title (and optional description) on the left, a real
 * Material switch on the right. [busy] shows a small spinner in place of the
 * switch while a change is being saved.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    busy: Boolean = false,
) {
    val haptics = rememberHaptics()
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Chalk)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Smoke)
            }
        }
        Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                Spinner()
            } else {
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        haptics.tick()
                        onCheckedChange(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink,
                        checkedTrackColor = Acid,
                        checkedBorderColor = Acid,
                        uncheckedThumbColor = Smoke,
                        uncheckedTrackColor = SurfaceLav,
                        uncheckedBorderColor = Ash,
                    ),
                )
            }
        }
    }
}

/** A tinted rounded square holding an icon -- a row's marker. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = SurfaceLav,
    iconTint: Color = Acid,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        EkIcon(icon, tint = iconTint, size = size * 0.5f)
    }
}

enum class ChipTone { Good, Warn, Neutral, Accent }

/** A small status pill: "On", "Needed", "Optional". */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        ChipTone.Good -> GoodSoft to Good
        ChipTone.Warn -> HeatSoft to Heat
        ChipTone.Neutral -> SurfaceLav to Smoke
        ChipTone.Accent -> SurfaceBlush to Acid
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

enum class BannerTone { Info, Warn, Good }

/**
 * A soft inline notice with an optional action: offline, "needs usage access",
 * a failed save. Never a dialog -- it sits where the problem is.
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (bg, fg) = when (tone) {
        BannerTone.Info -> SurfaceLav to Chalk
        BannerTone.Warn -> HeatSoft to Heat
        BannerTone.Good -> GoodSoft to Good
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            EkIcon(icon, tint = if (tone == BannerTone.Info) Acid else fg, size = 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (tone == BannerTone.Info) Chalk else fg,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = if (tone == BannerTone.Info) Acid else fg,
                maxLines = 1,
                modifier = Modifier
                    .pressScale(interaction, 0.92f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.75f))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * What a screen shows when there is nothing yet: a big icon, a line of the
 * app's humour, and optionally one thing to do about it.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(instaGradientSoft()),
            contentAlignment = Alignment.Center,
        ) {
            EkIcon(icon, tint = Acid, size = 28.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Chalk,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            FlatButton(action, onClick = onAction)
        }
    }
}

/** A bullet point with a small gradient dot, for readable lists of steps. */
@Composable
fun Bullet(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 10.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(brush = instaGradient()),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Smoke)
    }
}
