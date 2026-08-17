package com.closetiq.android.ui.additem

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetiq.android.AppContainer
import com.closetiq.android.data.image.ColorExtractor
import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.domain.color.ColorNamer
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.repository.ProfileRepository
import com.closetiq.android.domain.repository.TryOnRepository
import com.closetiq.android.domain.repository.WardrobeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddItemUiState(
    val label: String = "",
    val category: Category = Category.TOP,
    val localImagePath: String? = null,
    val importing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    /**
     * The measured garment colour, kept so the suggested name can be reworded when the
     * category changes without re-reading the photo.
     */
    val garmentColor: LabColor? = null,
    /**
     * True once the user has typed in the name field. After that the suggestion stops
     * following the category — their words win.
     */
    val labelEdited: Boolean = false,
    /** Every picture of the user on file. Kept in sync with the profile, never a copy. */
    val photos: PersonPhotos = PersonPhotos.EMPTY,
    /** True once a photo has been attached from this screen rather than at onboarding. */
    val photoJustAttached: Boolean = false,
    val rendering: Boolean = false,
    val renderUrl: String? = null,
    val renderNote: String? = null
) {
    val canSave: Boolean
        get() = label.isNotBlank() && localImagePath != null && !saving && !rendering

    /** The stored photo that actually contains the region this category targets. */
    val personPhoto: String?
        get() = photos.bestFor(category.renderTarget)

    /**
     * Where a photo picked on this screen is saved, and equally the slot to ask for when
     * nothing on file covers this category.
     */
    val targetSlot: PhotoSlot get() = photos.preferredSlotFor(category.renderTarget)

    /** Try-on needs a garment to put on and a body to put it on. */
    val canRender: Boolean
        get() = localImagePath != null && personPhoto != null && !rendering && !importing
}

class AddItemViewModel(
    private val wardrobe: WardrobeRepository,
    private val imageStore: ImageStore,
    private val colorExtractor: ColorExtractor,
    private val profile: ProfileRepository,
    private val tryOn: TryOnRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AddItemUiState())
    val state = _state.asStateFlow()

    init {
        // The photo taken during onboarding is the default body for "See it on me", so the
        // user is not asked for a second picture of themselves mid-flow.
        viewModelScope.launch {
            val stored = profile.photos()
            _state.update { it.copy(photos = stored) }
        }
    }

    fun onLabelChange(value: String) = _state.update {
        it.copy(label = value, labelEdited = true)
    }

    fun onCategoryChange(value: Category) = _state.update { current ->
        val next = current.copy(category = value)
        // A category change rewords the suggestion — "Mustard top" becomes "Mustard
        // jacket" — but only while the field still holds the app's words, not theirs.
        if (current.labelEdited) next else next.copy(label = suggestionFor(next))
    }

    /**
     * Copies the picked image into app storage and resizes it into the YouCam upload
     * envelope in one step — see ImagePreflight. Doing it here means a rejected upload
     * is impossible later rather than merely unlikely.
     *
     * The colour is read straight afterwards so the name field can fill itself in. That
     * read is local arithmetic over a 128px thumbnail: no network, no credit.
     */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(importing = true, error = null, renderUrl = null, renderNote = null)
            }

            val path = runCatching { imageStore.importFromUri(uri) }
                .getOrElse { error ->
                    _state.update {
                        it.copy(importing = false, error = error.message ?: "Could not read image")
                    }
                    return@launch
                }

            // A failed colour read is not a failed import. The photo is still fine to
            // save; the user just types the name themselves, as they did before.
            val color = runCatching { colorExtractor.fromPathCentreCrop(path) }.getOrNull()

            _state.update { current ->
                val next = current.copy(
                    importing = false,
                    localImagePath = path,
                    garmentColor = color
                )
                if (current.labelEdited) next else next.copy(label = suggestionFor(next))
            }
        }
    }

    /**
     * Attach or replace a picture of the user, saved into the slot the current category
     * needs — a top writes the upper-body shot, trousers the lower-body one.
     *
     * It is kept, not borrowed. The screen asks for a specific missing photo, so treating
     * the answer as a throwaway would mean asking again for the very next garment.
     */
    fun onPersonPhotoPicked(uri: Uri) {
        val slot = _state.value.targetSlot

        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }

            runCatching { imageStore.importFromUri(uri) }
                .onSuccess { path ->
                    profile.setPhoto(slot, path)
                    _state.update {
                        it.copy(
                            importing = false,
                            photos = it.photos.with(slot, path),
                            photoJustAttached = true,
                            renderUrl = null,
                            renderNote = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(importing = false, error = error.message ?: "Could not read image")
                    }
                }
        }
    }

    /**
     * One try-on render of the garment being added. Costs one YouCam call, and only when
     * the user asks — which is why this is a button rather than part of onImagePicked.
     */
    fun onSeeItOnMe() {
        val current = _state.value
        val garment = current.localImagePath ?: return
        val person = current.personPhoto ?: return

        viewModelScope.launch {
            _state.update { it.copy(rendering = true, error = null, renderNote = null) }

            tryOn.render(person, garment, current.category.renderTarget)
                .onSuccess { result ->
                    // A cloth task can report success with no image at all when a photo is
                    // unusable. Saying so beats an empty frame.
                    val note = result.note
                        ?: if (result.imageUrl == null) EMPTY_RENDER_NOTE else null

                    _state.update {
                        it.copy(rendering = false, renderUrl = result.imageUrl, renderNote = note)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(rendering = false, error = error.message ?: "Try-on failed")
                    }
                }
        }
    }

    fun onSave(onDone: () -> Unit) {
        val current = _state.value
        val path = current.localImagePath ?: return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching {
                wardrobe.addGarment(current.label.trim(), current.category, path)
            }
                .onSuccess {
                    // The person photo survives the reset — it belongs to the user, not to
                    // the garment that was just added.
                    _state.update { AddItemUiState(photos = it.photos) }
                    onDone()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(saving = false, error = error.message ?: "Could not save")
                    }
                }
        }
    }

    /** Falls back to whatever is already typed when no colour could be read. */
    private fun suggestionFor(state: AddItemUiState): String =
        state.garmentColor?.let { ColorNamer.suggestLabel(it, state.category) } ?: state.label

    companion object {
        private const val EMPTY_RENDER_NOTE =
            "Nothing came back for that pair of photos. A full-length, front-on picture " +
                "of you renders best."

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                AddItemViewModel(
                    wardrobe = container.wardrobeRepository,
                    imageStore = container.imageStore,
                    colorExtractor = container.colorExtractor,
                    profile = container.profileRepository,
                    tryOn = container.tryOnRepository
                )
            }
        }
    }
}
