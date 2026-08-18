package com.closetiq.android.ui.closet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.ui.components.Kicker
import com.closetiq.android.ui.components.NocturneSpinner
import com.closetiq.android.ui.components.RadiusLg
import com.closetiq.android.ui.components.RadiusMd
import com.closetiq.android.ui.components.toComposeColor
import com.closetiq.android.ui.theme.Nocturne
import java.io.File

/** Big enough to judge a garment on, small enough to still read as an overlay. */
private const val SHEET_IMAGE_HEIGHT_DP = 210

/** A render is a whole person rather than a folded jumper, so it gets more room. */
private const val RENDER_IMAGE_HEIGHT_DP = 300

/** Past this the sheet scrolls instead of growing into a full screen. */
private const val SHEET_MAX_HEIGHT_DP = 620

/**
 * One garment, opened from the closet grid.
 *
 * Deliberately an overlay rather than a screen of its own. Everything here is an edit to
 * something the user is already looking at — its name, its photograph, how it looks on
 * them — and the grid behind it is the context for all three. A pushed route would have
 * taken that away and needed a back journey to get it back.
 */
@Composable
fun GarmentSheet(
    garment: Garment,
    sheet: GarmentSheetState,
    onDismiss: () -> Unit,
    onLabelChange: (String) -> Unit,
    onRename: () -> Unit,
    onGarmentPhotoPicked: (android.net.Uri) -> Unit,
    onPersonPhotoPicked: (PhotoSlot, android.net.Uri) -> Unit,
    onTryOn: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDismissError: () -> Unit
) {
    val garmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onGarmentPhotoPicked) }

    // The slot this garment would render onto, so the prompt asks for the one photo that
    // would unblock it rather than for "a photo".
    val target = garment.category.renderTarget
    val neededSlot = if (sheet.photos.bestFor(target) == null) {
        sheet.photos.preferredSlotFor(target)
    } else {
        null
    }
    val personPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && neededSlot != null) onPersonPhotoPicked(neededSlot, uri)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = SHEET_MAX_HEIGHT_DP.dp)
                .clip(RadiusLg)
                .background(Nocturne.Surface)
                .border(1.dp, Nocturne.Neutral800, RadiusLg)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SheetHeader(garment = garment, onDismiss = onDismiss)

            // The render takes the frame when there is one: it is the newer, more specific
            // answer to "what does this look like", and showing both would have made the
            // sheet twice as tall to say one thing twice.
            val renderUrl = sheet.renderUrl
            if (renderUrl != null) {
                AsyncImage(
                    model = renderUrl,
                    contentDescription = "Try-on render",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RENDER_IMAGE_HEIGHT_DP.dp)
                        .clip(RadiusMd)
                        .border(1.dp, Nocturne.Accent800, RadiusMd),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SHEET_IMAGE_HEIGHT_DP.dp)
                        .clip(RadiusMd)
                        .background(garment.color.toComposeColor())
                        .border(1.dp, Nocturne.Neutral800, RadiusMd),
                    contentAlignment = Alignment.Center
                ) {
                    garment.imagePath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = garment.label,
                            modifier = Modifier.fillMaxWidth().height(SHEET_IMAGE_HEIGHT_DP.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (sheet.importingPhoto) NocturneSpinner(size = 20.dp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Kicker("Name")
                OutlinedTextField(
                    value = sheet.draftLabel,
                    onValueChange = onLabelChange,
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
            }

            val renamed = sheet.draftLabel.trim() != garment.label &&
                sheet.draftLabel.isNotBlank()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { garmentPicker.launch("image/*") },
                    enabled = !sheet.importingPhoto && !sheet.rendering,
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                ) {
                    Text(
                        text = if (sheet.importingPhoto) "Reading…" else "Change photo",
                        color = Nocturne.Text
                    )
                }
                OutlinedButton(
                    onClick = onRename,
                    // Enabled only once the name actually differs, so the button says
                    // whether there is anything to save rather than always looking live.
                    enabled = renamed && !sheet.savingLabel,
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                ) {
                    Text(
                        text = if (sheet.savingLabel) "Saving…" else "Save name",
                        color = if (renamed) Nocturne.Text else Nocturne.Neutral600
                    )
                }
            }

            TryOnRow(
                sheet = sheet,
                neededSlot = neededSlot,
                onTryOn = onTryOn,
                onAttachPhoto = { personPicker.launch("image/*") }
            )

            DeleteRow(
                confirming = sheet.confirmingDelete,
                onRequest = onDeleteRequested,
                onCancel = onDeleteCancelled,
                onConfirm = onDeleteConfirmed
            )

            sheet.error?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Nocturne.Accent300,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(garment: Garment, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = garment.label,
                style = MaterialTheme.typography.titleMedium,
                color = Nocturne.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${garment.category.name.lowercase()} · ${garment.lastWornLabel()}",
                style = MaterialTheme.typography.labelMedium,
                color = Nocturne.Neutral600
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(1.dp, Nocturne.Neutral800, CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleMedium,
                color = Nocturne.Neutral400
            )
        }
    }
}

/**
 * Delete, behind one confirmation.
 *
 * Sits apart from the other actions and stays quiet until asked: everything else in this
 * sheet is reversible by doing it again, and this is the one control that is not. The
 * confirmation is inline rather than a second dialog stacked over the first, which on a
 * phone reads as the app having lost its place.
 */
@Composable
private fun DeleteRow(
    confirming: Boolean,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!confirming) {
        TextButton(
            onClick = onRequest,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "Delete from closet",
                style = MaterialTheme.typography.labelMedium,
                color = Nocturne.Neutral500
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Delete this for good? The photograph goes with it.",
            style = MaterialTheme.typography.bodySmall,
            color = Nocturne.Accent200
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("Keep it", color = Nocturne.Text)
            }
            OutlinedButton(
                onClick = onConfirm,
                border = BorderStroke(1.dp, Nocturne.Accent700),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("Delete", color = Nocturne.Accent200)
            }
        }
    }
}

/** "See it on me", or the one photo that would make it possible. */
@Composable
private fun TryOnRow(
    sheet: GarmentSheetState,
    neededSlot: PhotoSlot?,
    onTryOn: () -> Unit,
    onAttachPhoto: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sheet.rendering) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RadiusMd)
                    .border(1.dp, Nocturne.Neutral800, RadiusMd)
                    .padding(14.dp),
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

        // `cloth` can report success with no image when it cannot use the photos it was
        // given, so the note is what turns a blank frame into an explanation.
        sheet.renderNote?.let { note ->
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
                enabled = !sheet.importingPhoto,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Text("Add that photo now", color = Nocturne.Text)
            }
        } else {
            OutlinedButton(
                onClick = onTryOn,
                enabled = !sheet.rendering && !sheet.importingPhoto,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Text(
                    text = when {
                        sheet.rendering -> "Rendering…"
                        sheet.renderUrl != null -> "Render again"
                        else -> "See it on me"
                    },
                    color = Nocturne.Text
                )
            }
        }
    }
}

private fun Garment.lastWornLabel(): String = lastWornAt?.let {
    val days = (System.currentTimeMillis() - it) / MILLIS_PER_DAY
    "worn $days days ago"
} ?: "never worn"

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
