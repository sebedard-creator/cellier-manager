package com.cellier.manager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cellier.manager.data.EnrichmentRequest
import com.cellier.manager.data.EnrichmentRequestState
import com.cellier.manager.ui.enrichmentStatus
import com.cellier.manager.ui.viewmodel.EnrichmentViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrichmentScreen(
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    vm: EnrichmentViewModel = viewModel()
) {
    val requests by vm.requests.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val activeIds = requests.filter { it.state in setOf(
        EnrichmentRequestState.LOCAL_PENDING.name,
        EnrichmentRequestState.SENDING.name,
        EnrichmentRequestState.WAITING_BROWSER.name,
    ) }.map(EnrichmentRequest::requestId)
    LaunchedEffect(activeIds) {
        do {
            now = System.currentTimeMillis()
            vm.refresh()
            if (activeIds.isNotEmpty()) delay(2_500)
        } while (activeIds.isNotEmpty())
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Recherches") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } },
            actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Synchroniser") } }
        )
    }) { padding ->
        if (requests.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Aucune recherche. Lance-en une depuis la fiche d’une bouteille.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(requests, key = EnrichmentRequest::requestId) { request ->
                    RequestCard(request, now, onReview = { onReview(request.requestId) }, onCancel = { vm.cancel(request.requestId) })
                }
            }
        }
    }
}

@Composable
private fun RequestCard(request: EnrichmentRequest, now: Long, onReview: () -> Unit, onCancel: () -> Unit) {
    val identity = runCatching { JSONObject(request.identitySnapshotJson) }.getOrNull()
    val status = enrichmentStatus(request, now)
    val ready = request.state == EnrichmentRequestState.READY_FOR_REVIEW.name
    val open = request.state in setOf(
        EnrichmentRequestState.LOCAL_PENDING.name, EnrichmentRequestState.SENDING.name,
        EnrichmentRequestState.WAITING_BROWSER.name, EnrichmentRequestState.READY_FOR_REVIEW.name
    )
    Card(modifier = Modifier.fillMaxWidth().then(if (ready) Modifier.clickable(onClick = onReview) else Modifier)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(identity?.optString("name").orEmpty().ifBlank { "Produit" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = {}, label = { Text(request.source) })
            }
            Text(identity?.optString("producer").orEmpty(), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.inProgress) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    status.text,
                    color = when {
                        status.problem -> MaterialTheme.colorScheme.error
                        status.completed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(request.updatedAt)), style = MaterialTheme.typography.labelSmall)
            request.lastErrorMessage?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ready) Button(onClick = onReview) { Text("Vérifier") }
                if (open) TextButton(onClick = onCancel) { Text("Annuler") }
            }
        }
    }
}
