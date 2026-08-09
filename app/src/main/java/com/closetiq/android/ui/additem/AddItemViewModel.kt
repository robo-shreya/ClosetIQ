package com.closetiq.android.ui.additem

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetiq.android.AppContainer
import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.domain.model.Category
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
    val error: String? = null
) {
    val canSave: Boolean get() = label.isNotBlank() && localImagePath != null && !saving
}

class AddItemViewModel(
    private val wardrobe: WardrobeRepository,
    private val imageStore: ImageStore
) : ViewModel() {

    private val _state = MutableStateFlow(AddItemUiState())
    val state = _state.asStateFlow()

    fun onLabelChange(value: String) = _state.update { it.copy(label = value) }

    fun onCategoryChange(value: Category) = _state.update { it.copy(category = value) }

    /**
     * Copies the picked image into app storage and resizes it into the YouCam upload
     * envelope in one step — see ImagePreflight. Doing it here means a rejected upload
     * is impossible later rather than merely unlikely.
     */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }
            runCatching { imageStore.importFromUri(uri) }
                .onSuccess { path ->
                    _state.update { it.copy(importing = false, localImagePath = path) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(importing = false, error = error.message ?: "Could not read image")
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
                    _state.update { AddItemUiState() }
                    onDone()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(saving = false, error = error.message ?: "Could not save")
                    }
                }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                AddItemViewModel(
                    wardrobe = container.wardrobeRepository,
                    imageStore = container.imageStore
                )
            }
        }
    }
}
