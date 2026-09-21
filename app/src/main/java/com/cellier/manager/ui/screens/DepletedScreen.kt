package com.cellier.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cellier.manager.data.CellarItem
import com.cellier.manager.ui.viewmodel.DepletedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepletedScreen(onBack: () -> Unit, onItemClick: (Long) -> Unit, vm: DepletedViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Épuisés") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } }
        )
    }) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aucune fiche épuisée.")
            }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = CellarItem::id) { item ->
                Card(onClick = { onItemClick(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text(item.producer, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { vm.restock(item.id) }) { Text("+1") }
                    }
                }
            }
        }
    }
}
