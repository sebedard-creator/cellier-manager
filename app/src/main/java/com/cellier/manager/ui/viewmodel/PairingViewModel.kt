package com.cellier.manager.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellier.manager.enrichment.CompanionClient
import com.cellier.manager.enrichment.CompanionStore
import com.cellier.manager.enrichment.PairingBundle
import com.cellier.manager.work.CompanionSyncWorker
import com.cellier.manager.sommelier.SommelierStore
import com.cellier.manager.backup.BackupManager
import com.cellier.manager.backup.BackupSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairingUiState(
    val pairedServer: String? = null,
    val pairedUrl: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val sommelierConfigured: Boolean = false,
    val sommelierModel: String = SommelierStore.DEFAULT_MODEL,
    val backupBusy: Boolean = false,
    val restorePreview: BackupSummary? = null
)

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val store = CompanionStore(application)
    private val sommelierStore = SommelierStore(application)
    private val backups = BackupManager(application)
    private var pendingRestore: Uri? = null
    private val _ui = MutableStateFlow(store.load().let {
        val sommelier = sommelierStore.load()
        PairingUiState(pairedServer = it?.serverName, pairedUrl = it?.baseUrl,
            sommelierConfigured = sommelier != null,
            sommelierModel = sommelier?.model ?: SommelierStore.DEFAULT_MODEL)
    })
    val ui: StateFlow<PairingUiState> = _ui.asStateFlow()

    fun importPairingFile(content: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null, error = null) }
            runCatching {
                val bundle = PairingBundle.fromJson(content)
                CompanionClient.forPairing(bundle).pair(store, bundle)
            }.onSuccess { configuration ->
                _ui.update { state -> state.copy(
                    pairedServer = configuration.serverName,
                    pairedUrl = configuration.baseUrl,
                    busy = false, message = "Ordinateur jumelé.", error = null
                ) }
                CompanionSyncWorker.schedule(getApplication())
            }.onFailure { error ->
                _ui.update { it.copy(busy = false, error = error.message ?: "Jumelage impossible.") }
            }
        }
    }

    fun disconnect() {
        store.clear()
        _ui.update { it.copy(pairedServer = null, pairedUrl = null, message = "Ordinateur déconnecté.", error = null) }
    }

    fun synchronize() {
        CompanionSyncWorker.schedule(getApplication())
        _ui.update { it.copy(message = "Synchronisation demandée.", error = null) }
    }

    fun saveSommelier(apiKey: String, model: String) {
        runCatching { sommelierStore.save(apiKey, model) }
            .onSuccess { _ui.update { it.copy(sommelierConfigured = true, sommelierModel = model.trim().ifBlank { SommelierStore.DEFAULT_MODEL }, message = "Bromelier configuré.", error = null) } }
            .onFailure { error -> _ui.update { it.copy(error = error.message, message = null) } }
    }

    fun clearSommelier() {
        sommelierStore.clear()
        _ui.update { it.copy(sommelierConfigured = false, message = "Clé Bromelier supprimée.", error = null) }
    }

    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(backupBusy = true, message = null, error = null) }
            runCatching { backups.export(destination) }
                .onSuccess { summary -> _ui.update { it.copy(
                    backupBusy = false,
                    message = "Sauvegarde terminée: ${summary.totalItems} fiche(s), ${summary.photosPresent} photo(s)."
                ) } }
                .onFailure { error -> _ui.update { it.copy(backupBusy = false, error = error.message ?: "Sauvegarde impossible.") } }
        }
    }

    fun inspectBackup(source: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(backupBusy = true, message = null, error = null, restorePreview = null) }
            runCatching { backups.inspect(source) }
                .onSuccess { summary ->
                    pendingRestore = source
                    _ui.update { it.copy(backupBusy = false, restorePreview = summary) }
                }
                .onFailure { error -> _ui.update { it.copy(backupBusy = false, error = error.message ?: "Sauvegarde invalide.") } }
        }
    }

    fun dismissRestore() {
        pendingRestore = null
        _ui.update { it.copy(restorePreview = null) }
    }

    fun confirmRestore() {
        val source = pendingRestore ?: return
        viewModelScope.launch {
            _ui.update { it.copy(backupBusy = true, restorePreview = null, message = null, error = null) }
            runCatching { backups.restore(source) }
                .onSuccess { summary ->
                    pendingRestore = null
                    _ui.update { it.copy(backupBusy = false, message = "Inventaire restauré: ${summary.totalItems} fiche(s).") }
                }
                .onFailure { error -> _ui.update { it.copy(backupBusy = false, error = error.message ?: "Restauration impossible.") } }
        }
    }
}
