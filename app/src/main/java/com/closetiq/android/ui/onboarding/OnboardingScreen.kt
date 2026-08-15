package com.closetiq.android.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.closetiq.android.domain.repository.ProfileRepository
import com.closetiq.android.domain.repository.SkinRepository
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

private const val PREVIEW_HEIGHT_DP = 260

data class OnboardingUiState(
    val photoPath: String? = null,
    val working: Boolean = false,
    val error: String? = null
)

class OnboardingViewModel(
    private val profile: ProfileRepository,
    private val skin: SkinRepository,
    private val imageStore: ImageStore
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()

    /**
     * The photo is saved to the profile before the reading is attempted, and kept even
     * if the reading fails. A failed skin call should not cost the user the picture —
     * try-on still needs it.
     */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }

            val path = runCatching { imageStore.importFromUri(uri) }.getOrElse { error ->
                _state.update {
                    it.copy(working = false, error = error.message ?: "Could not read that photo")
                }
                return@launch
            }

            profile.setPersonPhoto(path)
            _state.update { it.copy(photoPath = path) }

            skin.captureReading(path)
                .onSuccess { _state.update { s -> s.copy(working = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            working = false,
                            error = "Photo saved, but the skin reading failed: " +
                                (error.message ?: "unknown error")
                        )
                    }
                }
        }
    }

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
 * Asks for one photo, once.
 *
 * Deliberately skippable. The app's whole premise is that it works without a photo and
 * is only sharper with one — a blocking onboarding gate would contradict that, and would
 * strand anyone who opens the app somewhere they would rather not photograph themselves.
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::onPhotoPicked) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Kicker("First, one photo", color = Nocturne.Neutral600)
        Text(
            text = "A picture of you",
            style = MaterialTheme.typography.headlineMedium,
            color = Nocturne.Text
        )
        Text(
            text = "Used twice: to read your skin, and as the body every try-on is " +
                "rendered on. Attach it once and you won't be asked again.",
            style = MaterialTheme.typography.bodyLarge,
            color = Nocturne.Neutral400
        )

        val photoPath = state.photoPath

        if (photoPath == null) {
            DashedPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT_DP.dp)
            ) {
                if (state.working) {
                    NocturneSpinner(size = 20.dp)
                } else {
                    Text(
                        text = "Head and shoulders, or half body for try-on",
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
                model = File(photoPath),
                contentDescription = "Your photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT_DP.dp)
                    .clip(RadiusMd)
                    .border(1.dp, Nocturne.Neutral800, RadiusMd),
                contentScale = ContentScale.Crop
            )
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Accent300
            )
        }

        OutlinedButton(
            onClick = { picker.launch("image/*") },
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
        ) {
            Text(
                text = when {
                    state.working -> "Reading…"
                    photoPath != null -> "Use a different photo"
                    else -> "Choose a photo"
                },
                color = Nocturne.Text
            )
        }

        Button(
            onClick = { viewModel.finish(onDone) },
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
            Text(if (photoPath != null) "Open my closet" else "Continue")
        }

        if (photoPath == null) {
            TextButton(
                onClick = { viewModel.finish(onDone) },
                enabled = !state.working,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Skip — the closet works without it",
                    style = MaterialTheme.typography.labelMedium,
                    color = Nocturne.Neutral500
                )
            }
        }

        Footnote("You can add or replace this any time from the Mirror.")
    }
}
