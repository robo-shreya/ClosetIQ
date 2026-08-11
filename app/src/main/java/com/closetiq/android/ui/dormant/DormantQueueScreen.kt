package com.closetiq.android.ui.dormant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.repository.WardrobeRepository
import com.closetiq.android.domain.usecase.RankDormantUseCase
import com.closetiq.android.ui.components.GarmentTile
import com.closetiq.android.ui.components.RadiusMd
import com.closetiq.android.ui.theme.Nocturne
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Past this, an item reads as genuinely forgotten and its label takes the accent. */
private const val OVERDUE_DAYS = 60

class DormantViewModel(
    wardrobe: WardrobeRepository,
    private val rankDormant: RankDormantUseCase
) : ViewModel() {

    /**
     * Ranking runs on every emission from Room. It is arithmetic over a list of twenty —
     * there is no reason to cache it, and recomputing keeps it correct for free.
     */
    val dormant: StateFlow<List<Garment>> = wardrobe.observeActiveGarments()
        .map { garments -> rankDormant.rank(garments, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun daysSince(garment: Garment): Long? = garment.lastWornAt?.let {
        (System.currentTimeMillis() - it) / RankDormantUseCase.MILLIS_PER_DAY
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                DormantViewModel(container.wardrobeRepository, container.rankDormant)
            }
        }
    }
}

@Composable
fun DormantQueueScreen(
    container: AppContainer,
    viewModel: DormantViewModel = viewModel(factory = DormantViewModel.factory(container))
) {
    val dormant by viewModel.dormant.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            RankNote(modifier = Modifier.padding(bottom = 6.dp))
        }

        items(dormant, key = { it.id }) { garment ->
            val days = viewModel.daysSince(garment)
            val overdue = days == null || days > OVERDUE_DAYS

            GarmentTile(
                garment = garment,
                subtitle = if (days == null) "never worn" else "$days days ago",
                subtitleColor = if (overdue) Nocturne.Accent300 else Nocturne.Neutral600
            )
        }
    }
}

@Composable
private fun RankNote(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RadiusMd)
            .background(Nocturne.Surface)
            .border(1.dp, Nocturne.Accent800, RadiusMd)
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Text(
            text = "Ranked most-neglected first. Anything past $OVERDUE_DAYS days is marked.",
            style = MaterialTheme.typography.labelMedium,
            color = Nocturne.Accent200
        )
    }
}
