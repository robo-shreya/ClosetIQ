package com.closetiq.android.ui.closet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closetiq.android.AppContainer
import com.closetiq.android.ui.components.GarmentTile
import kotlin.math.roundToInt

@Composable
fun ClosetScreen(
    container: AppContainer,
    onAddItem: () -> Unit,
    viewModel: ClosetViewModel = viewModel(factory = ClosetViewModel.factory(container))
) {
    val garments by viewModel.garments.collectAsStateWithLifecycle()
    val utilisation by viewModel.utilisation.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add item", modifier = Modifier.padding(start = 8.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            UtilisationHeader(utilisation = utilisation, itemCount = garments.size)

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(garments, key = { it.id }) { garment ->
                    GarmentTile(garment = garment)
                }
            }
        }
    }
}

/**
 * The headline number. It exists to go up — it is the only thing on screen that turns
 * "I wore something I already owned" into visible progress.
 */
@Composable
private fun UtilisationHeader(utilisation: Float, itemCount: Int) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "${(utilisation * 100).roundToInt()}% worn this season",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "$itemCount items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            progress = { utilisation.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}
