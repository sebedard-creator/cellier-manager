package com.cellier.manager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cellier.manager.R
import com.cellier.manager.data.BeverageType
import com.cellier.manager.data.CellarItem
import com.cellier.manager.data.EnrichmentRequest
import com.cellier.manager.data.EnrichmentRequestState
import com.cellier.manager.data.EnrichmentSource
import com.cellier.manager.data.MatchQuality
import com.cellier.manager.data.SyncFailureReason
import com.cellier.manager.ui.enrichmentStatus
import com.cellier.manager.ui.theme.BeerAccentContainer
import com.cellier.manager.ui.theme.BeerAccentOnContainer
import com.cellier.manager.ui.viewmodel.FicheProduitViewModel
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheProduitScreen(
    itemId: Long,
    onBack: () -> Unit,
    vm: FicheProduitViewModel = viewModel()
) {
    val itemFlow = remember(itemId) { vm.observeItem(itemId) }
    val item by itemFlow.collectAsState(initial = null)

    // Thème dynamique : bourgogne pour vin, jaune pour bière. Tant que `item` est
    // null (chargement initial), on reste en bourgogne par défaut.
    com.cellier.manager.ui.theme.CellierManagerTheme(beverageType = item?.type) {
        FicheProduitScreenContent(
            itemId = itemId,
            item = item,
            onBack = onBack,
            vm = vm
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FicheProduitScreenContent(
    itemId: Long,
    item: CellarItem?,
    onBack: () -> Unit,
    vm: FicheProduitViewModel
) {
    val isEditing by vm.isEditing.collectAsStateWithLifecycle()
    val showDeleteDialog by vm.showDeleteDialog.collectAsStateWithLifecycle()
    val showResetDialog by vm.showResetDialog.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val requestFlow = remember(itemId) { vm.observeEnrichmentRequests(itemId) }
    val enrichmentRequests by requestFlow.collectAsState(initial = emptyList())
    var enrichmentClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val activeRequestIds = enrichmentRequests.filter {
        it.state in setOf(
            EnrichmentRequestState.LOCAL_PENDING.name,
            EnrichmentRequestState.SENDING.name,
            EnrichmentRequestState.WAITING_BROWSER.name,
        )
    }.map(EnrichmentRequest::requestId)

    LaunchedEffect(activeRequestIds) {
        do {
            enrichmentClock = System.currentTimeMillis()
            vm.refreshEnrichment()
            if (activeRequestIds.isNotEmpty()) delay(2_500)
        } while (activeRequestIds.isNotEmpty())
    }

    // Navigation automatique quand la fiche est supprimée
    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (item != null && !isEditing) {
                        IconButton(onClick = vm::askResetSources) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = stringResource(R.string.action_reset_sources)
                            )
                        }
                        IconButton(onClick = vm::toggleEdit) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_edit)
                            )
                        }
                        IconButton(onClick = vm::askDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete)
                            )
                        }
                    } else if (item != null && isEditing) {
                        IconButton(onClick = vm::toggleEdit) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_edit)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val currentItem = item
        if (currentItem == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (isEditing) {
                EditingContent(
                    item = currentItem,
                    padding = padding,
                    onSave = vm::saveEdits,
                    onCancel = vm::cancelEdit
                )
            } else {
                val saqSearching by vm.saqSearching.collectAsStateWithLifecycle()
                val vivinoSearching by vm.vivinoSearching.collectAsStateWithLifecycle()
                val untappdSearching by vm.untappdSearching.collectAsStateWithLifecycle()
                ReadingContent(
                    item = currentItem,
                    padding = padding,
                    onIncrement = { vm.incrementQuantity(currentItem.id) },
                    onDecrement = { vm.decrementQuantity(currentItem.id) },
                    onRetrySync = { vm.retryIndexation(currentItem.id) },
                    onConfirmVivinoMatch = { vm.confirmVivinoMatch(currentItem.id) },
                    onConfirmUntappdMatch = { vm.confirmUntappdMatch(currentItem.id) },
                    onSearchSaq = { vm.searchSaqManual(currentItem.id) },
                    onSearchVivino = { vm.searchVivinoManual(currentItem.id) },
                    onSearchUntappd = { vm.searchUntappdManual(currentItem.id) },
                    saqSearching = saqSearching,
                    vivinoSearching = vivinoSearching,
                    untappdSearching = untappdSearching,
                    enrichmentRequests = enrichmentRequests,
                    enrichmentClock = enrichmentClock,
                )
            }
        }
    }

    if (showDeleteDialog && item != null) {
        DeleteConfirmDialog(
            onConfirm = { vm.confirmDelete(item!!) },
            onDismiss = vm::cancelDelete
        )
    }

    if (showResetDialog && item != null) {
        ResetSourcesDialog(
            onConfirm = { vm.confirmResetSources(item.id) },
            onDismiss = vm::cancelResetSources
        )
    }
}

// ============================================================
// Mode LECTURE
// ============================================================

@Composable
private fun ReadingContent(
    item: CellarItem,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRetrySync: () -> Unit,
    onConfirmVivinoMatch: () -> Unit,
    onConfirmUntappdMatch: () -> Unit,
    onSearchSaq: () -> Unit,
    onSearchVivino: () -> Unit,
    onSearchUntappd: () -> Unit,
    saqSearching: Boolean = false,
    vivinoSearching: Boolean = false,
    untappdSearching: Boolean = false,
    enrichmentRequests: List<EnrichmentRequest> = emptyList(),
    enrichmentClock: Long = System.currentTimeMillis(),
) {
    var showFullPhoto by remember(item.photoPath) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // Hero photo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (item.photoPath.isNotBlank()) {
                        Modifier.clickable { showFullPhoto = true }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (item.photoPath.isNotBlank()) {
                AsyncImage(
                    model = File(item.photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))

            // Nom + badge type + producteur/millésime
            Text(
                text = item.name,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadgeLarge(type = item.type)
                Spacer(Modifier.width(10.dp))
                val subtitle = buildString {
                    append(item.producer)
                    item.vintage?.let { append(" · ").append(it) }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Pastille couleur (vins seulement, si définie)
                if (item.type == BeverageType.VIN && item.wineColor != null) {
                    Spacer(Modifier.width(8.dp))
                    WineColorDotLarge(color = item.wineColor)
                }
            }
            Spacer(Modifier.height(20.dp))

            // Badge sync pending / failed
            when {
                item.syncFailureReason != null -> SyncFailedBanner(
                    reason = item.syncFailureReason,
                    onRetry = onRetrySync
                )
                item.isSyncPending -> SyncPendingBanner()
                item.syncFailed -> SyncFailedBanner(
                    reason = item.syncFailureReason,
                    onRetry = onRetrySync
                )
                else -> {}
            }

            // Stepper quantité
            QuantityCard(
                quantity = item.quantity,
                onIncrement = onIncrement,
                onDecrement = onDecrement
            )
            Spacer(Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // Détails
            DetailsSection(item = item)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // Liens
            LinksSection(
                item = item,
                onConfirmVivinoMatch = onConfirmVivinoMatch,
                onConfirmUntappdMatch = onConfirmUntappdMatch,
                onSearchSaq = onSearchSaq,
                onSearchVivino = onSearchVivino,
                onSearchUntappd = onSearchUntappd,
                saqSearching = saqSearching,
                vivinoSearching = vivinoSearching,
                untappdSearching = untappdSearching,
                requests = enrichmentRequests,
                now = enrichmentClock,
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showFullPhoto && item.photoPath.isNotBlank()) {
        FullPhotoDialog(
            photoPath = item.photoPath,
            onDismiss = { showFullPhoto = false }
        )
    }
}

@Composable
private fun FullPhotoDialog(
    photoPath: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun TypeBadgeLarge(type: BeverageType) {
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
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun QuantityCard(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.fiche_quantity_remaining),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDecrement,
                    enabled = quantity > 0,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$quantity",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.fiche_quantity_bottles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(item: CellarItem) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item.country?.let {
            DetailRow(label = stringResource(R.string.fiche_country), value = it)
        }
        if (item.type == BeverageType.VIN) {
            item.region?.let {
                DetailRow(label = stringResource(R.string.fiche_region), value = it)
            }
            item.style?.let {
                DetailRow(label = stringResource(R.string.fiche_style), value = it)
            }
            if (item.grapes.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.fiche_grapes),
                    value = item.grapes.joinToString(", ")
                )
            }
            item.alcoholVolume?.let {
                DetailRow(
                    label = stringResource(R.string.fiche_alcohol),
                    value = stringResource(R.string.fiche_alcohol_value, it)
                )
            }
        } else {
            item.style?.let {
                DetailRow(label = stringResource(R.string.fiche_style), value = it)
            }
            item.alcoholVolume?.let {
                DetailRow(
                    label = stringResource(R.string.fiche_alcohol),
                    value = stringResource(R.string.fiche_alcohol_value, it)
                )
            }
            item.ibu?.let {
                DetailRow(
                    label = stringResource(R.string.fiche_ibu),
                    value = stringResource(R.string.fiche_ibu_value, it)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DetailRowMultiline(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun LinksSection(
    item: CellarItem,
    onConfirmVivinoMatch: () -> Unit,
    onConfirmUntappdMatch: () -> Unit,
    onSearchSaq: () -> Unit,
    onSearchVivino: () -> Unit,
    onSearchUntappd: () -> Unit,
    saqSearching: Boolean,
    vivinoSearching: Boolean,
    untappdSearching: Boolean,
    requests: List<EnrichmentRequest>,
    now: Long,
) {
    val context = LocalContext.current
    val vivinoRequest = requests.firstOrNull { it.source == EnrichmentSource.VIVINO.name }
    val untappdRequest = requests.firstOrNull { it.source == EnrichmentSource.UNTAPPD.name }
    val saqRequest = requests.firstOrNull { it.source == EnrichmentSource.SAQ.name }
    // Loupes toujours visibles (section toujours présente)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.fiche_links),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        EnrichmentProgressLine("Vivino", vivinoRequest, now)
        // Vivino : lien existant ou recherche manuelle si absent.
        if (item.vivinoUrl != null) {
            val isSearchUrl = item.vivinoUrl.isSearchResultUrl()
            MatchableLinkRow(
                label = if (isSearchUrl) {
                    stringResource(R.string.fiche_search_vivino)
                } else {
                    stringResource(R.string.fiche_link_vivino)
                },
                domain = if (isSearchUrl) "vivino.com/search" else "vivino.com",
                url = item.vivinoUrl,
                matchQuality = item.vivinoMatchQuality.takeUnless { isSearchUrl },
                onLinkClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(item.vivinoUrl))
                    )
                },
                onConfirmMatch = onConfirmVivinoMatch
            )
        } else {
            SearchLinkRow(
                label = stringResource(R.string.fiche_search_vivino),
                domain = "vivino.com",
                isSearching = vivinoSearching || vivinoRequest?.let { enrichmentStatus(it, now).inProgress } == true,
                onSearch = onSearchVivino
            )
        }

        EnrichmentProgressLine("Untappd", untappdRequest, now)
        // Untappd : lien existant ou recherche manuelle si absent.
        if (item.untappdUrl != null) {
            val isSearchUrl = item.untappdUrl.isSearchResultUrl()
            MatchableLinkRow(
                label = if (isSearchUrl) {
                    stringResource(R.string.fiche_search_untappd)
                } else {
                    stringResource(R.string.fiche_link_untappd)
                },
                domain = if (isSearchUrl) "untappd.com/search" else "untappd.com",
                url = item.untappdUrl,
                matchQuality = item.untappdMatchQuality.takeUnless { isSearchUrl },
                onLinkClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(item.untappdUrl))
                    )
                },
                onConfirmMatch = onConfirmUntappdMatch
            )
        } else {
            SearchLinkRow(
                label = stringResource(R.string.fiche_search_untappd),
                domain = "untappd.com",
                isSearching = untappdSearching || untappdRequest?.let { enrichmentStatus(it, now).inProgress } == true,
                onSearch = onSearchUntappd
            )
        }

        EnrichmentProgressLine("SAQ", saqRequest, now)
        // SAQ : lien ou loupe optionnelle (toujours)
        if (item.saqUrl != null) {
            LinkRow(
                label = stringResource(R.string.fiche_link_saq),
                domain = "saq.com",
                url = item.saqUrl,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(item.saqUrl))
                    )
                }
            )
        } else {
            SearchLinkRow(
                label = stringResource(R.string.fiche_search_saq),
                domain = "saq.com",
                isSearching = saqSearching || saqRequest?.let { enrichmentStatus(it, now).inProgress } == true,
                onSearch = onSearchSaq
            )
        }
    }
}

@Composable
private fun EnrichmentProgressLine(source: String, request: EnrichmentRequest?, now: Long) {
    if (request == null) return
    val status = enrichmentStatus(request, now)
    val color = when {
        status.problem -> MaterialTheme.colorScheme.error
        status.completed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (status.inProgress) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else if (status.completed) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        } else if (status.problem) {
            Icon(Icons.Default.Close, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text("$source · ${status.text}", style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * Row avec loupe 🔍 quand un lien optionnel n'est pas encore trouvé.
 * Cliquer lance la recherche manuelle. Pendant la recherche : spinner au lieu de loupe.
 */
@Composable
private fun SearchLinkRow(
    label: String,
    domain: String,
    isSearching: Boolean,
    onSearch: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = { if (!isSearching) onSearch() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Rechercher sur $domain",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, domain: String, url: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun String.isSearchResultUrl(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    val path = uri.path.orEmpty().lowercase()
    return path == "/search" || path.startsWith("/search/")
}

/**
 * Variant pour le lien Vivino avec :
 *  - couleur du libellé selon la qualité du match (jaune=APPROX, vert=PERFECT)
 *  - icône checkmark à droite :
 *    - PERFECT : filled vert (confirmé, non cliquable)
 *    - APPROX : outlined jaune (cliquable → confirm manuellement)
 *    - null (pas encore indexé) : pas d'icône
 */
@Composable
private fun MatchableLinkRow(
    label: String,
    domain: String,
    url: String,
    matchQuality: MatchQuality?,
    onLinkClick: () -> Unit,
    onConfirmMatch: () -> Unit
) {
    val greenPerfect = androidx.compose.ui.graphics.Color(0xFF76BE82)
    val yellowApprox = androidx.compose.ui.graphics.Color(0xFFE4A530)

    val qualityColor = when (matchQuality) {
        MatchQuality.PERFECT -> greenPerfect
        MatchQuality.APPROX -> yellowApprox
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val qualityLabel = when (matchQuality) {
        MatchQuality.PERFECT ->
            stringResource(R.string.fiche_match_perfect)
        MatchQuality.APPROX ->
            stringResource(R.string.fiche_match_approx)
        null -> null
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Partie cliquable : icône + texte (ouvre le lien)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onLinkClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row {
                        Text(
                            domain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (qualityLabel != null) {
                            Text(
                                " · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                qualityLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = qualityColor
                            )
                        }
                    }
                }
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Badge checkmark à droite (séparé pour être cliquable indépendamment)
            when (matchQuality) {
                MatchQuality.PERFECT -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.fiche_match_perfect),
                        tint = greenPerfect,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(28.dp)
                    )
                }
                MatchQuality.APPROX -> {
                    IconButton(
                        onClick = onConfirmMatch,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = stringResource(R.string.fiche_confirm_match),
                            tint = yellowApprox,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                null -> {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncPendingBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.fiche_indexing),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.fiche_indexing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyncFailedBanner(reason: SyncFailureReason?, onRetry: () -> Unit) {
    val title = when (reason) {
        SyncFailureReason.RATE_LIMIT -> stringResource(R.string.fiche_sync_rate_limited)
        SyncFailureReason.PROVIDER_BUSY -> stringResource(R.string.fiche_sync_provider_busy)
        SyncFailureReason.NETWORK -> stringResource(R.string.fiche_sync_network_error)
        SyncFailureReason.NO_RESULT, null -> stringResource(R.string.fiche_sync_failed)
    }
    val hint = when (reason) {
        SyncFailureReason.RATE_LIMIT -> stringResource(R.string.fiche_sync_rate_limited_hint)
        SyncFailureReason.PROVIDER_BUSY -> stringResource(R.string.fiche_sync_provider_busy_hint)
        SyncFailureReason.NETWORK -> stringResource(R.string.fiche_sync_network_error_hint)
        SyncFailureReason.NO_RESULT, null -> stringResource(R.string.fiche_sync_failed_hint)
    }
    val containerColor = when (reason) {
        SyncFailureReason.RATE_LIMIT -> MaterialTheme.colorScheme.primaryContainer
        SyncFailureReason.PROVIDER_BUSY -> MaterialTheme.colorScheme.primaryContainer
        SyncFailureReason.NETWORK -> MaterialTheme.colorScheme.errorContainer
        SyncFailureReason.NO_RESULT, null -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (reason) {
        SyncFailureReason.RATE_LIMIT -> MaterialTheme.colorScheme.onPrimaryContainer
        SyncFailureReason.PROVIDER_BUSY -> MaterialTheme.colorScheme.onPrimaryContainer
        SyncFailureReason.NETWORK -> MaterialTheme.colorScheme.onErrorContainer
        SyncFailureReason.NO_RESULT, null -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.fiche_sync_retry))
            }
        }
    }
}

// ============================================================
// Mode ÉDITION
// ============================================================

@Composable
private fun EditingContent(
    item: CellarItem,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onSave: (CellarItem) -> Unit,
    onCancel: () -> Unit
) {
    var producer by remember(item.id, item.producer) { mutableStateOf(item.producer) }
    var name by remember(item.id, item.name) { mutableStateOf(item.name) }
    var vintage by remember(item.id, item.vintage) { mutableStateOf(item.vintage ?: "") }
    var country by remember(item.id, item.country) { mutableStateOf(item.country ?: "") }
    var region by remember(item.id, item.region) { mutableStateOf(item.region ?: "") }
    var grapes by remember(item.id, item.grapes) { mutableStateOf(item.grapes.joinToString(", ")) }
    var style by remember(item.id, item.style) { mutableStateOf(item.style ?: "") }
    var alcohol by remember(item.id, item.alcoholVolume) { mutableStateOf(item.alcoholVolume?.toString() ?: "") }
    var ibu by remember(item.id, item.ibu) { mutableStateOf(item.ibu?.toString() ?: "") }
    var wineColor by remember(item.id, item.wineColor) { mutableStateOf(item.wineColor) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(12.dp))
        EditField(label = stringResource(R.string.ajouter_field_producer), value = producer) { producer = it }
        EditField(label = stringResource(R.string.ajouter_field_name), value = name) { name = it }
        EditField(label = stringResource(R.string.ajouter_field_vintage), value = vintage) { vintage = it }
        EditField(label = stringResource(R.string.fiche_country), value = country) { country = it }
        if (item.type == BeverageType.VIN) {
            EditField(label = stringResource(R.string.fiche_region), value = region) { region = it }
            EditField(label = stringResource(R.string.fiche_style), value = style) { style = it }
            EditField(label = stringResource(R.string.fiche_grapes), value = grapes) { grapes = it }
            EditField(label = stringResource(R.string.fiche_alcohol), value = alcohol) { alcohol = it }
            // Sélecteur de couleur (édition)
            Spacer(Modifier.height(8.dp))
            EditWineColorRow(selected = wineColor, onSelect = { wineColor = it })
        } else {
            EditField(label = stringResource(R.string.fiche_style), value = style) { style = it }
            EditField(label = stringResource(R.string.fiche_alcohol), value = alcohol) { alcohol = it }
            EditField(label = stringResource(R.string.fiche_ibu), value = ibu) { ibu = it }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.fiche_edit_cancel))
            }
            androidx.compose.material3.Button(
                onClick = {
                    onSave(
                        item.copy(
                            producer = producer.trim(),
                            name = name.trim(),
                            vintage = vintage.trim().takeIf { it.isNotBlank() },
                            country = country.trim().takeIf { it.isNotBlank() },
                            region = region.trim().takeIf { it.isNotBlank() },
                            grapes = grapes.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            style = style.trim().takeIf { it.isNotBlank() },
                            alcoholVolume = alcohol.replace(",", ".").toDoubleOrNull(),
                            ibu = ibu.trim().toIntOrNull(),
                            wineColor = if (item.type == BeverageType.VIN) wineColor else null
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.fiche_edit_save))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = label != "Cépages",
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

// ============================================================
// Dialog confirmation suppression
// ============================================================

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fiche_delete_title)) },
        text = { Text(stringResource(R.string.fiche_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.fiche_delete_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.fiche_delete_cancel))
            }
        }
    )
}

@Composable
private fun ResetSourcesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fiche_reset_title)) },
        text = { Text(stringResource(R.string.fiche_reset_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.fiche_reset_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.fiche_delete_cancel))
            }
        }
    )
}

/**
 * Pastille de couleur de vin pour la fiche détail (12dp, plus visible que celle
 * du listing). Contour fin pour rester visible sur les couleurs claires (Blanc/Rosé).
 */
@Composable
private fun WineColorDotLarge(color: com.cellier.manager.data.WineColor) {
    val fillColor = when (color) {
        com.cellier.manager.data.WineColor.ROUGE -> com.cellier.manager.ui.theme.WineColorRouge
        com.cellier.manager.data.WineColor.BLANC -> com.cellier.manager.ui.theme.WineColorBlanc
        com.cellier.manager.data.WineColor.JAUNE -> com.cellier.manager.ui.theme.WineColorJaune
        com.cellier.manager.data.WineColor.ORANGE -> com.cellier.manager.ui.theme.WineColorOrange
        com.cellier.manager.data.WineColor.ROSE -> com.cellier.manager.ui.theme.WineColorRose
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(fillColor)
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline,
                androidx.compose.foundation.shape.CircleShape
            )
    )
}

/**
 * Sélecteur de couleur pour le mode édition de la fiche.
 * Layout en row de 4 pastilles cliquables.
 */
@Composable
private fun EditWineColorRow(
    selected: com.cellier.manager.data.WineColor?,
    onSelect: (com.cellier.manager.data.WineColor?) -> Unit
) {
    val options = listOf(
        Triple(
            com.cellier.manager.data.WineColor.ROUGE,
            "Rouge",
            com.cellier.manager.ui.theme.WineColorRouge
        ),
        Triple(
            com.cellier.manager.data.WineColor.BLANC,
            "Blanc",
            com.cellier.manager.ui.theme.WineColorBlanc
        ),
        Triple(
            com.cellier.manager.data.WineColor.JAUNE,
            "Jaune",
            com.cellier.manager.ui.theme.WineColorJaune
        ),
        Triple(
            com.cellier.manager.data.WineColor.ORANGE,
            "Orange",
            com.cellier.manager.ui.theme.WineColorOrange
        ),
        Triple(
            com.cellier.manager.data.WineColor.ROSE,
            "Rosé",
            com.cellier.manager.ui.theme.WineColorRose
        )
    )
    Column {
        Text(
            stringResource(R.string.ajouter_field_wine_color),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (color, label, dotColor) ->
                    val isSelected = selected == color
                    Surface(
                        onClick = { onSelect(if (isSelected) null else color) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
