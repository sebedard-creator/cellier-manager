package com.cellier.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cellier.manager.data.BeverageType
import com.cellier.manager.data.CellarItem
import com.cellier.manager.data.Suggestion
import com.cellier.manager.data.WineColor
import com.cellier.manager.repository.CellarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AjouterUiState(
    val type: BeverageType = BeverageType.VIN,
    val photoPath: String = "",
    val producer: String = "",
    val name: String = "",
    val vintage: String = "",
    val wineColor: WineColor? = null,
    val producerSuggestions: List<Suggestion> = emptyList(),
    val isSaving: Boolean = false,
    val savedItemId: Long? = null
) {
    /**
     * Validation : photo + producteur + nom + (millésime si vin).
     */
    val canSave: Boolean
        get() = photoPath.isNotBlank()
            && producer.isNotBlank()
            && name.isNotBlank()
            && (type == BeverageType.BIERE || vintage.isNotBlank())
}

class AjouterViewModel(app: Application, private val savedState: SavedStateHandle) : AndroidViewModel(app) {

    private val repo = CellarRepository.getInstance(app)

    private val _state = MutableStateFlow(AjouterUiState(
        type = savedState.get<String>("type")?.let { runCatching { BeverageType.valueOf(it) }.getOrNull() } ?: BeverageType.VIN,
        photoPath = savedState["photoPath"] ?: "",
        producer = savedState["producer"] ?: "",
        name = savedState["name"] ?: "",
        vintage = savedState["vintage"] ?: "",
        wineColor = savedState.get<String>("wineColor")?.let { runCatching { WineColor.valueOf(it) }.getOrNull() }
    ))
    val state: StateFlow<AjouterUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                producerSuggestions = repo.producerSuggestions()
            )
        }
    }

    fun setType(type: BeverageType) {
        // Si on passe à BIERE, on reset wineColor (n'a aucun sens pour une bière)
        _state.value = _state.value.copy(
            type = type,
            wineColor = if (type == BeverageType.BIERE) null else _state.value.wineColor
        )
        savedState["type"] = type.name
        savedState["wineColor"] = _state.value.wineColor?.name
    }

    fun setWineColor(color: WineColor?) {
        _state.value = _state.value.copy(wineColor = color)
        savedState["wineColor"] = color?.name
    }

    fun setPhotoPath(path: String) {
        _state.value = _state.value.copy(photoPath = path)
        savedState["photoPath"] = path
    }

    fun setProducer(value: String) {
        _state.value = _state.value.copy(producer = value)
        savedState["producer"] = value
    }

    fun setName(value: String) {
        _state.value = _state.value.copy(name = value)
        savedState["name"] = value
    }

    fun setVintage(value: String) {
        _state.value = _state.value.copy(vintage = value)
        savedState["vintage"] = value
    }

    fun save() {
        val s = _state.value
        if (!s.canSave || s.isSaving) return

        viewModelScope.launch {
            _state.value = s.copy(isSaving = true)
            val item = CellarItem(
                name = s.name.trim(),
                producer = s.producer.trim(),
                vintage = s.vintage.trim().takeIf { it.isNotBlank() },
                type = s.type,
                photoPath = s.photoPath,
                quantity = 1,
                wineColor = s.wineColor
            )
            val id = repo.addItem(item)
            listOf("type", "photoPath", "producer", "name", "vintage", "wineColor")
                .forEach { key -> savedState.remove<Any?>(key) }
            _state.value = _state.value.copy(isSaving = false, savedItemId = id)
        }
    }
}
