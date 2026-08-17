package com.closetiq.android.ui.closet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.repository.Utilisation
import com.closetiq.android.domain.repository.WardrobeRepository
import com.closetiq.android.domain.usecase.RankDormantUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClosetViewModel(
    private val wardrobe: WardrobeRepository,
    private val rankDormant: RankDormantUseCase
) : ViewModel() {

    /**
     * The whole closet, most-forgotten first.
     *
     * There is no separate dormant screen any more: a closet that shows what you own in
     * database order buries the thing the app exists to surface, so the one grid is the
     * ranked one. Ranking runs on every emission from Room — it is arithmetic over a list
     * of twenty, and recomputing keeps it correct for free.
     */
    val garments: StateFlow<List<Garment>> = wardrobe.observeAllGarments()
        .map { garments -> rankDormant.rank(garments, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val utilisation: StateFlow<Utilisation> = wardrobe.observeUtilisation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Utilisation(0, 0))

    fun daysSince(garment: Garment): Long? = garment.lastWornAt?.let {
        (System.currentTimeMillis() - it) / RankDormantUseCase.MILLIS_PER_DAY
    }

    fun onWore(garmentId: String) {
        viewModelScope.launch { wardrobe.logWear(garmentId) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ClosetViewModel(container.wardrobeRepository, container.rankDormant)
            }
        }
    }
}
