package com.closetiq.android.ui.buycheck

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetiq.android.AppContainer
import com.closetiq.android.data.image.ColorExtractor
import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.domain.engine.PaletteEngine
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.domain.repository.SkinRepository
import com.closetiq.android.domain.repository.WardrobeRepository
import com.closetiq.android.domain.usecase.CheckDuplicateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BuyCheckUiState(
    val photoPath: String? = null,
    val candidateColor: LabColor? = null,
    val category: Category = Category.TOP,
    val importing: Boolean = false,
    val checking: Boolean = false,
    val verdict: CheckDuplicateUseCase.Verdict? = null,
    val error: String? = null
) {
    val canCheck: Boolean get() = candidateColor != null && !importing && !checking
}

class BuyCheckViewModel(
    private val wardrobe: WardrobeRepository,
    private val skin: SkinRepository,
    private val checkDuplicate: CheckDuplicateUseCase,
    private val imageStore: ImageStore,
    private val colorExtractor: ColorExtractor
) : ViewModel() {

    private val _state = MutableStateFlow(BuyCheckUiState())
    val state = _state.asStateFlow()

    /**
     * Colour is extracted the moment the photo lands rather than when the button is
     * tapped, so the verdict appears instantly — the whole point of a check you run
     * while standing in a shop.
     */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            // Nothing here is kept. Without this, checking five things in a shop leaves
            // five orphaned files in app storage.
            _state.value.photoPath?.let(imageStore::delete)

            _state.update {
                it.copy(importing = true, error = null, verdict = null, photoPath = null)
            }

            runCatching {
                val path = imageStore.importFromUri(uri)
                val bitmap = requireNotNull(imageStore.load(path)) { "Could not read the photo" }
                path to colorExtractor.fromBitmapCentreCrop(bitmap)
            }.onSuccess { (path, color) ->
                _state.update {
                    it.copy(importing = false, photoPath = path, candidateColor = color)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(importing = false, error = error.message ?: "Could not read the photo")
                }
            }
        }
    }

    fun onCategoryChange(category: Category) =
        _state.update { it.copy(category = category, verdict = null) }

    fun onCheck() {
        val color = _state.value.candidateColor ?: return

        viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null) }

            runCatching {
                // A purchase is a long-term decision, so only the stable half of the
                // reading is consulted — undertone and Fitzpatrick, never today's redness.
                val reading = skin.currentFreshReading() ?: SkinReading.NEUTRAL
                val palette = PaletteEngine.buildPalette(reading)
                val closet = wardrobe.observeActiveGarments().first()

                checkDuplicate(closet, color, _state.value.category, palette)
            }.onSuccess { verdict ->
                _state.update { it.copy(checking = false, verdict = verdict) }
            }.onFailure { error ->
                _state.update {
                    it.copy(checking = false, error = error.message ?: "Could not run the check")
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                BuyCheckViewModel(
                    wardrobe = container.wardrobeRepository,
                    skin = container.skinRepository,
                    checkDuplicate = container.checkDuplicate,
                    imageStore = container.imageStore,
                    colorExtractor = container.colorExtractor
                )
            }
        }
    }
}
