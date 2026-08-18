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
import com.closetiq.android.domain.model.PhotoSlot
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
 *
 * The selfie now comes first and nothing is suggested without one. Scoring does still work
 * without a reading, but a pick offered before the app has looked at the user reads as a
 * guess — and it left the skin card sitting above it as an ignorable option. Asking here is
 * also the only place the selfie is asked for at all, now that onboarding is gone.
 */
@Composable
fun MirrorScreen(
    container: AppContainer,
    viewModel: MirrorViewModel = viewModel(factory = MirrorViewModel.factory(container))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The card above reads skin, so its picker always means "a new selfie". Body shots are
    // attached from the prompt below, or from the add-item screen.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onPhotoPicked(PhotoSlot.SELFIE, it) } }

    // Bound to whatever the hero actually needs, so the blocked message below is
    // something the user can act on here rather than a dead end.
    val neededSlot = state.heroNeededSlot
    val bodyPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && neededSlot != null) viewModel.onPhotoPicked(neededSlot, uri)
    }

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
            selfiePath = state.photos.selfie,
            analysing = state.analysing,
            onCheckMirror = { photoPicker.launch("image/*") },
            onRetake = { photoPicker.launch("image/*") }
        )

        state.error?.let { message ->
            ErrorNote(message = message, onDismiss = viewModel::dismissError)
        }

        // No selfie, no suggestion. The gate is the selfie rather than the reading itself,
        // so a failed Skin Analysis call still leaves a usable app: the photo is on file,
        // scoring runs locally, and the pick is simply not sharpened by a reading.
        if (state.photos.selfie != null) {
            SectionLabel("The pick")

            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) { NocturneSpinner(size = 20.dp) }

                pick != null -> PickSection(
                    pick = pick,
                    canRender = state.heroCanRender,
                    blockedReason = when {
                        state.heroIsSwatch ->
                            "This one is a colour swatch, so there's nothing to render. " +
                                "Photograph it to try it on."
                        // Naming the missing shot matters: `cloth` reports a render it could
                        // not do as success with no image, so a vague "add a photo" would let
                        // the user spend a credit on a guaranteed blank.
                        state.heroNeededSlot != null -> when (state.heroNeededSlot) {
                            PhotoSlot.LOWER_BODY ->
                                "Add a lower-body photo of yourself — trousers need your legs " +
                                    "in frame to render."
                            PhotoSlot.UPPER_BODY ->
                                "Add an upper-body photo of yourself and try-on will render " +
                                    "onto it."
                            else ->
                                "Add a full-body photo of yourself — this one needs your whole " +
                                    "figure in frame."
                        }
                        else -> null
                    },
                    rendering = state.rendering,
                    renderPass = state.renderPass,
                    renderUrl = state.renderUrl,
                    renderNote = state.renderNote,
                    onRender = viewModel::onRenderHero,
                    onWoreIt = viewModel::onWoreIt,
                    onAttachPhoto = neededSlot?.let { { bodyPhotoPicker.launch("image/*") } }
                )

                else -> DashedPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nothing to show yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = Nocturne.Neutral300
                    )
                    Text(
                        text = "Photograph something you own and it will start picking " +
                            "from it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Nocturne.Neutral600
                    )
                }
            }
        }
    }
}

/**
 * Today's skin: the selfie the app read, and what it read from it, side by side.
 *
 * The two belong in one row because they are one fact — this picture produced this reading.
 * Stacking them would have pushed the pick below the fold, and the whole point of the pick
 * is that it is visible without scrolling, so the reading is laid out beside the photo at
 * the height the photo already needed.
 */
@Composable
private fun SkinCard(
    reading: SkinReading?,
    selfiePath: String?,
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
                text = if (reading != null) "YouCam · 1 call" else "start here",
                style = MaterialTheme.typography.labelSmall,
                color = Nocturne.Accent300
            )
        }

        if (selfiePath == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Upload a selfie to begin. It reads your undertone and skin " +
                        "type, and today's pick is built from it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Nocturne.Neutral300
                )
                OutlinedButton(
                    onClick = onCheckMirror,
                    enabled = !analysing
                ) {
                    Text(if (analysing) "Reading…" else "Upload a selfie")
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = File(selfiePath),
                        contentDescription = "Your selfie",
                        modifier = Modifier
                            .size(SELFIE_PREVIEW_DP.dp)
                            .clip(RadiusMd)
                            .border(1.dp, Nocturne.Neutral800, RadiusMd),
                        contentScale = ContentScale.Crop
                    )

                    if (reading == null) {
                        // The photo is on file but the reading is not. Say which of the two
                        // is missing, so a failed call does not read as a failed upload.
                        Text(
                            text = if (analysing) {
                                "Reading your skin…"
                            } else {
                                "Selfie saved, but the skin reading didn't come back. " +
                                    "The pick below still works — it just isn't tuned to " +
                                    "your colouring."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Nocturne.Neutral400,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // The reconstructed swatch stays even beside the real photo:
                                // it is the measured skin colour, not a thumbnail of it.
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(reading.swatchBrush())
                                        .border(1.dp, Nocturne.Neutral800, CircleShape)
                                )
                                Column {
                                    Text(
                                        text = "${reading.undertone.name.lowercase()
                                            .replaceFirstChar { it.uppercase() }} undertone",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Nocturne.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Fitzpatrick ${reading.fitzpatrickNumeral()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Nocturne.Neutral500
                                    )
                                }
                            }

                            MetricBar("Redness", (reading.redness * 100).roundToInt())
                            MetricBar("Dullness", (reading.dullness * 100).roundToInt())
                            MetricBar("Dark circles", (reading.darkCircles * 100).roundToInt())
                        }
                    }
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
    blockedReason: String?,
    rendering: Boolean,
    renderPass: Pair<Int, Int>?,
    renderUrl: String?,
    renderNote: String?,
    onRender: () -> Unit,
    onWoreIt: () -> Unit,
    /** Null unless the render is blocked for want of a photo the user could attach now. */
    onAttachPhoto: (() -> Unit)? = null
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
                    // A chained outfit is three calls and roughly thirty seconds, so the
                    // spinner has to say where it has got to.
                    text = renderPass?.let { (pass, total) ->
                        if (total > 1) "Layer $pass of $total…" else "One VTO render…"
                    } ?: "One VTO render…",
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

        blockedReason?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral600
            )
        }

        // `cloth` can report task_status "success" with an empty results object rather
        // than an error when it can't use the photos it was given. Showing note here is
        // what turns that into an explanation instead of a silent blank frame.
        renderNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral600
            )
        }

        onAttachPhoto?.let { attach ->
            TextButton(onClick = attach) {
                Text(
                    text = "Add that photo now",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nocturne.Accent200
                )
            }
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

/**
 * Square, and sized to the three metric bars beside it rather than to the photo itself —
 * the row is only as tall as the reading needs, so the card is no taller than it was.
 */
private const val SELFIE_PREVIEW_DP = 116
