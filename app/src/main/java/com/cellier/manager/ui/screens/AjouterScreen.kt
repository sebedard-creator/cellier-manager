package com.cellier.manager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cellier.manager.R
import com.cellier.manager.data.BeverageType
import com.cellier.manager.ui.viewmodel.AjouterViewModel
import com.cellier.manager.util.PhotoCapture
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjouterScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    vm: AjouterViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Thème dynamique : bourgogne pour vin, jaune pour bière. Ré-appliqué quand
    // l'utilisateur toggle le type — toute la branche enfant réagit.
    com.cellier.manager.ui.theme.CellierManagerTheme(beverageType = state.type) {
        AjouterScreenContent(state = state, vm = vm, onBack = onBack, onSaved = onSaved)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AjouterScreenContent(
    state: com.cellier.manager.ui.viewmodel.AjouterUiState,
    vm: AjouterViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Naviguer quand la fiche est sauvegardée
    LaunchedEffect(state.savedItemId) {
        state.savedItemId?.let { onSaved(it) }
    }

    // Chemin de la photo en cours. `rememberSaveable` evite de le perdre si
    // Android recree l'ecran pendant que l'app camera est au premier plan.
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showOcrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val restoredPath = PhotoCapture.pendingCapturePath(context)
        if (state.photoPath.isBlank() && !restoredPath.isNullOrBlank()) {
            val restoredFile = PhotoCapture.waitForCapturedPhoto(restoredPath, timeoutMs = 1_200L)
            if (restoredFile != null) {
                vm.setPhotoPath(restoredFile.absolutePath)
                PhotoCapture.clearPendingCapture(context)
                pendingPhotoPath = null
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedPath = pendingPhotoPath ?: PhotoCapture.pendingCapturePath(context)
        coroutineScope.launch {
            val file = PhotoCapture.waitForCapturedPhoto(capturedPath)

            if ((success || file != null) && file != null) {
                vm.setPhotoPath(file.absolutePath)
            } else {
                capturedPath?.let(PhotoCapture::deletePhotoFile)
                Toast.makeText(
                    context,
                    context.getString(R.string.ajouter_photo_capture_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }

            PhotoCapture.clearPendingCapture(context)
            pendingPhotoPath = null
        }
    }

    val launchCameraCapture = {
        val (file, uri) = PhotoCapture.createPhotoFile(context)
        pendingPhotoPath = file.absolutePath
        PhotoCapture.rememberPendingCapture(context, file.absolutePath)
        try {
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            PhotoCapture.clearPendingCapture(context)
            pendingPhotoPath = null
            file.delete()
            Toast.makeText(
                context,
                context.getString(R.string.ajouter_photo_capture_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        }
    }

    val triggerPhoto = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.ajouter_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Segmented Vin / Bière
            TypeToggle(
                selected = state.type,
                onSelect = vm::setType
            )
            Spacer(Modifier.height(20.dp))

            // Zone photo
            PhotoCaptureArea(
                photoPath = state.photoPath,
                onClick = triggerPhoto
            )
            if (state.photoPath.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showOcrDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ocr_open))
                }
            }
            Spacer(Modifier.height(20.dp))

            // Producteur (autocomplete désactivé pour le moment - cf v1.1)
            LabeledTextField(
                label = stringResource(R.string.ajouter_field_producer),
                value = state.producer,
                onChange = vm::setProducer,
                capitalization = KeyboardCapitalization.Words
            )
            Spacer(Modifier.height(16.dp))

            // Nom du produit
            LabeledTextField(
                label = stringResource(R.string.ajouter_field_name),
                value = state.name,
                onChange = vm::setName,
                capitalization = KeyboardCapitalization.Words
            )
            Spacer(Modifier.height(16.dp))

            // Millésime
            val vintageLabel = if (state.type == BeverageType.VIN)
                stringResource(R.string.ajouter_field_vintage_required)
            else
                stringResource(R.string.ajouter_field_vintage)
            LabeledTextField(
                label = vintageLabel,
                value = state.vintage,
                onChange = vm::setVintage,
                keyboardType = KeyboardType.Number
            )

            // Sélecteur de couleur (vins uniquement)
            if (state.type == BeverageType.VIN) {
                Spacer(Modifier.height(16.dp))
                WineColorSelector(
                    selected = state.wineColor,
                    onSelect = vm::setWineColor
                )
            }

            Spacer(Modifier.height(28.dp))

            // Bouton Enregistrer
            Button(
                onClick = vm::save,
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    stringResource(R.string.ajouter_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showOcrDialog && state.photoPath.isNotBlank()) {
        OcrSelectionDialog(
            photoPath = state.photoPath,
            producerFilled = state.producer.isNotBlank(),
            nameFilled = state.name.isNotBlank(),
            vintageFilled = state.vintage.isNotBlank(),
            onDismiss = { showOcrDialog = false },
            onUseProducer = vm::setProducer,
            onUseName = vm::setName,
            onUseVintage = vm::setVintage
        )
    }
}

@Composable
private fun TypeToggle(
    selected: BeverageType,
    onSelect: (BeverageType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        @Composable
        fun Segment(value: BeverageType, label: String) {
            val isSelected = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent
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
        Segment(BeverageType.VIN, stringResource(R.string.consulter_wine))
        Segment(BeverageType.BIERE, stringResource(R.string.consulter_beer))
    }
}

@Composable
private fun PhotoCaptureArea(photoPath: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (photoPath.isBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.ajouter_photo_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ajouter_photo_subhint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            AsyncImage(
                model = File(photoPath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None
) {
    Column {
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
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = ImeAction.Next
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

/**
 * Sélecteur de couleur de vin : 4 segments Rouge / Blanc / Orange / Rosé.
 * Saisie optionnelle (selected peut être null = aucun choisi).
 */
@Composable
private fun WineColorSelector(
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        androidx.compose.material3.Surface(
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
                    androidx.compose.material3.Surface(
                        onClick = {
                            // Tap sur l'option déjà sélectionnée la désélectionne
                            onSelect(if (isSelected) null else color)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
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
                                style = MaterialTheme.typography.labelMedium,
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
