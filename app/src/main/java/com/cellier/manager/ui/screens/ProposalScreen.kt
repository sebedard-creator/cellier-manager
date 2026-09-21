package com.cellier.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cellier.manager.ui.viewmodel.ProposalViewModel
import com.cellier.manager.ui.viewmodel.ProposedField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalScreen(requestId: String, onBack: () -> Unit, vm: ProposalViewModel = viewModel()) {
    LaunchedEffect(requestId) { vm.load(requestId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Comparer les renseignements") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } }
        )
    }) { padding ->
        if (ui.loading) {
            Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.padding(32.dp)) }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(ui.item?.let { "${it.producer} — ${it.name}" }.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text("Source : ${ui.source}", style = MaterialTheme.typography.bodySmall)
                    if (ui.vintageAssessment == "MISMATCH") {
                        Text("Le millésime affiché est différent de celui de ta bouteille.", color = MaterialTheme.colorScheme.error)
                    }
                    if (ui.identityAssessment !in setOf("PLAUSIBLE", "MATCH")) {
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(checked = ui.productConfirmed, onCheckedChange = vm::confirmProduct)
                            Text("Je confirme qu’il s’agit du bon produit", Modifier.padding(top = 12.dp))
                        }
                    }
                }
                items(ui.fields, key = ProposedField::name) { field ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp)) {
                            Checkbox(checked = field.selected, onCheckedChange = { vm.toggle(field.name) })
                            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(field.label, fontWeight = FontWeight.SemiBold)
                                Text("Actuel : ${field.currentValue.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                                Text("Proposé : ${field.proposedValue}")
                                field.evidence?.let { Text("Preuve : $it", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
                item {
                    ui.message?.let { Text(it, color = if (ui.resolved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = vm::apply,
                        enabled = !ui.applying && !ui.resolved && ui.fields.any(ProposedField::selected),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (ui.applying) "Application…" else "Appliquer les champs choisis") }
                    TextButton(onClick = vm::reject, enabled = !ui.applying && !ui.resolved, modifier = Modifier.fillMaxWidth()) {
                        Text("Refuser ce résultat")
                    }
                }
            }
        }
    }
}
