package com.closetiq.android.ui.closet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.repository.Utilisation
import com.closetiq.android.ui.components.AccentBar
import com.closetiq.android.ui.components.GarmentTile
import com.closetiq.android.ui.theme.Nocturne
import kotlin.math.roundToInt

/** Clears the floating action button so it never sits on top of the last row. */
private val GridBottomInset = 96.dp

/** Past this, an item reads as genuinely forgotten and its label takes the accent. */
private const val OVERDUE_DAYS = 60

@Composable
fun ClosetScreen(
    container: AppContainer,
    onAddItem: () -> Unit,
    viewModel: ClosetViewModel = viewModel(factory = ClosetViewModel.factory(container))
) {
    val garments by viewModel.garments.collectAsStateWithLifecycle()
    val utilisation by viewModel.utilisation.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = GridBottomInset
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                UtilisationHeader(utilisation = utilisation)
            }

            // The closet ships empty, so the first thing anyone sees here is nothing at
            // all. Saying where the button is beats an empty grid that looks broken.
            if (garments.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Nothing in your closet yet. Photograph something with the " +
                            "+ button and it will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Nocturne.Neutral600
                    )
                }
            }

            items(garments, key = { it.id }) { garment ->
                val days = viewModel.daysSince(garment)
                val overdue = days == null || days > OVERDUE_DAYS

                GarmentTile(
                    garment = garment,
                    subtitle = if (days == null) "never worn" else "$days days ago",
                    subtitleColor = if (overdue) Nocturne.Accent300 else Nocturne.Neutral600,
                    onClick = { viewModel.onGarmentOpened(garment) }
                )
            }
        }

        AddItemButton(
            onClick = onAddItem,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        )
    }

    // Resolved against the live list rather than held in the sheet state, so a rename or a
    // new photograph lands in the open sheet as soon as Room emits it. A garment that
    // disappears from the closet while open closes the sheet with it.
    sheet?.let { open ->
        val garment = garments.firstOrNull { it.id == open.garmentId }

        if (garment == null) {
            // Closing is a side effect, so it runs after composition rather than during it.
            LaunchedEffect(open.garmentId) { viewModel.onSheetDismissed() }
        } else {
            GarmentSheet(
                garment = garment,
                sheet = open,
                onDismiss = viewModel::onSheetDismissed,
                onLabelChange = viewModel::onDraftLabelChange,
                onRename = viewModel::onRenameConfirmed,
                onGarmentPhotoPicked = viewModel::onGarmentPhotoPicked,
                onPersonPhotoPicked = viewModel::onPersonPhotoPicked,
                onTryOn = { viewModel.onTryOn(garment) },
                onDeleteRequested = viewModel::onDeleteRequested,
                onDeleteCancelled = viewModel::onDeleteCancelled,
                onDeleteConfirmed = viewModel::onDeleteConfirmed,
                onDismissError = viewModel::dismissSheetError
            )
        }
    }
}

/**
 * The headline number, and the only thing on screen that goes up when you wear something
 * you already own.
 */
@Composable
private fun UtilisationHeader(utilisation: Utilisation) {
    val fraction = utilisation.fraction

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.displaySmall,
                color = Nocturne.Text
            )
            Text(
                text = "worn this season",
                style = MaterialTheme.typography.bodyMedium,
                color = Nocturne.Neutral500,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }

        AccentBar(fraction = fraction, height = 6.dp)

        Text(
            // The ranking is stated here rather than in a banner of its own: the grid
            // below is sorted, and an unexplained order reads as no order at all.
            text = "${utilisation.wornCount} of ${utilisation.activeCount} " +
                "items worn in the last 90 days · most neglected first",
            style = MaterialTheme.typography.bodySmall,
            color = Nocturne.Neutral600
        )
    }
}

/**
 * The one place the accent carries a tinted fill — a 56dp mark, not a flood.
 * Drawn as a plain composable so the app needs no icon font at all.
 */
@Composable
private fun AddItemButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Nocturne.Accent900)
            .border(1.dp, Nocturne.Accent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            style = MaterialTheme.typography.headlineMedium,
            color = Nocturne.Accent200
        )
    }
}
