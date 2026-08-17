package com.closetiq.android.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.closetiq.android.AppContainer
import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.repository.ProfileRepository
import com.closetiq.android.domain.repository.SkinRepository
import com.closetiq.android.ui.additem.AddItemScreen
import com.closetiq.android.ui.components.DashedPanel
import com.closetiq.android.ui.components.Footnote
import com.closetiq.android.ui.components.Kicker
import com.closetiq.android.ui.components.NocturneSpinner
import com.closetiq.android.ui.components.RadiusMd
import com.closetiq.android.ui.theme.Nocturne
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val SELFIE_PREVIEW_HEIGHT_DP = 240
private const val BODY_TILE_HEIGHT_DP = 132

/**
 * Two steps, in the order the app needs them: who you are, then what you own.
 *
 * The photos step used to end with "Open my closet", which left the user on a tab bar
 * having to work out what to do next. Handing them straight to the add-item screen means
 * first run finishes with a wardrobe that has something of theirs in it, and a Mirror that
 * has a real reason to show anything.
 */
enum class OnboardingStep { PHOTOS, FIRST_GARMENT }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.PHOTOS,
    val photos: PersonPhotos = PersonPhotos.EMPTY,
    /** Which slot is mid-import, so only that tile shows a spinner. */
    val busySlot: PhotoSlot? = null,
    /** True while the selfie is out at Skin Analysis. */
    val reading: Boolean = false,
    val error: String? = null
) {
    val working: Boolean get() = busySlot != null || reading
}

class OnboardingViewModel(
    private val profile: ProfileRepository,
    private val skin: SkinRepository,
    private val imageStore: ImageStore
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()

    init {
        // Re-entering onboarding should show what is already on file rather than looking
        // like nothing was ever attached.
        viewModelScope.launch {
            val stored = profile.photos()
            if (stored.hasAny) _state.update { it.copy(photos = stored) }
        }
    }

    /**
     * Every photo is saved to the profile before anything else is attempted, and kept even
     * if the skin reading fails. A failed skin call should not cost the user the picture —
     * try-on still needs it.
     *
     * Only the selfie is sent for a reading. The body shots are for `cloth` and would be
     * two wasted YouCam calls each: Skin Analysis needs a face.
     */
    fun onPhotoPicked(slot: PhotoSlot, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busySlot = slot, error = null) }

            val path = runCatching { imageStore.importFromUri(uri) }.getOrElse { error ->
                _state.update {
                    it.copy(
                        busySlot = null,
                        error = error.message ?: "Could not read that photo"
                    )
                }
                return@launch
            }

            profile.setPhoto(slot, path)
            _state.update { it.copy(busySlot = null, photos = it.photos.with(slot, path)) }

            if (slot == PhotoSlot.SELFIE) captureReading(path)
        }
    }

    private suspend fun captureReading(path: String) {
        _state.update { it.copy(reading = true) }

        skin.captureReading(path)
            .onSuccess { _state.update { it.copy(reading = false) } }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        reading = false,
                        error = "Selfie saved, but the skin reading failed: " +
                            (error.message ?: "unknown error")
                    )
                }
            }
    }

    fun toGarmentStep() = _state.update { it.copy(step = OnboardingStep.FIRST_GARMENT) }

    fun backToPhotos() = _state.update { it.copy(step = OnboardingStep.PHOTOS) }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            profile.markOnboarded()
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                OnboardingViewModel(
                    profile = container.profileRepository,
                    skin = container.skinRepository,
                    imageStore = container.imageStore
                )
            }
        }
    }
}

/**
 * Asks for the photos once, up front.
 *
 * The selfie and the body shots answer different questions, so they are asked for
 * separately: Skin Analysis needs a face, `cloth` needs the body region it is being asked
 * to replace. One image cannot be both, and pretending otherwise is why a lower-body
 * try-on used to come back empty.
 *
 * Still deliberately skippable. The app's whole premise is that it works without a photo
 * and is only sharper with one — a blocking gate would contradict that, and would strand
 * anyone who opens the app somewhere they would rather not photograph themselves.
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.step) {
        OnboardingStep.PHOTOS -> PhotosStep(
            state = state,
            onPick = viewModel::onPhotoPicked,
            onNext = viewModel::toGarmentStep,
            onSkip = { viewModel.finish(onDone) }
        )

        OnboardingStep.FIRST_GARMENT -> FirstGarmentStep(
            container = container,
            onBack = viewModel::backToPhotos,
            onAdded = { viewModel.finish(onDone) },
            onSkip = { viewModel.finish(onDone) }
        )
    }
}

/**
 * The first garment, added before onboarding is over.
 *
 * This is the whole add-item screen, not a copy of it. Reusing it means the colour-derived
 * name and try-on are in first run for free, and there is only ever one add flow to keep
 * working. The framing above it is fixed while the screen scrolls underneath — nesting one
 * vertical scroll inside another would crash.
 */
@Composable
private fun FirstGarmentStep(
    container: AppContainer,
    onBack: () -> Unit,
    onAdded: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Both step controls sit above the add screen, not below it. Putting them at the
        // bottom left "Save to closet" clipped to a one-pixel sliver — the primary action,
        // invisible — because the embedded screen scrolls inside whatever height is left.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "← Photos",
                    style = MaterialTheme.typography.labelMedium,
                    color = Nocturne.Neutral500
                )
            }
            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.labelMedium,
                    color = Nocturne.Neutral500
                )
            }
        }

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Kicker("Step 2 of 2", color = Nocturne.Neutral600)
            Text(
                text = "Add something you own",
                style = MaterialTheme.typography.headlineMedium,
                color = Nocturne.Text
            )
            Text(
                text = "Your closet already has a few examples. Add one thing of yours — the " +
                    "name writes itself from the photo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Nocturne.Neutral400
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            AddItemScreen(container = container, onDone = onAdded)
        }
    }
}

@Composable
private fun PhotosStep(
    state: OnboardingUiState,
    onPick: (PhotoSlot, Uri) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    // One launcher serves every slot; this remembers which tile asked.
    var pendingSlot by remember { mutableStateOf(PhotoSlot.SELFIE) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onPick(pendingSlot, it) } }

    fun pick(slot: PhotoSlot) {
        pendingSlot = slot
        picker.launch("image/*")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Kicker("Step 1 of 2", color = Nocturne.Neutral600)
        Text(
            text = "A few pictures of you",
            style = MaterialTheme.typography.headlineMedium,
            color = Nocturne.Text
        )
        Text(
            text = "The selfie reads your skin. The body shots are what try-on renders " +
                "onto — a top needs your upper half in frame, trousers need your lower " +
                "half. Attach them once and you won't be asked again.",
            style = MaterialTheme.typography.bodyLarge,
            color = Nocturne.Neutral400
        )

        SelfiePanel(state = state, onPick = { pick(PhotoSlot.SELFIE) })

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Kicker("For try-on — optional")
            Text(
                text = "A full-body shot alone covers everything. The other two only " +
                    "sharpen it.",
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral600
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BodySlotTile(
                    label = "Full body",
                    path = state.photos.fullBody,
                    busy = state.busySlot == PhotoSlot.FULL_BODY,
                    enabled = !state.working,
                    onClick = { pick(PhotoSlot.FULL_BODY) },
                    modifier = Modifier.weight(1f)
                )
                BodySlotTile(
                    label = "Upper body",
                    path = state.photos.upperBody,
                    busy = state.busySlot == PhotoSlot.UPPER_BODY,
                    enabled = !state.working,
                    onClick = { pick(PhotoSlot.UPPER_BODY) },
                    modifier = Modifier.weight(1f)
                )
                BodySlotTile(
                    label = "Lower body",
                    path = state.photos.lowerBody,
                    busy = state.busySlot == PhotoSlot.LOWER_BODY,
                    enabled = !state.working,
                    onClick = { pick(PhotoSlot.LOWER_BODY) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Accent300
            )
        }

        Button(
            onClick = onNext,
            enabled = !state.working,
            shape = RadiusMd,
            colors = ButtonDefaults.buttonColors(
                containerColor = Nocturne.Accent900,
                contentColor = Nocturne.Accent200,
                disabledContainerColor = Nocturne.Neutral900,
                disabledContentColor = Nocturne.Neutral600
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("Next — add a garment")
        }

        if (!state.photos.hasAny) {
            TextButton(
                onClick = onSkip,
                enabled = !state.working,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Skip — the closet works without them",
                    style = MaterialTheme.typography.labelMedium,
                    color = Nocturne.Neutral500
                )
            }
        }

        Footnote(
            "You can replace the selfie from the Mirror, and any body shot from the " +
                "screen where you add an item."
        )
    }
}

/** The selfie gets the big frame: it is the only one that is close to required. */
@Composable
private fun SelfiePanel(state: OnboardingUiState, onPick: () -> Unit) {
    val selfie = state.photos.selfie

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Kicker("Selfie — reads your skin")

        if (selfie == null) {
            DashedPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SELFIE_PREVIEW_HEIGHT_DP.dp)
            ) {
                if (state.busySlot == PhotoSlot.SELFIE || state.reading) {
                    NocturneSpinner(size = 20.dp)
                } else {
                    Text(
                        text = "Head and shoulders, well lit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Nocturne.Neutral400
                    )
                    Text(
                        text = "Stays on your device except for the two YouCam calls",
                        style = MaterialTheme.typography.bodySmall,
                        color = Nocturne.Neutral600
                    )
                }
            }
        } else {
            AsyncImage(
                model = File(selfie),
                contentDescription = "Your selfie",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SELFIE_PREVIEW_HEIGHT_DP.dp)
                    .clip(RadiusMd)
                    .border(1.dp, Nocturne.Neutral800, RadiusMd),
                contentScale = ContentScale.Crop
            )
        }

        OutlinedButton(
            onClick = onPick,
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
        ) {
            Text(
                text = when {
                    state.reading -> "Reading your skin…"
                    state.busySlot == PhotoSlot.SELFIE -> "Importing…"
                    selfie != null -> "Use a different selfie"
                    else -> "Choose a selfie"
                },
                color = Nocturne.Text
            )
        }
    }
}

/**
 * One optional body shot. Tapping the tile is the whole interaction — there is no separate
 * button, because three slots each with their own button is a wall of controls.
 */
@Composable
private fun BodySlotTile(
    label: String,
    path: String?,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BODY_TILE_HEIGHT_DP.dp)
                .clip(RadiusMd)
                .background(Nocturne.Field)
                .border(
                    width = 1.dp,
                    // An attached slot is marked with an accent hairline — a line, not a
                    // flood, which is the one thing Nocturne is strict about.
                    color = if (path != null) Nocturne.Accent800 else Nocturne.Neutral800,
                    shape = RadiusMd
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            when {
                busy -> NocturneSpinner(size = 16.dp)

                path != null -> AsyncImage(
                    model = File(path),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize().clip(RadiusMd),
                    contentScale = ContentScale.Crop
                )

                else -> Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Nocturne.Neutral600
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (path != null) Nocturne.Text else Nocturne.Neutral600,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
