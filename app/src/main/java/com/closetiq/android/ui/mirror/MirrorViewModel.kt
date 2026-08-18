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
    /** True only while the selfie is out at Skin Analysis. */
    val analysing: Boolean = false,
    /** True while a body shot is being copied in — no API call is involved. */
    val attaching: Boolean = false,
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

            // Nothing is suggested until YouCam has answered. Scoring itself works without
            // a reading — it falls back to a neutral palette — but a pick that appears the
            // moment the photo lands and then rearranges itself when the analysis returns
            // reads as the app guessing first and checking afterwards. It waits instead.
            val pick = reading?.let { runCatching { getTodaysPick(garments, it) } }

            _state.update {
                it.copy(
                    loading = false,
                    photos = photos,
                    reading = reading,
                    readingIsFresh = reading != null,
                    pick = pick?.getOrNull(),
                    error = pick?.exceptionOrNull()?.let(::describe)
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
        val isSelfie = slot == PhotoSlot.SELFIE

        viewModelScope.launch {
            // Only the selfie is "analysing" — a body shot is a file copy, and saying
            // otherwise put "Reading your skin…" on screen while nothing was being read.
            _state.update {
                it.copy(analysing = isSelfie, attaching = !isSelfie, error = null)
            }

            val path = runCatching { imageStore.importFromUri(uri) }
                .getOrElse { error ->
                    _state.update {
                        it.copy(analysing = false, attaching = false, error = describe(error))
                    }
                    return@launch
                }

            profile.setPhoto(slot, path)

            // The photo shows straight away — the card is the one thing that should react
            // to the tap. What waits is the pick, which needs the analysis under it.
            _state.update { it.copy(photos = it.photos.with(slot, path)) }

            if (!isSelfie) {
                _state.update { it.copy(attaching = false) }
                return@launch
            }

            skin.captureReading(path)
                .onSuccess {
                    _state.update { s -> s.copy(analysing = false) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(analysing = false, reading = null, pick = null,
                                error = describe(error))
                    }
                }
        }
    }

    /**
     * Forgets one of the user's photos.
     *
     * Removing the selfie takes the reading with it and puts the Mirror back to the state a
     * fresh install opens in. The reading was measured from that selfie and outlives it by
     * five days, and a reading with no photo under it is exactly the stale-but-plausible
     * state this screen should never show. A body shot carries nothing with it — it is only
     * ever a surface for try-on to render onto.
     */
    fun onRemovePhoto(slot: PhotoSlot) {
        viewModelScope.launch {
            profile.clearPhoto(slot)

            if (slot == PhotoSlot.SELFIE) {
                skin.clearReadings()
                _state.update {
                    it.copy(
                        photos = it.photos.with(slot, null),
                        reading = null,
                        readingIsFresh = false,
                        pick = null,
                        renderUrl = null,
                        renderNote = null,
                        error = null
                    )
                }
            } else {
                _state.update { it.copy(photos = it.photos.with(slot, null), error = null) }
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

    /**
     * Another outfit, at random.
     *
     * Rerolled up to [SHUFFLE_ATTEMPTS] times if it comes back as the outfit already on
     * screen. On a small closet a uniform draw repeats often — with two tops and two pairs
     * of trousers, one in four shuffles is the same outfit — and a button that visibly does
     * nothing reads as broken rather than as coincidence.
     */
    fun onShuffle() {
        viewModelScope.launch {
            val garments = wardrobe.observeActiveGarments().first()
            val reading = _state.value.reading
            val current = _state.value.pick

            var next: OutfitPick? = null
            repeat(SHUFFLE_ATTEMPTS) {
                if (next == null || next.sameOutfitAs(current)) {
                    next = runCatching { getTodaysPick.shuffled(garments, reading) }.getOrNull()
                }
            }

            // The render belongs to the outfit that produced it, so it goes with the outfit.
            next?.let { pick ->
                _state.update {
                    it.copy(pick = pick, renderUrl = null, renderNote = null, error = null)
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

    /** Same garments, whatever order they came out in. */
    private fun OutfitPick.sameOutfitAs(other: OutfitPick?): Boolean {
        if (other == null) return false
        fun ids(pick: OutfitPick) =
            (listOf(pick.hero) + pick.supporting).map { it.garment.id }.toSet()
        return ids(this) == ids(other)
    }

    /**
     * Until the domain TODOs are written, scoring throws NotImplementedError. Saying so
     * plainly beats a blank screen — you will see this a lot on days 3 and 4.
     */
    private fun describe(error: Throwable): String = when (error) {
        is NotImplementedError -> "Not written yet: ${error.message ?: "a domain function"}"
        else -> error.message ?: error::class.simpleName ?: "Something went wrong"
    }

    companion object {
        /** Enough to get off a repeat on a small closet, few enough to stay instant. */
        private const val SHUFFLE_ATTEMPTS = 5

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
