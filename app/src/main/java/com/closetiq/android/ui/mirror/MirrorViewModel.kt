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
import com.closetiq.android.domain.model.SkinReading
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
    val analysing: Boolean = false,
    val error: String? = null
) {
    /**
     * Virtual try-on needs a picture of the garment, and seeded swatches have none.
     * Surfaced as state so the button can explain itself instead of failing on tap.
     */
    val heroCanRender: Boolean
        get() = pick?.hero?.garment?.let { it.cutoutPath ?: it.imagePath } != null
}

class MirrorViewModel(
    private val wardrobe: WardrobeRepository,
    private val skin: SkinRepository,
    private val getTodaysPick: GetTodaysPickUseCase,
    private val logWear: LogWearUseCase,
    private val renderStrategy: RenderStrategy,
    private val imageStore: ImageStore
) : ViewModel() {

    private val _state = MutableStateFlow(MirrorUiState())
    val state = _state.asStateFlow()

    /** The photo used for the VTO render. Also the image sent for skin analysis. */
    private var personImagePath: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val reading = skin.currentFreshReading()
            val garments = wardrobe.observeActiveGarments().first()

            // Scoring is local and free, so it runs on every refresh whether or not
            // there is a skin reading. The app is useful without a selfie; it is just
            // sharper with one.
            val pick = runCatching { getTodaysPick(garments, reading) }

            _state.update {
                it.copy(
                    loading = false,
                    reading = reading,
                    readingIsFresh = reading != null,
                    pick = pick.getOrNull(),
                    error = pick.exceptionOrNull()?.let(::describe)
                )
            }
        }
    }

    /** One photo, used for both the skin reading and the try-on render. */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(analysing = true, error = null) }

            val path = runCatching { imageStore.importFromUri(uri) }
                .getOrElse { error ->
                    _state.update { it.copy(analysing = false, error = describe(error)) }
                    return@launch
                }

            personImagePath = path

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
        val pick = _state.value.pick ?: return
        val person = personImagePath ?: run {
            _state.update { it.copy(error = "Take a photo first — VTO needs a picture of you.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(rendering = true, error = null) }

            renderStrategy.render(person, listOf(pick.hero.garment))
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
                    _state.update { it.copy(rendering = false, error = describe(error)) }
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
                    getTodaysPick = container.getTodaysPick,
                    logWear = container.logWear,
                    renderStrategy = container.renderStrategy,
                    imageStore = container.imageStore
                )
            }
        }
    }
}
