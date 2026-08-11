package com.closetiq.android.ui.closet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetiq.android.AppContainer
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.repository.WardrobeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClosetViewModel(
    private val wardrobe: WardrobeRepository
) : ViewModel() {

    val garments: StateFlow<List<Garment>> = wardrobe.observeAllGarments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _utilisation = MutableStateFlow(0f)
    val utilisation: StateFlow<Float> = _utilisation.asStateFlow()

    init {
        refreshUtilisation()
    }

    fun refreshUtilisation() {
        viewModelScope.launch {
            _utilisation.value = wardrobe.utilisation()
        }
    }

    fun onWore(garmentId: String) {
        viewModelScope.launch {
            wardrobe.logWear(garmentId)
            refreshUtilisation()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ClosetViewModel(container.wardrobeRepository) }
        }
    }
}
