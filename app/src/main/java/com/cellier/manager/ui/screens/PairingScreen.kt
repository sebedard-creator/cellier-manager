package com.cellier.manager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cellier.manager.ui.viewmodel.PairingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onBack: () -> Unit, vm: PairingViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var apiKey by remember { mutableStateOf("") }
    var model by remember(ui.sommelierModel) { mutableStateOf(ui.sommelierModel) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Fichier vide.")
        }.onSuccess(vm::importPairingFile)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::inspectBackup)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Réglages") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Compagnon Windows", style = MaterialTheme.typography.titleLarge)
            if (ui.pairedServer == null) {
                Text("Aucun ordinateur jumelé. Sur l’ordinateur, démarre le compagnon puis crée le fichier de jumelage Android.")
                Button(onClick = { launcher.launch(arrayOf("application/json", "text/json", "*/*")) }, enabled = !ui.busy) {
                    Text(if (ui.busy) "Jumelage…" else "Importer le fichier de jumelage")
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ui.pairedServer ?: "Mon ordinateur", style = MaterialTheme.typography.titleMedium)
                        Text(ui.pairedUrl.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = vm::synchronize) { Text("Synchroniser maintenant") }
                OutlinedButton(onClick = { launcher.launch(arrayOf("application/json", "text/json", "*/*")) }) { Text("Changer d’ordinateur") }
                TextButton(onClick = vm::disconnect) { Text("Déconnecter cet ordinateur") }
            }
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("Bromelier", style = MaterialTheme.typography.titleLarge)
            Text(if (ui.sommelierConfigured) "Clé configurée sur cet appareil." else "Ajoute ta clé Anthropic pour activer les conseils.")
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Clé Anthropic") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Modèle") }, singleLine = true
            )
            Button(onClick = { vm.saveSommelier(apiKey, model); apiKey = "" }, enabled = apiKey.isNotBlank()) {
                Text("Enregistrer la clé")
            }
            if (ui.sommelierConfigured) TextButton(onClick = vm::clearSommelier) { Text("Supprimer la clé Bromelier") }
            HorizontalDivider()
            Text("Sauvegarde complète", style = MaterialTheme.typography.titleLarge)
            Text("La sauvegarde contient toutes les fiches, y compris les épuisées, leurs renseignements et leurs photos. Elle n’est pas chiffrée.")
            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.CANADA_FRENCH).format(Date())
                    backupLauncher.launch("cellier-$stamp.cellierbackup")
                }, enabled = !ui.backupBusy
            ) { Text("Créer une sauvegarde") }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                enabled = !ui.backupBusy
            ) { Text("Restaurer une sauvegarde") }
            if (ui.backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider()
            Text("L’inventaire, les photos et les quantités restent utilisables sans ordinateur.")
        }
    }

    ui.restorePreview?.let { summary ->
        AlertDialog(
            onDismissRequest = vm::dismissRestore,
            title = { Text("Remplacer l’inventaire ?") },
            text = { Text(
                "Cette sauvegarde contient ${summary.totalItems} fiche(s), dont ${summary.depletedItems} épuisée(s), " +
                    "et ${summary.photosPresent} photo(s). L’inventaire actuel sera remplacé après création d’une sauvegarde de sécurité."
            ) },
            confirmButton = { Button(onClick = vm::confirmRestore) { Text("Restaurer") } },
            dismissButton = { TextButton(onClick = vm::dismissRestore) { Text("Annuler") } }
        )
    }
}
