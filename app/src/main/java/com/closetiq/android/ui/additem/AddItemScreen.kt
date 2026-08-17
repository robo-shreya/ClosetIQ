package com.closetiq.android.ui.additem

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.ui.components.CategoryPicker
import com.closetiq.android.ui.components.DashedPanel
import com.closetiq.android.ui.components.Footnote
import com.closetiq.android.ui.components.Kicker
import com.closetiq.android.ui.components.RadiusMd
import com.closetiq.android.ui.theme.Nocturne
import java.io.File

private const val PHOTO_PANEL_HEIGHT_DP = 210

/** Taller than the garment panel — a render is a whole person, not a folded jumper. */
private const val RENDER_PANEL_HEIGHT_DP = 320

@Composable
fun AddItemScreen(
    container: AppContainer,
    onDone: () -> Unit,
    viewModel: AddItemViewModel = viewModel(factory = AddItemViewModel.factory(container))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::onImagePicked) }

    val personPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::onPersonPhotoPicked) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        val photoPath = state.localImagePath

        if (photoPath == null) {
            DashedPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PHOTO_PANEL_HEIGHT_DP.dp)
            ) {
                Text(
                    text = "Fill the frame with the garment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Nocturne.Neutral400
                )
                Text(
                    text = "Colour is read from the centre, and the photo is resized into " +
                        "YouCam's envelope on import",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nocturne.Neutral600
                )
            }
        } else {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "Selected garment",
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
                    state.importing -> "Importing…"
                    photoPath != null -> "Photo attached · replace"
                    else -> "Choose photo"
                },
                color = Nocturne.Text
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Kicker("Name")
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::onLabelChange,
                placeholder = {
                    Text("Olive field jacket", color = Nocturne.Neutral600)
                },
                singleLine = true,
                shape = RadiusMd,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Nocturne.Field,
                    unfocusedContainerColor = Nocturne.Field,
                    focusedBorderColor = Nocturne.Accent,
                    unfocusedBorderColor = Nocturne.Neutral800,
                    cursorColor = Nocturne.Accent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Only while the field still holds the app's guess. Once the user types, the
            // line would be claiming credit for their words.
            if (state.garmentColor != null && !state.labelEdited) {
                Text(
                    text = "Suggested from the colour in the photo. Overwrite it freely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nocturne.Neutral600
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Kicker("Category")
            CategoryPicker(
                selected = state.category,
                onSelect = viewModel::onCategoryChange
            )
        }

        SeeItOnMe(
            state = state,
            onRender = viewModel::onSeeItOnMe,
            onChoosePersonPhoto = { personPicker.launch("image/*") }
        )

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Accent300
            )
        }

        Button(
            onClick = { viewModel.onSave(onDone) },
            enabled = state.canSave,
            shape = RadiusMd,
            colors = ButtonDefaults.buttonColors(
                containerColor = Nocturne.Accent900,
                contentColor = Nocturne.Accent200,
                disabledContainerColor = Nocturne.Neutral900,
                disabledContentColor = Nocturne.Neutral600
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(if (state.saving) "Saving…" else "Save to closet")
        }

        Footnote(
            "Inserts immediately as PROCESSING, so the tile appears before any image " +
                "work finishes."
        )
    }
}

/**
 * "an upper-body photo" — carrying its own article, which is not the same for every slot.
 * Kept beside its possessive twin so the two cannot drift apart.
 */
private fun slotIndefinite(slot: PhotoSlot): String = when (slot) {
    PhotoSlot.LOWER_BODY -> "a lower-body photo"
    PhotoSlot.UPPER_BODY -> "an upper-body photo"
    PhotoSlot.FULL_BODY -> "a full-body photo"
    PhotoSlot.SELFIE -> "a selfie"
}

/** "your upper-body photo". */
private fun slotPossessive(slot: PhotoSlot): String = when (slot) {
    PhotoSlot.LOWER_BODY -> "your lower-body photo"
    PhotoSlot.UPPER_BODY -> "your upper-body photo"
    PhotoSlot.FULL_BODY -> "your full-body photo"
    PhotoSlot.SELFIE -> "your selfie"
}

/**
 * Try-on for the garment being added, before it is saved.
 *
 * Separate from the Mirror on purpose: the Mirror answers "what should I wear today",
 * while this answers "does this thing suit me at all" — a question you ask once, about a
 * garment that is not in the closet yet.
 *
 * Nothing here is persisted. The render is a preview for the person standing in the add
 * flow, which is also what keeps this off the expiring-URL problem entirely.
 */
@Composable
private fun SeeItOnMe(
    state: AddItemUiState,
    onRender: () -> Unit,
    onChoosePersonPhoto: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Kicker("See it on me")

        when {
            state.rendering -> DashedPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RENDER_PANEL_HEIGHT_DP.dp)
            ) {
                Text(
                    text = "Rendering…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Nocturne.Neutral400
                )
                Text(
                    text = "YouCam takes about nine seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nocturne.Neutral600
                )
            }

            state.renderUrl != null -> AsyncImage(
                model = state.renderUrl,
                contentDescription = "You wearing ${state.label}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RENDER_PANEL_HEIGHT_DP.dp)
                    .clip(RadiusMd)
                    .border(1.dp, Nocturne.Accent800, RadiusMd),
                contentScale = ContentScale.Fit
            )
        }

        state.renderNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral500
            )
        }

        OutlinedButton(
            onClick = onRender,
            enabled = state.canRender,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        ) {
            Text(
                text = when {
                    state.rendering -> "Rendering…"
                    state.renderUrl != null -> "Render again"
                    else -> "See it on me"
                },
                color = if (state.canRender) Nocturne.Text else Nocturne.Neutral600
            )
        }

        // The button disables itself rather than failing on tap, so it has to say why —
        // and which photo is missing, since a render onto the wrong body region comes back
        // blank while still reporting success and still costing a credit.
        Text(
            text = when {
                state.localImagePath == null ->
                    "Attach the garment photo first — there is nothing to put on yet."
                state.personPhoto == null ->
                    "This needs ${slotIndefinite(state.targetSlot)} of you. Choose one below " +
                        "and it's saved for next time."
                state.photoJustAttached ->
                    "Saved as ${slotPossessive(state.targetSlot)}. One YouCam call."
                // Deliberately does not name a slot. The resolved photo may be a fallback —
                // a full-body shot standing in for an upper-body one — and naming the
                // wrong picture would read as confidently as naming the right one.
                else -> "Renders onto your photo from setup. One YouCam call."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Nocturne.Neutral600
        )

        TextButton(onClick = onChoosePersonPhoto, enabled = !state.importing) {
            Text(
                // Names the slot, because tapping this now writes to the profile. A vague
                // "use a different photo" would hide which of the four it replaces.
                text = if (state.personPhoto == null) {
                    "Choose ${slotIndefinite(state.targetSlot)}"
                } else {
                    "Replace ${slotPossessive(state.targetSlot)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Accent200
            )
        }
    }
}
