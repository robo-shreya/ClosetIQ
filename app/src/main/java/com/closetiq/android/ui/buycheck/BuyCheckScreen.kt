package com.closetiq.android.ui.buycheck

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.usecase.CheckDuplicateUseCase
import com.closetiq.android.domain.usecase.CheckDuplicateUseCase.BuyAdvice
import com.closetiq.android.domain.usecase.CheckDuplicateUseCase.Verdict
import com.closetiq.android.domain.usecase.RankDormantUseCase
import com.closetiq.android.ui.components.CategoryPicker
import com.closetiq.android.ui.components.DashedPanel
import com.closetiq.android.ui.components.Footnote
import com.closetiq.android.ui.components.GarmentTile
import com.closetiq.android.ui.components.Kicker
import com.closetiq.android.ui.components.NocturneCard
import com.closetiq.android.ui.components.NocturneSpinner
import com.closetiq.android.ui.components.PipScale
import com.closetiq.android.ui.components.RadiusMd
import com.closetiq.android.ui.theme.Nocturne
import java.io.File

private const val PHOTO_PANEL_HEIGHT_DP = 210

/** Past this, a match is something forgotten rather than something in rotation. */
private const val DORMANT_DAYS = 60

/**
 * The buy check.
 *
 * Every other screen helps you wear what you own. This is the only one that argues
 * against acquiring more, which is why the discouraging verdict is the one that takes
 * the accent — the app should be loudest when it is telling you to stop.
 */
@Composable
fun BuyCheckScreen(
    container: AppContainer,
    viewModel: BuyCheckViewModel = viewModel(factory = BuyCheckViewModel.factory(container))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::onPhotoPicked) }

    // Bound to the slot the chosen category actually needs, so the prompt below asks for
    // the one photo that would unblock this render rather than for "a photo".
    val neededSlot = state.neededSlot
    val personPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && neededSlot != null) viewModel.onPersonPhotoPicked(neededSlot, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        val photoPath = state.photoPath

        if (photoPath == null) {
            DashedPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PHOTO_PANEL_HEIGHT_DP.dp)
            ) {
                Text(
                    text = "Photograph the thing you're about to buy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Nocturne.Neutral400
                )
                Text(
                    text = "Nothing is saved to your closet — this only compares.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nocturne.Neutral600
                )
            }
        } else {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "The item you're considering",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PHOTO_PANEL_HEIGHT_DP.dp)
                    .clip(RadiusMd)
                    .border(1.dp, Nocturne.Neutral800, RadiusMd),
                contentScale = ContentScale.Crop
            )
        }

        OutlinedButton(
            onClick = { picker.launch("image/*") },
            enabled = !state.importing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        ) {
            Text(
                text = when {
                    state.importing -> "Reading the colour…"
                    photoPath != null -> "Photo attached · replace"
                    else -> "Choose photo"
                },
                color = Nocturne.Text
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Kicker("What is it?")
            CategoryPicker(
                selected = state.category,
                onSelect = viewModel::onCategoryChange
            )
        }

        state.error?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Nocturne.Accent300
                )
                TextButton(onClick = viewModel::dismissError) {
                    Text("Dismiss", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Button(
            onClick = viewModel::onCheck,
            enabled = state.canCheck,
            shape = RadiusMd,
            colors = ButtonDefaults.buttonColors(
                containerColor = Nocturne.Accent900,
                contentColor = Nocturne.Accent200,
                disabledContainerColor = Nocturne.Neutral900,
                disabledContentColor = Nocturne.Neutral600
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(if (state.checking) "Checking…" else "Is it worth it?")
        }

        state.verdict?.let { verdict ->
            VerdictCard(verdict)
            MatchGrid(verdict.matches)
        }

        // Under the verdict, not above it: the argument about what you already own is the
        // point of the screen, and seeing the thing on yourself is what you do once you
        // have read it. Available before the check too — the photo is all it needs.
        if (photoPath != null) {
            TryOnSection(
                canTryOn = state.canTryOn,
                rendering = state.rendering,
                renderUrl = state.renderUrl,
                renderNote = state.renderNote,
                neededSlot = neededSlot,
                onTryOn = viewModel::onTryOn,
                onAttachPhoto = { personPicker.launch("image/*") }
            )
        }

        if (state.verdict == null) {
            Footnote(
                "Compares against the colours already in your closet. No API call, " +
                    "no photo leaves the device."
            )
        }
    }
}

/**
 * "See it on me" for something you do not own yet.
 *
 * One call rather than the Mirror's chained outfit: there is a single garment here, and it
 * is the only thing about the purchase still in question. The button stays a deliberate tap
 * because it spends a real YouCam credit — running it with every verdict would charge for a
 * render on every colour compared in a shop.
 */
@Composable
private fun TryOnSection(
    canTryOn: Boolean,
    rendering: Boolean,
    renderUrl: String?,
    renderNote: String?,
    /** Non-null when the render is blocked for want of a picture of the user. */
    neededSlot: PhotoSlot?,
    onTryOn: () -> Unit,
    onAttachPhoto: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("See it on you")

        if (rendering) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RadiusMd)
                    .border(1.dp, Nocturne.Neutral800, RadiusMd)
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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

        // `cloth` can report success with no image when it cannot use the photos it was
        // given, so the note is what turns a blank frame into an explanation.
        renderNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral600
            )
        }

        if (neededSlot != null) {
            Text(
                text = when (neededSlot) {
                    PhotoSlot.LOWER_BODY ->
                        "Add a lower-body photo of yourself — trousers need your legs in " +
                            "frame to render."
                    PhotoSlot.UPPER_BODY ->
                        "Add an upper-body photo of yourself and try-on will render onto it."
                    else ->
                        "Add a full-body photo of yourself — this one needs your whole " +
                            "figure in frame."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral600
            )
            OutlinedButton(
                onClick = onAttachPhoto,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Text("Add that photo now", color = Nocturne.Text)
            }
        } else {
            OutlinedButton(
                onClick = onTryOn,
                enabled = canTryOn,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Text(
                    text = when {
                        rendering -> "Rendering…"
                        renderUrl != null -> "Render again"
                        else -> "See it on me"
                    },
                    color = Nocturne.Text
                )
            }
        }
    }
}

@Composable
private fun VerdictCard(verdict: Verdict) {
    val discouraging = verdict.discouraging

    NocturneCard(
        modifier = Modifier.fillMaxWidth(),
        background = Brush.verticalGradient(
            if (discouraging) {
                listOf(Nocturne.Accent900, Nocturne.SkinCardBottom)
            } else {
                listOf(Nocturne.Surface, Nocturne.Surface)
            }
        ),
        borderColor = if (discouraging) Nocturne.Accent700 else Nocturne.Neutral800
    ) {
        Kicker(
            text = when (verdict.advice) {
                BuyAdvice.BUY -> "Go ahead"
                BuyAdvice.THINK_TWICE -> "Think twice"
                BuyAdvice.ALREADY_OWN -> "You own this"
            },
            color = if (discouraging) Nocturne.Accent300 else Nocturne.Neutral500,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Text(
            text = verdict.headline,
            style = MaterialTheme.typography.titleMedium,
            color = Nocturne.Text
        )

        verdict.detail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (discouraging) Nocturne.Accent200 else Nocturne.Neutral400,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Column(
            modifier = Modifier.padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Kicker("What that's based on", color = Nocturne.Neutral600)

            // The three things the verdict is actually computed from. A single opaque
            // number told nobody what it measured or what it was out of.
            Factor(
                label = "Suits your colouring",
                detail = "from your undertone and skin type",
                score = verdict.paletteOutOfFive,
                alarming = verdict.paletteFit < 0.5f
            )
            Factor(
                label = "Unlike anything you own",
                detail = if (verdict.matches.isEmpty()) {
                    "nothing this colour in this category"
                } else {
                    "${verdict.matches.size} similar already in your closet"
                },
                score = (CheckDuplicateUseCase.SCALE - verdict.matches.size).coerceAtLeast(0),
                alarming = verdict.matches.isNotEmpty()
            )
            // Dormancy is a count, not a score out of five. Forcing it onto the same
            // scale produced a low number beside a reassuring sentence, which read as a
            // contradiction. It carries more weight as a plain flagged fact anyway.
            if (verdict.dormantMatches > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Of those, unworn for months",
                        style = MaterialTheme.typography.labelMedium,
                        color = Nocturne.Accent200
                    )
                    Text(
                        text = "${verdict.dormantMatches}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Nocturne.Accent200
                    )
                }
            }
        }
    }
}

/** One decision input: what it measures, how it scored, and why. */
@Composable
private fun Factor(
    label: String,
    detail: String,
    score: Int,
    alarming: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Nocturne.Neutral300
            )
            Text(
                text = "$score/${CheckDuplicateUseCase.SCALE}",
                style = MaterialTheme.typography.labelMedium,
                color = if (alarming) Nocturne.Accent300 else Nocturne.Text
            )
        }
        PipScale(
            filled = score,
            total = CheckDuplicateUseCase.SCALE,
            filledColor = if (alarming) Nocturne.Accent300 else Nocturne.Accent
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = Nocturne.Neutral600
        )
    }
}

@Composable
private fun MatchGrid(matches: List<Garment>) {
    if (matches.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("Already in your closet")

        // A plain Column of Rows rather than a LazyVerticalGrid: this sits inside a
        // scrolling Column, and nesting a lazy grid in one is not allowed.
        matches.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { garment ->
                    GarmentTile(
                        garment = garment,
                        subtitle = garment.lastWornLabel(),
                        subtitleColor = if (garment.isForgotten()) {
                            Nocturne.Accent300
                        } else {
                            Nocturne.Neutral600
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keeps a short final row aligned with the columns above it.
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun Garment.daysSinceWorn(): Long? = lastWornAt?.let {
    (System.currentTimeMillis() - it) / RankDormantUseCase.MILLIS_PER_DAY
}

private fun Garment.lastWornLabel(): String =
    daysSinceWorn()?.let { "$it days ago" } ?: "never worn"

private fun Garment.isForgotten(): Boolean = (daysSinceWorn() ?: Long.MAX_VALUE) > DORMANT_DAYS
