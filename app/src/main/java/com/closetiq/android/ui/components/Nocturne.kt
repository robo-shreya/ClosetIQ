package com.closetiq.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.closetiq.android.ui.theme.Nocturne

/**
 * The pieces Nocturne repeats across screens.
 *
 * Radii follow the system: [RadiusMd] for tiles and controls, [RadiusLg] for the cards
 * that hold a whole idea.
 */

val RadiusMd = RoundedCornerShape(8.dp)
val RadiusLg = RoundedCornerShape(14.dp)

/** Uppercase eyebrow. Sets a section without spending a heading on it. */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Nocturne.Neutral500
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}

/**
 * A rule that fades to transparent at its open end rather than stopping cleanly —
 * the system's signature. Box outlines and short accent marks stay solid; only
 * freestanding rules like this one fade.
 */
@Composable
fun FadingRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(listOf(Nocturne.Divider, Color.Transparent))
            )
    )
}

/** A kicker with a fading rule running off to the right. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Kicker(text)
        FadingRule(modifier = Modifier.weight(1f))
    }
}

/**
 * The standard bordered container. On a dark ground elevation is an edge plus ambient
 * darkness, so this is a hairline border and a fill — never a stacked shadow.
 */
@Composable
fun NocturneCard(
    modifier: Modifier = Modifier,
    background: Brush = Brush.verticalGradient(listOf(Nocturne.Surface, Nocturne.Surface)),
    borderColor: Color = Nocturne.Neutral800,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RadiusLg)
            .background(background)
            .border(1.dp, borderColor, RadiusLg)
            .padding(contentPadding),
        content = content
    )
}

/**
 * A labelled 0–100 bar. Used for the skin metrics, where the number matters as much as
 * the bar — hence the tabular value sitting opposite the name.
 */
@Composable
fun MetricBar(
    name: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = Nocturne.Neutral400
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Nocturne.Text
            )
        }
        AccentBar(fraction = value / 100f, height = 4.dp)
    }
}

/** The accent used as a line: a track with a gradient fill, never a flood. */
@Composable
fun AccentBar(
    fraction: Float,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(99.dp))
            .background(Nocturne.Neutral900)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(Nocturne.Accent600, Nocturne.Accent))
                )
        )
    }
}

/**
 * A discrete score, drawn as filled and empty pips.
 *
 * A continuous bar implies a precision this measure does not have; five pips say
 * "roughly this out of five" and cannot be misread as a percentage.
 */
@Composable
fun PipScale(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
    filledColor: Color = Nocturne.Accent
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) filledColor else Nocturne.Neutral800)
            )
        }
    }
}

/** The spinner, at the one size the system uses it. */
@Composable
fun NocturneSpinner(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = Nocturne.Accent,
        trackColor = Nocturne.Neutral700,
        strokeWidth = 2.dp
    )
}

/** A dashed placeholder for something not built or not yet chosen. */
@Composable
fun DashedPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val stroke = with(LocalDensity.current) { 1.dp.toPx() }
    val radius = with(LocalDensity.current) { 14.dp.toPx() }
    val dash = with(LocalDensity.current) { floatArrayOf(8.dp.toPx(), 6.dp.toPx()) }

    Column(
        modifier = modifier
            .clip(RadiusLg)
            .background(Nocturne.Field)
            .drawBehind {
                drawRoundRect(
                    color = Nocturne.Neutral700,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(dash)
                    )
                )
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        content = content
    )
}

/** Centred muted footnote, for the one-line explanations under a form. */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Nocturne.Neutral600,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}
