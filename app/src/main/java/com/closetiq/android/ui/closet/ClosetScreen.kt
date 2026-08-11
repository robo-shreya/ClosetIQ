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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closetiq.android.AppContainer
import com.closetiq.android.ui.components.AccentBar
import com.closetiq.android.ui.components.GarmentTile
import com.closetiq.android.ui.theme.Nocturne
import kotlin.math.roundToInt

/** Clears the floating action button so it never sits on top of the last row. */
private val GridBottomInset = 96.dp

@Composable
fun ClosetScreen(
    container: AppContainer,
    onAddItem: () -> Unit,
    viewModel: ClosetViewModel = viewModel(factory = ClosetViewModel.factory(container))
) {
    val garments by viewModel.garments.collectAsStateWithLifecycle()
    val utilisation by viewModel.utilisation.collectAsStateWithLifecycle()

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
                UtilisationHeader(utilisation = utilisation, total = garments.size)
            }

            items(garments, key = { it.id }) { garment ->
                GarmentTile(garment = garment)
            }
        }

        AddItemButton(
            onClick = onAddItem,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        )
    }
}

/**
 * The headline number, and the only thing on screen that goes up when you wear something
 * you already own.
 */
@Composable
private fun UtilisationHeader(utilisation: Float, total: Int) {
    // utilisation is wornCount / total, so multiplying back recovers the count exactly.
    val wornCount = (utilisation * total).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${(utilisation * 100).roundToInt()}%",
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

        AccentBar(fraction = utilisation, height = 6)

        Text(
            text = "$wornCount of $total items worn in the last 90 days",
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
