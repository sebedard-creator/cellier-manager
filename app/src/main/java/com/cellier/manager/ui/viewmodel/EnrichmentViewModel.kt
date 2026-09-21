package com.cellier.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellier.manager.data.EnrichmentRequest
import com.cellier.manager.repository.CellarRepository
import com.cellier.manager.work.CompanionSyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EnrichmentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CellarRepository.getInstance(application)

    val requests: StateFlow<List<EnrichmentRequest>> = repository.observeEnrichmentRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() = CompanionSyncWorker.schedule(getApplication())

    fun cancel(requestId: String) {
        viewModelScope.launch { repository.cancelEnrichment(requestId) }
    }
}
