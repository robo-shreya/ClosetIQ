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
import com.closetiq.android.ui.components.CategoryPicker
import com.closetiq.android.ui.components.DashedPanel
import com.closetiq.android.ui.components.Footnote
import com.closetiq.android.ui.components.Kicker
import com.closetiq.android.ui.components.RadiusMd
import com.closetiq.android.ui.theme.Nocturne
import java.io.File

private const val PHOTO_PANEL_HEIGHT_DP = 210

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
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Kicker("Category")
            CategoryPicker(
                selected = state.category,
                onSelect = viewModel::onCategoryChange
            )
        }

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
