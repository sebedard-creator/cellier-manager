package com.cellier.manager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cellier.manager.R
import com.cellier.manager.data.BeverageType
import com.cellier.manager.data.CellarItem
import com.cellier.manager.data.MatchQuality
import com.cellier.manager.ui.theme.BeerAccent
import com.cellier.manager.ui.theme.BeerAccentContainer
import com.cellier.manager.ui.theme.BeerAccentOnContainer
import com.cellier.manager.ui.viewmodel.ConsulterViewModel
import com.cellier.manager.ui.viewmodel.SortOrder
import com.cellier.manager.ui.viewmodel.TypeFilter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsulterScreen(
    onSommelierClick: () -> Unit,
    onSearchesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDepletedClick: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    vm: ConsulterViewModel = viewModel()
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()

    // État pour afficher le bottom sheet de sélection d'un filtre
    var openFilter by remember { mutableStateOf<FilterCategory?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var mainMenuOpen by remember { mutableStateOf(false) }

    val beverageType = when (filters.type) {
        TypeFilter.BIERE -> BeverageType.BIERE
        TypeFilter.VIN -> BeverageType.VIN
        TypeFilter.TOUS -> null
    }

    com.cellier.manager.ui.theme.CellierManagerTheme(beverageType = beverageType) {
        Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.consulter_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    SommelierNavButton(onClick = onSommelierClick)
                },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = onSearchesClick) {
                        Icon(Icons.Default.ManageSearch, contentDescription = "Recherches")
                    }
                    Box {
                        IconButton(onClick = { mainMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Plus d’options")
                        }
                        DropdownMenu(expanded = mainMenuOpen, onDismissRequest = { mainMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("Épuisés") }, onClick = {
                                mainMenuOpen = false
                                onDepletedClick()
                            })
                            DropdownMenuItem(text = { Text("Réglages") }, onClick = {
                                mainMenuOpen = false
                                onSettingsClick()
                            })
                            DropdownMenuItem(text = { Text("Exporter le stock en CSV") }, onClick = {
                                mainMenuOpen = false
                            try {
                                com.cellier.manager.util.CsvExporter.exportAndShare(
                                    context = context,
                                    items = ui.allItems
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("ConsulterScreen", "Échec export CSV", e)
                            }
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Barre de recherche
            SearchBar(
                value = filters.search,
                onChange = vm::setSearch
            )
            Spacer(Modifier.height(12.dp))

            // Segmented Tous / Vin / Bière
            TypeSegmented(
                selected = filters.type,
                onSelect = vm::setType
            )
            Spacer(Modifier.height(14.dp))

            // Filtres par catégorie (2 lignes)
            FilterButtonsRows(
                filters = filters,
                onOpen = { openFilter = it },
                onClear = { vm.setFilterValue(it, null) }
            )
            Spacer(Modifier.height(12.dp))

            // Compteur + tri
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.consulter_results, ui.filteredItems.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                SortButton(
                    current = filters.sort,
                    expanded = sortMenuOpen,
                    onToggle = { sortMenuOpen = !sortMenuOpen },
                    onSelect = {
                        vm.setSort(it)
                        sortMenuOpen = false
                    },
                    onDismiss = { sortMenuOpen = false }
                )
            }
            Spacer(Modifier.height(12.dp))

            // Grille 2 colonnes
            if (ui.filteredItems.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    listItems(ui.filteredItems, key = { it.id }) { item ->
                        CellarItemCard(item = item, onClick = { onItemClick(item.id) })
                    }
                }
            }
        }
    }

    // Bottom sheet de sélection d'une valeur de filtre
    val selectedFilter = openFilter
    if (selectedFilter != null) {
        FilterPickerSheet(
            category = selectedFilter,
            currentValue = filters.valueFor(selectedFilter),
            availableValues = ui.availableValuesFor(selectedFilter),
            onDismiss = { openFilter = null },
            onSelect = { value ->
                vm.setFilterValue(selectedFilter, value)
                openFilter = null
            }
        )
    }
    }
}

// ================================================================
// Sous-composants
// ================================================================

@Composable
private fun SommelierNavButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        onClick = onClick,
        modifier = Modifier
            .padding(start = 10.dp)
            .heightIn(min = 30.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "BROMELIER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.consulter_search_hint)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun TypeSegmented(
    selected: TypeFilter,
    onSelect: (TypeFilter) -> Unit
) {
    val options = listOf(
        TypeFilter.TOUS to stringResource(R.string.consulter_all),
        TypeFilter.VIN to stringResource(R.string.consulter_wine),
        TypeFilter.BIERE to stringResource(R.string.consulter_beer)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

enum class FilterCategory { VINTAGE, PRODUCER, COUNTRY, GRAPE, REGION, WINE_COLOR }

@Composable
private fun FilterButtonsRows(
    filters: com.cellier.manager.ui.viewmodel.ConsulterFilters,
    onOpen: (FilterCategory) -> Unit,
    onClear: (FilterCategory) -> Unit
) {
    val showWineFilters = filters.type != TypeFilter.BIERE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                label = stringResource(R.string.consulter_filter_vintage),
                value = filters.vintage,
                onClick = { onOpen(FilterCategory.VINTAGE) },
                onClear = { onClear(FilterCategory.VINTAGE) }
            )
            FilterChip(
                label = stringResource(R.string.consulter_filter_producer),
                value = filters.producer,
                onClick = { onOpen(FilterCategory.PRODUCER) },
                onClear = { onClear(FilterCategory.PRODUCER) }
            )
            FilterChip(
                label = stringResource(R.string.consulter_filter_country),
                value = filters.country,
                onClick = { onOpen(FilterCategory.COUNTRY) },
                onClear = { onClear(FilterCategory.COUNTRY) }
            )
        }
        if (showWineFilters) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    label = stringResource(R.string.consulter_filter_grape),
                    value = filters.grape,
                    onClick = { onOpen(FilterCategory.GRAPE) },
                    onClear = { onClear(FilterCategory.GRAPE) }
                )
                FilterChip(
                    label = stringResource(R.string.consulter_filter_region),
                    value = filters.region,
                    onClick = { onOpen(FilterCategory.REGION) },
                    onClear = { onClear(FilterCategory.REGION) }
                )
                FilterChip(
                    label = stringResource(R.string.consulter_filter_wine_color),
                    value = filters.wineColor,
                    onClick = { onOpen(FilterCategory.WINE_COLOR) },
                    onClear = { onClear(FilterCategory.WINE_COLOR) }
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    value: String?,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    val active = value != null
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = if (!active) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(
                        start = 14.dp,
                        end = if (active && onClear != null) 6.dp else 14.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value ?: label,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!active || onClear == null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (active && onClear != null) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onClear)
                        .padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Effacer",
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SortButton(
    current: SortOrder,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val label = when (current) {
        SortOrder.AJOUT_RECENT -> stringResource(R.string.consulter_sort_date)
        SortOrder.MILLESIME_DESC -> stringResource(R.string.consulter_sort_vintage)
        SortOrder.PRODUCTEUR -> stringResource(R.string.consulter_sort_producer)
        SortOrder.NOM -> stringResource(R.string.consulter_sort_name)
    }
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = onToggle
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sort,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.consulter_sort_date)) },
                onClick = { onSelect(SortOrder.AJOUT_RECENT) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.consulter_sort_vintage)) },
                onClick = { onSelect(SortOrder.MILLESIME_DESC) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.consulter_sort_producer)) },
                onClick = { onSelect(SortOrder.PRODUCTEUR) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.consulter_sort_name)) },
                onClick = { onSelect(SortOrder.NOM) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPickerSheet(
    category: FilterCategory,
    currentValue: String?,
    availableValues: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val title = when (category) {
        FilterCategory.VINTAGE -> stringResource(R.string.consulter_filter_vintage)
        FilterCategory.PRODUCER -> stringResource(R.string.consulter_filter_producer)
        FilterCategory.COUNTRY -> stringResource(R.string.consulter_filter_country)
        FilterCategory.GRAPE -> stringResource(R.string.consulter_filter_grape)
        FilterCategory.REGION -> stringResource(R.string.consulter_filter_region)
        FilterCategory.WINE_COLOR -> stringResource(R.string.consulter_filter_wine_color)
    }

    // Search startsWith pour filtrer la liste. Insensible à la casse + accents.
    var search by remember(category) { mutableStateOf("") }
    val filteredValues = remember(availableValues, search) {
        if (search.isBlank()) availableValues
        else {
            val needle = normalize(search)
            availableValues.filter { normalize(it).startsWith(needle) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            // Search bar (filtre startsWith)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.consulter_filter_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                listItems(filteredValues) { value ->
                    val isSelected = value == currentValue
                    Surface(
                        onClick = { onSelect(value) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Normalisation pour search startsWith : accents + casse ignorés.
 */
private fun normalize(s: String): String {
    val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().trim()
}

@Composable
private fun CellarItemCard(item: CellarItem, onClick: () -> Unit) {
    val hasPerfectMatch = when (item.type) {
        BeverageType.VIN -> item.vivinoMatchQuality == MatchQuality.PERFECT
        BeverageType.BIERE -> item.untappdMatchQuality == MatchQuality.PERFECT
    }
    val vintageLabel = item.vintage?.trim()?.takeIf { it.isNotBlank() }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
        ) {
            // Photo à gauche (carrée)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.photoPath.isNotBlank()) {
                    AsyncImage(
                        model = File(item.photoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                TypeBadge(
                    type = item.type,
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                )
            }

            // Textes à droite — Arrangement.Center centre verticalement le bloc
            // textes, garantissant un padding haut/bas équilibré peu importe
            // qu'il y ait 3 ou 4 lignes (avec ou sans style).
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Produit
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (hasPerfectMatch) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.fiche_match_perfect),
                            tint = Color(0xFF76BE82),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                // Producteur
                Text(
                    text = item.producer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Style (AOC pour vins, ex. "Stout" pour bières)
                val styleText = item.style
                if (!styleText.isNullOrBlank()) {
                    Text(
                        text = styleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Année + pastille couleur (si vin et couleur définie)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (vintageLabel != null) {
                        Text(
                            text = vintageLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.type == BeverageType.VIN && item.wineColor != null) {
                        WineColorDot(color = item.wineColor)
                    }
                }
            }
        }
    }
}

/**
 * Pastille de couleur de vin. 10dp de diamètre, contour fin pour être visible
 * sur le blanc/rosé sur fond sombre.
 */
@Composable
private fun WineColorDot(color: com.cellier.manager.data.WineColor, size: Int = 10) {
    val fillColor = when (color) {
        com.cellier.manager.data.WineColor.ROUGE -> com.cellier.manager.ui.theme.WineColorRouge
        com.cellier.manager.data.WineColor.BLANC -> com.cellier.manager.ui.theme.WineColorBlanc
        com.cellier.manager.data.WineColor.JAUNE -> com.cellier.manager.ui.theme.WineColorJaune
        com.cellier.manager.data.WineColor.ORANGE -> com.cellier.manager.ui.theme.WineColorOrange
        com.cellier.manager.data.WineColor.ROSE -> com.cellier.manager.ui.theme.WineColorRose
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(fillColor)
            .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

@Composable
private fun TypeBadge(type: BeverageType, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (type) {
        BeverageType.VIN -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            stringResource(R.string.fiche_badge_wine)
        )
        BeverageType.BIERE -> Triple(
            BeerAccentContainer,
            BeerAccentOnContainer,
            stringResource(R.string.fiche_badge_beer)
        )
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.consulter_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.consulter_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Helpers pour lire/écrire la valeur du filtre selon la catégorie
private fun com.cellier.manager.ui.viewmodel.ConsulterFilters.valueFor(cat: FilterCategory): String? =
    when (cat) {
        FilterCategory.VINTAGE -> vintage
        FilterCategory.PRODUCER -> producer
        FilterCategory.COUNTRY -> country
        FilterCategory.GRAPE -> grape
        FilterCategory.REGION -> region
        FilterCategory.WINE_COLOR -> wineColor
    }

private fun com.cellier.manager.ui.viewmodel.ConsulterUiState.availableValuesFor(
    cat: FilterCategory
): List<String> = when (cat) {
    FilterCategory.VINTAGE -> availableVintages
    FilterCategory.PRODUCER -> availableProducers
    FilterCategory.COUNTRY -> availableCountries
    FilterCategory.GRAPE -> availableGrapes
    FilterCategory.REGION -> availableRegions
    FilterCategory.WINE_COLOR -> availableWineColors
}

private fun ConsulterViewModel.setFilterValue(cat: FilterCategory, value: String?) {
    when (cat) {
        FilterCategory.VINTAGE -> setVintage(value)
        FilterCategory.PRODUCER -> setProducer(value)
        FilterCategory.COUNTRY -> setCountry(value)
        FilterCategory.GRAPE -> setGrape(value)
        FilterCategory.REGION -> setRegion(value)
        FilterCategory.WINE_COLOR -> setWineColor(value)
    }
}
