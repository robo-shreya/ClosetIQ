package com.closetiq.android.ui.mirror

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.model.OutfitPick
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.ui.components.GarmentTile
import kotlin.math.roundToInt

/**
 * The centrepiece. Everything in the demo video happens on this screen.
 *
 * Reading order, deliberately: today's skin state, then the item it surfaced, then why,
 * then the render, then "wore it". The reason line sits above the render because the
 * argument has to land before the picture does.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("The mirror", style = MaterialTheme.typography.headlineMedium)

        SkinStateCard(
            reading = state.reading,
            analysing = state.analysing,
            onCheckMirror = { photoPicker.launch("image/*") }
        )

        state.error?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                }
            }
        }

        when {
            state.loading -> CircularProgressIndicator()
            state.pick != null -> PickCard(
                pick = state.pick!!,
                rendering = state.rendering,
                renderUrl = state.renderUrl,
                renderNote = state.renderNote,
                onRender = viewModel::onRenderHero,
                onWoreIt = viewModel::onWoreIt
            )
            else -> Text(
                "No pick yet. Once the scoring functions are written this fills in.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SkinStateCard(
    reading: SkinReading?,
    analysing: Boolean,
    onCheckMirror: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (reading == null) {
                Text("No recent reading", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The closet works without one. A photo just makes today's pick sharper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Today's skin", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${reading.undertone.name.lowercase()} undertone · " +
                        "type ${reading.fitzpatrick}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric("redness", reading.redness)
                    Metric("dullness", reading.dullness)
                    Metric("circles", reading.darkCircles)
                }
            }

            if (analysing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Reading…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                OutlinedButton(onClick = onCheckMirror) {
                    Text(if (reading == null) "Check the mirror" else "Update")
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Float) {
    Column {
        Text(
            text = "${(value * 100).roundToInt()}",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PickCard(
    pick: OutfitPick,
    rendering: Boolean,
    renderUrl: String?,
    renderNote: String?,
    onRender: () -> Unit,
    onWoreIt: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Today's rescue", style = MaterialTheme.typography.titleSmall)

            GarmentTile(
                garment = pick.hero.garment,
                modifier = Modifier.fillMaxWidth(0.5f),
                subtitle = "score ${"%.2f".format(pick.hero.total)}"
            )

            // The reason line. This is the sentence the whole product exists to produce.
            Text(pick.reason, style = MaterialTheme.typography.bodyLarge)

            if (pick.supporting.isNotEmpty()) {
                Text(
                    "Wear it with",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pick.supporting.forEach { scored ->
                        GarmentTile(
                            garment = scored.garment,
                            modifier = Modifier.fillMaxWidth(0.4f)
                        )
                    }
                }
            }

            renderUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Try-on render",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }

            // The Gemma mock usually returns commentary instead of an image. Showing it
            // means the mock path is demonstrably working end to end before YouCam is live.
            renderNote?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRender, enabled = !rendering) {
                    Text(if (rendering) "Rendering…" else "See it on me")
                }
                OutlinedButton(onClick = onWoreIt) { Text("Wore it") }
            }
        }
    }
}
