package com.cellier.manager.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellier.manager.R
import com.cellier.manager.data.CellarItem
import com.cellier.manager.data.EnrichmentRequest
import com.cellier.manager.data.SyncFailureReason
import com.cellier.manager.repository.CellarRepository
import com.cellier.manager.repository.ManualSearchOutcome
import com.cellier.manager.util.PhotoCapture
import com.cellier.manager.work.CompanionSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FicheProduitViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CellarRepository.getInstance(app)
    private val appContext = app.applicationContext

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val _showResetDialog = MutableStateFlow(false)
    val showResetDialog: StateFlow<Boolean> = _showResetDialog.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun observeItem(id: Long): Flow<CellarItem?> = repo.observeById(id)
    fun observeEnrichmentRequests(itemId: Long): Flow<List<EnrichmentRequest>> =
        repo.observeEnrichmentRequests(itemId)

    fun refreshEnrichment() = CompanionSyncWorker.schedule(getApplication())

    fun toggleEdit() {
        _isEditing.value = !_isEditing.value
    }

    fun cancelEdit() {
        _isEditing.value = false
    }

    fun saveEdits(updated: CellarItem) {
        viewModelScope.launch {
            repo.updateItem(updated)
            _isEditing.value = false
        }
    }

    fun incrementQuantity(id: Long) {
        viewModelScope.launch { repo.incrementQuantity(id) }
    }

    fun decrementQuantity(id: Long) {
        viewModelScope.launch { repo.decrementQuantity(id) }
    }

    fun askDelete() { _showDeleteDialog.value = true }
    fun cancelDelete() { _showDeleteDialog.value = false }
    fun askResetSources() { _showResetDialog.value = true }
    fun cancelResetSources() { _showResetDialog.value = false }

    fun confirmDelete(item: CellarItem) {
        viewModelScope.launch {
            repo.deleteItem(item)
            PhotoCapture.deletePhotoFile(item.photoPath)
            _showDeleteDialog.value = false
            _deleted.value = true
        }
    }

    fun retryIndexation(id: Long) {
        viewModelScope.launch { repo.retryIndexation(id) }
    }

    fun confirmResetSources(id: Long) {
        viewModelScope.launch {
            repo.resetResolvedLinksAndMetadata(id)
            _showResetDialog.value = false
            Toast.makeText(
                appContext,
                appContext.getString(R.string.fiche_reset_done),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun confirmVivinoMatch(id: Long) {
        viewModelScope.launch { repo.confirmVivinoMatch(id) }
    }

    fun confirmUntappdMatch(id: Long) {
        viewModelScope.launch { repo.confirmUntappdMatch(id) }
    }

    // États de chargement pour les recherches manuelles
    private val _saqSearching = kotlinx.coroutines.flow.MutableStateFlow(false)
    val saqSearching: kotlinx.coroutines.flow.StateFlow<Boolean> = _saqSearching.asStateFlow()

    private val _vivinoSearching = kotlinx.coroutines.flow.MutableStateFlow(false)
    val vivinoSearching: kotlinx.coroutines.flow.StateFlow<Boolean> = _vivinoSearching.asStateFlow()

    private val _untappdSearching = kotlinx.coroutines.flow.MutableStateFlow(false)
    val untappdSearching: kotlinx.coroutines.flow.StateFlow<Boolean> = _untappdSearching.asStateFlow()

    fun searchSaqManual(id: Long) {
        if (_saqSearching.value) return
        viewModelScope.launch {
            _saqSearching.value = true
            try {
                showSearchResult("SAQ", repo.searchSaqManual(id))
            } finally {
                _saqSearching.value = false
            }
        }
    }

    fun searchVivinoManual(id: Long) {
        if (_vivinoSearching.value) return
        viewModelScope.launch {
            _vivinoSearching.value = true
            try {
                showSearchResult("Vivino", repo.searchVivinoManual(id))
            } finally {
                _vivinoSearching.value = false
            }
        }
    }

    fun searchUntappdManual(id: Long) {
        if (_untappdSearching.value) return
        viewModelScope.launch {
            _untappdSearching.value = true
            try {
                showSearchResult("Untappd", repo.searchUntappdManual(id))
            } finally {
                _untappdSearching.value = false
            }
        }
    }

    private fun showSearchResult(source: String, outcome: ManualSearchOutcome) {
        val resId = when {
            outcome.found -> R.string.fiche_search_found
            outcome.failureReason == SyncFailureReason.RATE_LIMIT -> R.string.fiche_search_rate_limited
            outcome.failureReason == SyncFailureReason.PROVIDER_BUSY -> R.string.fiche_search_provider_busy
            outcome.failureReason == SyncFailureReason.NETWORK -> R.string.fiche_search_network_error
            else -> R.string.fiche_search_no_result
        }
        Toast.makeText(
            appContext,
            appContext.getString(resId, source),
            Toast.LENGTH_SHORT
        ).show()
    }
}
