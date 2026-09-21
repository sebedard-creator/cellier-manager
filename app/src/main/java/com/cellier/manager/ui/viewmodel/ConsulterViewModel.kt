package com.cellier.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellier.manager.data.BeverageType
import com.cellier.manager.data.CellarItem
import com.cellier.manager.data.WineColor
import com.cellier.manager.repository.CellarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class TypeFilter { TOUS, VIN, BIERE }

enum class SortOrder { AJOUT_RECENT, MILLESIME_DESC, PRODUCTEUR, NOM }

data class ConsulterFilters(
    val search: String = "",
    val type: TypeFilter = TypeFilter.TOUS,
    val vintage: String? = null,
    val producer: String? = null,
    val country: String? = null,
    val grape: String? = null,
    val region: String? = null,
    val wineColor: String? = null,
    val sort: SortOrder = SortOrder.AJOUT_RECENT
)

data class ConsulterUiState(
    val allItems: List<CellarItem> = emptyList(),
    val filteredItems: List<CellarItem> = emptyList(),
    val availableVintages: List<String> = emptyList(),
    val availableProducers: List<String> = emptyList(),
    val availableCountries: List<String> = emptyList(),
    val availableGrapes: List<String> = emptyList(),
    val availableRegions: List<String> = emptyList(),
    val availableWineColors: List<String> = emptyList()
)

class ConsulterViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CellarRepository.getInstance(app)

    private val _filters = MutableStateFlow(ConsulterFilters())
    val filters: StateFlow<ConsulterFilters> = _filters.asStateFlow()

    val uiState: StateFlow<ConsulterUiState> = combine(
        repo.observeAll(),
        _filters
    ) { items, f ->
        val filtered = applyFilters(items, f)
        
        // On pré-filtre les items disponibles pour les dropdowns selon le type principal (VIN/BIERE)
        val typeFilteredItems = when (f.type) {
            TypeFilter.TOUS -> items
            TypeFilter.VIN -> items.filter { it.type == BeverageType.VIN }
            TypeFilter.BIERE -> items.filter { it.type == BeverageType.BIERE }
        }

        ConsulterUiState(
            allItems = items,
            filteredItems = filtered,
            // Les dropdowns de filtres s'auto-populent à partir des fiches
            // actuellement visibles (hors le filtre correspondant).
            availableVintages = typeFilteredItems.mapNotNull { it.vintage.cleanFilterValue() }
                .distinct()
                .sortedDescending(),
            availableProducers = typeFilteredItems.mapNotNull { it.producer.cleanFilterValue() }
                .distinct()
                .sorted(),
            availableCountries = typeFilteredItems.mapNotNull { it.country.cleanFilterValue() }
                .distinct()
                .sorted(),
            availableGrapes = typeFilteredItems.flatMap { item -> item.grapes.mapNotNull { it.cleanFilterValue() } }
                .distinct()
                .sorted(),
            availableRegions = typeFilteredItems.mapNotNull { it.region.cleanFilterValue() }
                .distinct()
                .sorted(),
            availableWineColors = typeFilteredItems
                .filter { it.type == BeverageType.VIN }
                .mapNotNull { it.wineColor?.displayName() }
                .distinct()
                .sorted()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConsulterUiState())

    private fun applyFilters(items: List<CellarItem>, f: ConsulterFilters): List<CellarItem> {
        var result = items

        // Recherche texte sur nom + producteur
        if (f.search.isNotBlank()) {
            val q = f.search.trim().lowercase()
            result = result.filter {
                it.name.lowercase().contains(q) || it.producer.lowercase().contains(q)
            }
        }

        // Type
        result = when (f.type) {
            TypeFilter.TOUS -> result
            TypeFilter.VIN -> result.filter { it.type == BeverageType.VIN }
            TypeFilter.BIERE -> result.filter { it.type == BeverageType.BIERE }
        }

        // Filtres catégorie
        f.vintage.cleanFilterValue()?.let { v ->
            result = result.filter { it.vintage.cleanFilterValue() == v }
        }
        f.producer.cleanFilterValue()?.let { p ->
            result = result.filter { it.producer.cleanFilterValue() == p }
        }
        f.country.cleanFilterValue()?.let { c ->
            result = result.filter { it.country.cleanFilterValue() == c }
        }
        f.grape.cleanFilterValue()?.let { g ->
            result = result.filter { item -> item.grapes.any { it.cleanFilterValue() == g } }
        }
        f.region.cleanFilterValue()?.let { r ->
            result = result.filter { it.region.cleanFilterValue() == r }
        }
        f.wineColor?.let { color -> result = result.filter { it.wineColor?.displayName() == color } }

        // Tri
        return when (f.sort) {
            SortOrder.AJOUT_RECENT -> result.sortedByDescending { it.createdAt }
            SortOrder.MILLESIME_DESC -> result.sortedByDescending { it.vintage ?: "" }
            SortOrder.PRODUCTEUR -> result.sortedBy { it.producer.lowercase() }
            SortOrder.NOM -> result.sortedBy { it.name.lowercase() }
        }
    }

    fun setSearch(v: String) = update { it.copy(search = v) }
    fun setType(v: TypeFilter) = update {
        if (v != it.type) {
            it.copy(
                type = v,
                vintage = null,
                producer = null,
                country = null,
                grape = null,
                region = null,
                wineColor = null
            )
        } else {
            it
        }
    }
    fun setVintage(v: String?) = update { it.copy(vintage = v) }
    fun setProducer(v: String?) = update { it.copy(producer = v) }
    fun setCountry(v: String?) = update { it.copy(country = v) }
    fun setGrape(v: String?) = update { it.copy(grape = v) }
    fun setRegion(v: String?) = update { it.copy(region = v) }
    fun setWineColor(v: String?) = update { it.copy(wineColor = v) }
    fun setSort(v: SortOrder) = update { it.copy(sort = v) }

    fun clearAllFilters() {
        _filters.value = ConsulterFilters()
    }

    private fun update(transform: (ConsulterFilters) -> ConsulterFilters) {
        _filters.value = transform(_filters.value)
    }

    private fun String?.cleanFilterValue(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }

    private fun WineColor.displayName(): String = when (this) {
        WineColor.ROUGE -> "Rouge"
        WineColor.BLANC -> "Blanc"
        WineColor.JAUNE -> "Jaune"
        WineColor.ORANGE -> "Orange"
        WineColor.ROSE -> "Rose"
    }
}
