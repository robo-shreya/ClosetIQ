package com.closetiq.android.ui.mirror

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.OutfitPick
import com.closetiq.android.domain.model.ScoredGarment
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.domain.model.Undertone
import com.closetiq.android.ui.components.DashedPanel
import com.closetiq.android.ui.components.Kicker
import com.closetiq.android.ui.components.MetricBar
import com.closetiq.android.ui.components.NocturneCard
import com.closetiq.android.ui.components.NocturneSpinner
import com.closetiq.android.ui.components.RadiusLg
import com.closetiq.android.ui.components.RadiusMd
import com.closetiq.android.ui.components.SectionLabel
import com.closetiq.android.ui.components.toComposeColor
import com.closetiq.android.ui.theme.Nocturne
import java.io.File
import kotlin.math.roundToInt

/**
 * The centrepiece. Everything in the demo video happens here.
 *
 * Reading order is deliberate: today's skin, then the item it surfaced, then why, then
 * the render, then "wore it". The reason line sits directly under the hero name and
 * above the buttons, because the argument has to land before the picture does — and
 * because it must be visible without scrolling.
 */
@Composable
fun MirrorScreen(
    container: AppContainer,
    viewModel: MirrorViewModel = viewModel(factory = MirrorViewModel.factory(container))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::onPhotoPicked) }

    val pick = state.pick

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkinCard(
            reading = state.reading,
            analysing = state.analysing,
            onCheckMirror = { photoPicker.launch("image/*") },
            onRetake = { photoPicker.launch("image/*") }
        )

        state.error?.let { message ->
            ErrorNote(message = message, onDismiss = viewModel::dismissError)
        }

        SectionLabel("The pick")

        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { NocturneSpinner(size = 20.dp) }

            pick != null -> PickSection(
                pick = pick,
                canRender = state.heroCanRender,
                rendering = state.rendering,
                renderUrl = state.renderUrl,
                renderNote = state.renderNote,
                onRender = viewModel::onRenderHero,
                onWoreIt = viewModel::onWoreIt
            )

            else -> DashedPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nothing to show yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = Nocturne.Neutral300
                )
                Text(
                    text = "Add a garment, or wait for the closet to finish seeding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nocturne.Neutral600
                )
            }
        }
    }
}

@Composable
private fun SkinCard(
    reading: SkinReading?,
    analysing: Boolean,
    onCheckMirror: () -> Unit,
    onRetake: () -> Unit
) {
    NocturneCard(
        modifier = Modifier.fillMaxWidth(),
        background = Brush.linearGradient(
            listOf(Nocturne.SkinCardTop, Nocturne.SkinCardBottom)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Kicker("Today's skin")
            Text(
                text = if (reading != null) "YouCam · 1 call" else "optional",
                style = MaterialTheme.typography.labelSmall,
                color = Nocturne.Accent300
            )
        }

        if (reading == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "No photo yet. The closet works without one — a photo only " +
                        "sharpens the pick.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Nocturne.Neutral300
                )
                OutlinedButton(
                    onClick = onCheckMirror,
                    enabled = !analysing
                ) {
                    Text(if (analysing) "Reading…" else "Pick an image")
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(reading.swatchBrush())
                            .border(1.dp, Nocturne.Neutral800, CircleShape)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "${reading.undertone.name.lowercase()
                                .replaceFirstChar { it.uppercase() }} undertone",
                            style = MaterialTheme.typography.titleMedium,
                            color = Nocturne.Text
                        )
                        Text(
                            text = "Fitzpatrick ${reading.fitzpatrickNumeral()} · one YouCam call",
                            style = MaterialTheme.typography.labelMedium,
                            color = Nocturne.Neutral500
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBar("Redness", (reading.redness * 100).roundToInt())
                    MetricBar("Dullness", (reading.dullness * 100).roundToInt())
                    MetricBar("Dark circles", (reading.darkCircles * 100).roundToInt())
                }

                TextButton(
                    onClick = onRetake,
                    enabled = !analysing,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (analysing) "Reading…" else "Retake",
                        style = MaterialTheme.typography.labelMedium,
                        color = Nocturne.Neutral500
                    )
                }
            }
        }
    }
}

@Composable
private fun PickSection(
    pick: OutfitPick,
    canRender: Boolean,
    rendering: Boolean,
    renderUrl: String?,
    renderNote: String?,
    onRender: () -> Unit,
    onWoreIt: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroCard(hero = pick.hero, reason = pick.reason)

        if (pick.supporting.isNotEmpty()) {
            // weight(1f), not fillMaxWidth(fraction): a fraction inside a Row measures
            // against the space left over, so the second tile would come out smaller.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pick.supporting.forEach { scored ->
                    SupportingTile(scored = scored, modifier = Modifier.weight(1f))
                }
            }
        }

        if (rendering) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RadiusMd)
                    .border(1.dp, Nocturne.Neutral800, RadiusMd)
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NocturneSpinner()
                Text(
                    text = "One VTO render…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Nocturne.Neutral400
                )
            }
        }

        renderUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "Try-on render",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RadiusMd)
                    .border(1.dp, Nocturne.Accent800, RadiusMd),
                contentScale = ContentScale.FillWidth
            )
        }

        // The Gemma mock returns commentary instead of an image, so showing it proves the
        // whole path works end to end before YouCam is switched on.
        if (!canRender) {
            Text(
                text = "This one is a colour swatch, so there's nothing to render. " +
                    "Photograph it to try it on.",
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral600
            )
        }

        renderNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral600
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onRender,
                enabled = canRender && !rendering,
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text(if (rendering) "Rendering…" else "See it on me")
            }
            OutlinedButton(
                onClick = onWoreIt,
                modifier = Modifier.weight(1f).height(46.dp),
                border = BorderStroke(1.dp, Nocturne.Neutral700)
            ) {
                Text("Wore it", color = Nocturne.Text)
            }
        }
    }
}

@Composable
private fun HeroCard(hero: ScoredGarment, reason: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RadiusLg)
            .background(Nocturne.Surface)
            .border(1.dp, Nocturne.Neutral800, RadiusLg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HERO_IMAGE_HEIGHT_DP.dp)
                .background(hero.garment.color.toComposeColor())
        ) {
            hero.garment.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = hero.garment.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = hero.garment.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Nocturne.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "Hero",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nocturne.Accent300
                )
            }

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = Nocturne.Neutral400
            )
        }
    }
}

@Composable
private fun SupportingTile(scored: ScoredGarment, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RadiusMd)
            .background(Nocturne.Surface)
            .border(1.dp, Nocturne.Neutral800, RadiusMd)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SUPPORTING_IMAGE_HEIGHT_DP.dp)
                .background(scored.garment.color.toComposeColor())
        ) {
            scored.garment.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = scored.garment.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = scored.garment.label,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = scored.garment.category.name.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Nocturne.Neutral600
            )
        }
    }
}

@Composable
private fun ErrorNote(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RadiusMd)
            .background(Nocturne.Accent900)
            .border(1.dp, Nocturne.Accent700, RadiusMd)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Nocturne.Accent200
        )
        TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "Dismiss",
                style = MaterialTheme.typography.labelMedium,
                color = Nocturne.Accent300
            )
        }
    }
}

/**
 * The disc beside the undertone, built from the reading rather than picked by hand.
 *
 * Fitzpatrick sets lightness and undertone leans the hue, so the swatch actually moves
 * when the reading does. YouCam does return a measured `skin_color` hex, but the app's
 * contract carries the undertone enum rather than the raw colour — reconstructing it
 * here keeps that boundary intact while still showing something truthful.
 */
private fun SkinReading.swatchBrush(): Brush {
    val lightness = when (fitzpatrick) {
        1 -> 85f
        2 -> 76f
        3 -> 66f
        4 -> 55f
        5 -> 41f
        else -> 29f
    }
    val (a, b) = when (undertone) {
        Undertone.WARM -> 10f to 24f
        Undertone.COOL -> 12f to 10f
        Undertone.NEUTRAL -> 11f to 17f
    }

    val base = LabColor(lightness, a, b).toComposeColor()
    val deep = LabColor(lightness - 12f, a + 2f, b + 2f).toComposeColor()
    return Brush.linearGradient(listOf(base, deep))
}

private fun SkinReading.fitzpatrickNumeral(): String = when (fitzpatrick) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    else -> "VI"
}

private const val HERO_IMAGE_HEIGHT_DP = 196
private const val SUPPORTING_IMAGE_HEIGHT_DP = 84
