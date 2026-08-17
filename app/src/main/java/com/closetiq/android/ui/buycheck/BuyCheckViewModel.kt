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
import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.domain.repository.ProfileRepository
import com.closetiq.android.domain.repository.SkinRepository
import com.closetiq.android.domain.repository.TryOnRepository
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
    /** Every picture of the user on file, so the render can pick the right one. */
    val photos: PersonPhotos = PersonPhotos.EMPTY,
    val rendering: Boolean = false,
    val renderUrl: String? = null,
    val renderNote: String? = null,
    val error: String? = null
) {
    val canCheck: Boolean get() = candidateColor != null && !importing && !checking

    /**
     * The photo this render would use — an upper-body shot for a top, a lower-body one for
     * trousers. Null when nothing on file covers that region.
     */
    val personPhoto: String? get() = photos.bestFor(category.renderTarget)

    val canTryOn: Boolean
        get() = photoPath != null && personPhoto != null && !importing && !rendering

    /** The slot to ask for when the render is blocked for want of a picture of the user. */
    val neededSlot: PhotoSlot?
        get() = if (personPhoto == null) photos.preferredSlotFor(category.renderTarget) else null
}

class BuyCheckViewModel(
    private val wardrobe: WardrobeRepository,
    private val skin: SkinRepository,
    private val profile: ProfileRepository,
    private val tryOn: TryOnRepository,
    private val checkDuplicate: CheckDuplicateUseCase,
    private val imageStore: ImageStore,
    private val colorExtractor: ColorExtractor
) : ViewModel() {

    private val _state = MutableStateFlow(BuyCheckUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(photos = profile.photos()) }
        }
    }

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
                it.copy(
                    importing = true,
                    error = null,
                    verdict = null,
                    photoPath = null,
                    renderUrl = null,
                    renderNote = null
                )
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

    /**
     * A body shot, attached here rather than sending the user somewhere else for it. The
     * try-on prompt is the first time most people will be asked for one.
     */
    fun onPersonPhotoPicked(slot: PhotoSlot, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }

            runCatching { imageStore.importFromUri(uri) }
                .onSuccess { path ->
                    profile.setPhoto(slot, path)
                    _state.update {
                        it.copy(importing = false, photos = it.photos.with(slot, path))
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            importing = false,
                            error = error.message ?: "Could not read that photo"
                        )
                    }
                }
        }
    }

    // A render of a top is not a render of a pair of trousers, so switching category drops
    // it along with the verdict.
    fun onCategoryChange(category: Category) =
        _state.update {
            it.copy(category = category, verdict = null, renderUrl = null, renderNote = null)
        }

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

    /**
     * One call, one credit, only when asked.
     *
     * The shop photo is the garment and the stored body shot is the person, which is the
     * same pairing the Mirror renders — the difference is that nothing here is in the
     * closet, so this goes straight to [TryOnRepository] instead of through the chained
     * outfit strategy. There is only ever one garment to put on.
     */
    fun onTryOn() {
        val current = _state.value
        val garmentPath = current.photoPath ?: return

        val person = current.personPhoto ?: run {
            _state.update {
                it.copy(error = "Add a photo of yourself first — this renders onto it.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(rendering = true, error = null, renderNote = null) }

            tryOn.render(
                personImagePath = person,
                garmentImagePath = garmentPath,
                target = current.category.renderTarget
            )
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            rendering = false,
                            renderUrl = result.imageUrl,
                            renderNote = result.note
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            rendering = false,
                            error = error.message ?: "Could not render the try-on"
                        )
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
                    profile = container.profileRepository,
                    tryOn = container.tryOnRepository,
                    checkDuplicate = container.checkDuplicate,
                    imageStore = container.imageStore,
                    colorExtractor = container.colorExtractor
                )
            }
        }
    }
}
