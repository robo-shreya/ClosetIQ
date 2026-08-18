package com.closetiq.android.ui.closet

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetiq.android.AppContainer
import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.repository.ProfileRepository
import com.closetiq.android.domain.repository.TryOnRepository
import com.closetiq.android.domain.repository.Utilisation
import com.closetiq.android.domain.repository.WardrobeRepository
import com.closetiq.android.domain.usecase.RankDormantUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The open garment, if any.
 *
 * Only the id is held. The garment itself is read back out of the Room flow on every
 * emission, so a rename or a new photograph shows in the sheet the moment it lands in the
 * database — holding a copy of the garment here would have frozen the sheet on the values
 * it was opened with.
 */
data class GarmentSheetState(
    val garmentId: String,
    /** Every picture of the user on file, resolved per render target. */
    val photos: PersonPhotos = PersonPhotos.EMPTY,
    val draftLabel: String = "",
    val savingLabel: Boolean = false,
    val importingPhoto: Boolean = false,
    val rendering: Boolean = false,
    val renderUrl: String? = null,
    val renderNote: String? = null,
    /** True once delete has been asked for and is waiting to be confirmed. */
    val confirmingDelete: Boolean = false,
    val error: String? = null
)

class ClosetViewModel(
    private val wardrobe: WardrobeRepository,
    private val profile: ProfileRepository,
    private val tryOn: TryOnRepository,
    private val imageStore: ImageStore,
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

    private val _sheet = MutableStateFlow<GarmentSheetState?>(null)
    val sheet = _sheet.asStateFlow()

    fun daysSince(garment: Garment): Long? = garment.lastWornAt?.let {
        (System.currentTimeMillis() - it) / RankDormantUseCase.MILLIS_PER_DAY
    }

    fun onWore(garmentId: String) {
        viewModelScope.launch { wardrobe.logWear(garmentId) }
    }

    // ---- the garment sheet ----

    fun onGarmentOpened(garment: Garment) {
        _sheet.value = GarmentSheetState(garmentId = garment.id, draftLabel = garment.label)

        // Read after opening rather than before: the sheet should appear on the tap, not
        // one disk read later.
        viewModelScope.launch {
            val photos = profile.photos()
            _sheet.update { it?.copy(photos = photos) }
        }
    }

    fun onSheetDismissed() {
        _sheet.value = null
    }

    fun onDraftLabelChange(label: String) = _sheet.update { it?.copy(draftLabel = label) }

    /** Renames only when there is something to rename to — a blank name helps nobody. */
    fun onRenameConfirmed() {
        val current = _sheet.value ?: return
        val label = current.draftLabel.trim()
        if (label.isEmpty()) return

        viewModelScope.launch {
            _sheet.update { it?.copy(savingLabel = true, error = null) }
            runCatching { wardrobe.rename(current.garmentId, label) }
                .onSuccess { _sheet.update { s -> s?.copy(savingLabel = false) } }
                .onFailure { error ->
                    _sheet.update {
                        it?.copy(
                            savingLabel = false,
                            error = error.message ?: "Could not rename this"
                        )
                    }
                }
        }
    }

    /**
     * A new photograph of this garment. The old render is dropped with it — it shows the
     * previous picture, and leaving it up would claim the new one had been tried on.
     */
    fun onGarmentPhotoPicked(uri: Uri) {
        val current = _sheet.value ?: return

        viewModelScope.launch {
            _sheet.update { it?.copy(importingPhoto = true, error = null) }

            runCatching {
                val path = imageStore.importFromUri(uri)
                wardrobe.replacePhoto(current.garmentId, path)
            }.onSuccess {
                _sheet.update {
                    it?.copy(importingPhoto = false, renderUrl = null, renderNote = null)
                }
            }.onFailure { error ->
                _sheet.update {
                    it?.copy(
                        importingPhoto = false,
                        error = error.message ?: "Could not read that photo"
                    )
                }
            }
        }
    }

    /** A picture of the user, attached from the sheet when a render has nothing to sit on. */
    fun onPersonPhotoPicked(slot: PhotoSlot, uri: Uri) {
        viewModelScope.launch {
            _sheet.update { it?.copy(importingPhoto = true, error = null) }

            runCatching { imageStore.importFromUri(uri) }
                .onSuccess { path ->
                    profile.setPhoto(slot, path)
                    _sheet.update {
                        it?.copy(importingPhoto = false, photos = it.photos.with(slot, path))
                    }
                }
                .onFailure { error ->
                    _sheet.update {
                        it?.copy(
                            importingPhoto = false,
                            error = error.message ?: "Could not read that photo"
                        )
                    }
                }
        }
    }

    /**
     * One call, one credit, only when asked.
     *
     * A single garment onto a single body shot, so this goes straight to [TryOnRepository]
     * rather than through the Mirror's chained outfit strategy — there is nothing here to
     * chain, and a closet item is being looked at on its own.
     */
    fun onTryOn(garment: Garment) {
        val current = _sheet.value ?: return
        val target = garment.category.renderTarget

        val person = current.photos.bestFor(target) ?: run {
            _sheet.update {
                it?.copy(error = "Add a photo of yourself first — this renders onto it.")
            }
            return
        }

        // Seeded swatches have no picture, and `cloth` reports a render it could not do as
        // success with no image — so this is refused here rather than spending a credit.
        val garmentPath = garment.cutoutPath ?: garment.imagePath ?: run {
            _sheet.update {
                it?.copy(
                    error = "This one is a colour swatch, so there's nothing to render. " +
                        "Change the photo to try it on."
                )
            }
            return
        }

        viewModelScope.launch {
            _sheet.update { it?.copy(rendering = true, error = null, renderNote = null) }

            tryOn.render(
                personImagePath = person,
                garmentImagePath = garmentPath,
                target = target
            )
                .onSuccess { result ->
                    _sheet.update {
                        it?.copy(
                            rendering = false,
                            renderUrl = result.imageUrl,
                            renderNote = result.note
                        )
                    }
                }
                .onFailure { error ->
                    _sheet.update {
                        it?.copy(
                            rendering = false,
                            error = error.message ?: "Could not render the try-on"
                        )
                    }
                }
        }
    }

    // ---- delete ----

    fun onDeleteRequested() = _sheet.update { it?.copy(confirmingDelete = true, error = null) }

    fun onDeleteCancelled() = _sheet.update { it?.copy(confirmingDelete = false) }

    /**
     * Deletes the garment and closes the sheet.
     *
     * Confirmed in the sheet rather than done on the first tap, because this one cannot be
     * undone: the row and the photograph both go, and the photograph may be the only copy
     * if it was taken through the app.
     */
    fun onDeleteConfirmed() {
        val current = _sheet.value ?: return

        viewModelScope.launch {
            runCatching { wardrobe.delete(current.garmentId) }
                .onSuccess { _sheet.value = null }
                .onFailure { error ->
                    _sheet.update {
                        it?.copy(
                            confirmingDelete = false,
                            error = error.message ?: "Could not delete this"
                        )
                    }
                }
        }
    }

    fun dismissSheetError() = _sheet.update { it?.copy(error = null) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ClosetViewModel(
                    wardrobe = container.wardrobeRepository,
                    profile = container.profileRepository,
                    tryOn = container.tryOnRepository,
                    imageStore = container.imageStore,
                    rankDormant = container.rankDormant
                )
            }
        }
    }
}
