package com.cellier.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellier.manager.data.CellarItem
import com.cellier.manager.repository.CellarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DepletedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CellarRepository.getInstance(application)
    val items: StateFlow<List<CellarItem>> = repository.observeDepleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restock(id: Long) { viewModelScope.launch { repository.incrementQuantity(id) } }
}
