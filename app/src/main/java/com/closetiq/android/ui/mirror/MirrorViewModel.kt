package com.closetiq.android.ui.mirror

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetiq.android.AppContainer
import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.data.repository.RenderStrategy
import com.closetiq.android.domain.model.OutfitPick
import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.model.RenderTarget
import com.closetiq.android.domain.model.SkinReading
import com.closetiq.android.domain.repository.ProfileRepository
import com.closetiq.android.domain.repository.SkinRepository
import com.closetiq.android.domain.repository.WardrobeRepository
import com.closetiq.android.domain.usecase.GetTodaysPickUseCase
import com.closetiq.android.domain.usecase.LogWearUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MirrorUiState(
    val loading: Boolean = false,
    val reading: SkinReading? = null,
    val readingIsFresh: Boolean = false,
    val pick: OutfitPick? = null,
    val renderUrl: String? = null,
    val renderNote: String? = null,
    val rendering: Boolean = false,
    /** Which chained pass is in flight, as (pass, total). Null when idle. */
    val renderPass: Pair<Int, Int>? = null,
    /** Every picture of the user on file, resolved per render target. */
    val photos: PersonPhotos = PersonPhotos.EMPTY,
    val analysing: Boolean = false,
    val error: String? = null
) {
    /** Which body region the hero garment would be rendered onto. */
    val heroTarget: RenderTarget?
        get() = pick?.hero?.garment?.category?.renderTarget

    /**
     * The photo this render would actually use — an upper-body shot for a top, a
     * lower-body one for trousers. Null when no photo on file could contain that region.
     */
    val heroPersonPhoto: String?
        get() = heroTarget?.let { photos.bestFor(it) }

    /**
     * Virtual try-on needs a picture of the garment, and seeded swatches have none.
     * Surfaced as state so the button can explain itself instead of failing on tap.
     */
    val heroCanRender: Boolean
        get() = heroPersonPhoto != null &&
            pick?.hero?.garment?.let { it.cutoutPath ?: it.imagePath } != null

    /** Distinguishes "no picture of you" from "this garment is only a swatch". */
    val heroIsSwatch: Boolean
        get() = pick?.hero?.garment?.let { it.cutoutPath ?: it.imagePath } == null

    /** The slot to ask for when the hero cannot be rendered for want of a photo. */
    val heroNeededSlot: PhotoSlot?
        get() = if (heroPersonPhoto == null) {
            heroTarget?.let { photos.preferredSlotFor(it) }
        } else {
            null
        }
}

class MirrorViewModel(
    private val wardrobe: WardrobeRepository,
    private val skin: SkinRepository,
    private val profile: ProfileRepository,
    private val getTodaysPick: GetTodaysPickUseCase,
    private val logWear: LogWearUseCase,
    private val renderStrategy: RenderStrategy,
    private val imageStore: ImageStore
) : ViewModel() {

    private val _state = MutableStateFlow(MirrorUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val reading = skin.currentFreshReading()
            val garments = wardrobe.observeActiveGarments().first()
            val photos = profile.photos()

            // Scoring is local and free, so it runs on every refresh whether or not
            // there is a skin reading. The app is useful without a selfie; it is just
            // sharper with one.
            val pick = runCatching { getTodaysPick(garments, reading) }

            _state.update {
                it.copy(
                    loading = false,
                    photos = photos,
                    reading = reading,
                    readingIsFresh = reading != null,
                    pick = pick.getOrNull(),
                    error = pick.exceptionOrNull()?.let(::describe)
                )
            }
        }
    }

    /**
     * Attach or replace one of the user's photos.
     *
     * Only a selfie is worth sending to Skin Analysis — it needs a face. A body shot is
     * stored for `cloth` and nothing else, which keeps adding one free.
     */
    fun onPhotoPicked(slot: PhotoSlot, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(analysing = true, error = null) }

            val path = runCatching { imageStore.importFromUri(uri) }
                .getOrElse { error ->
                    _state.update { it.copy(analysing = false, error = describe(error)) }
                    return@launch
                }

            profile.setPhoto(slot, path)

            if (slot != PhotoSlot.SELFIE) {
                _state.update { it.copy(analysing = false) }
                refresh()
                return@launch
            }

            skin.captureReading(path)
                .onSuccess {
                    _state.update { s -> s.copy(analysing = false) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(analysing = false, error = describe(error)) }
                }
        }
    }

    /** One render per session — only the hero item, only when the user asks. */
    fun onRenderHero() {
        val current = _state.value
        val pick = current.pick ?: return

        // Resolved for the hero's own body region, so a pair of trousers is never rendered
        // onto a head-and-shoulders selfie.
        val person = current.heroPersonPhoto ?: run {
            val needed = when (current.heroNeededSlot) {
                PhotoSlot.LOWER_BODY -> "a lower-body photo"
                PhotoSlot.UPPER_BODY -> "an upper-body photo"
                else -> "a full-body photo"
            }
            _state.update {
                it.copy(error = "Add $needed of you first — this garment renders onto it.")
            }
            return
        }

        // The whole outfit, not just the hero: the chained strategy renders one garment
        // per body region, each onto the output of the last.
        val outfit = listOf(pick.hero.garment) + pick.supporting.map { it.garment }

        viewModelScope.launch {
            _state.update { it.copy(rendering = true, error = null, renderPass = null) }

            renderStrategy.render(
                personImagePath = person,
                garments = outfit,
                onProgress = { pass, total ->
                    _state.update { it.copy(renderPass = pass to total) }
                }
            )
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            rendering = false,
                            renderPass = null,
                            renderUrl = result.imageUrl,
                            renderNote = result.note
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(rendering = false, renderPass = null, error = describe(error))
                    }
                }
        }
    }

    fun onWoreIt() {
        val heroId = _state.value.pick?.hero?.garment?.id ?: return
        viewModelScope.launch {
            logWear(heroId)
            refresh()
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * Until the domain TODOs are written, scoring throws NotImplementedError. Saying so
     * plainly beats a blank screen — you will see this a lot on days 3 and 4.
     */
    private fun describe(error: Throwable): String = when (error) {
        is NotImplementedError -> "Not written yet: ${error.message ?: "a domain function"}"
        else -> error.message ?: error::class.simpleName ?: "Something went wrong"
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                MirrorViewModel(
                    wardrobe = container.wardrobeRepository,
                    skin = container.skinRepository,
                    profile = container.profileRepository,
                    getTodaysPick = container.getTodaysPick,
                    logWear = container.logWear,
                    renderStrategy = container.renderStrategy,
                    imageStore = container.imageStore
                )
            }
        }
    }
}
