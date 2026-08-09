package com.closetiq.android.ui.dormant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.repository.WardrobeRepository
import com.closetiq.android.domain.usecase.RankDormantUseCase
import com.closetiq.android.ui.components.GarmentTile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DormantViewModel(
    wardrobe: WardrobeRepository,
    private val rankDormant: RankDormantUseCase
) : ViewModel() {

    /**
     * Ranking runs on every emission from Room. It is arithmetic over a list of twenty —
     * there is no reason to cache it, and recomputing keeps it correct for free.
     */
    val dormant: StateFlow<List<Garment>> = wardrobe.observeActiveGarments()
        .map { garments ->
            runCatching { rankDormant.rank(garments, System.currentTimeMillis()) }
                .getOrDefault(garments)
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Forgotten",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(dormant, key = { it.id }) { garment ->
                val days = viewModel.daysSince(garment)
                GarmentTile(
                    garment = garment,
                    subtitle = when (days) {
                        null -> "never worn"
                        else -> "$days days ago"
                    }
                )
            }
        }
    }
}
