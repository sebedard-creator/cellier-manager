package com.cellier.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cellier.manager.data.WineColor
import com.cellier.manager.sommelier.SommelierChatMessage
import com.cellier.manager.sommelier.SommelierRole
import com.cellier.manager.ui.viewmodel.SommelierDrinkFilter
import com.cellier.manager.ui.viewmodel.SommelierFilters
import com.cellier.manager.ui.viewmodel.SommelierMood
import com.cellier.manager.ui.viewmodel.SommelierUiState
import com.cellier.manager.ui.viewmodel.SommelierViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SommelierScreen(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    vm: SommelierViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Bromelier",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (state.isChatStarted) {
                        IconButton(onClick = vm::resetChat) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Recommencer")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (state.isChatStarted) {
                ChatInput(
                    enabled = !state.isSending,
                    onSend = vm::sendMessage
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isChatStarted) {
            ChatContent(
                state = state,
                onItemClick = onItemClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            PrefilterContent(
                state = state,
                onDrink = vm::setDrinkFilter,
                onWineColor = vm::setWineColor,
                onMood = vm::setMood,
                onDesire = vm::setDesire,
                onStart = vm::startChat,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun PrefilterContent(
    state: SommelierUiState,
    onDrink: (SommelierDrinkFilter) -> Unit,
    onWineColor: (WineColor?) -> Unit,
    onMood: (SommelierMood?) -> Unit,
    onDesire: (String) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = state.filters
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Qu'est-ce qu'on ouvre?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Le cellier est filtre localement avant d'envoyer la shortlist a Claude.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            FilterSection(title = "Type") {
                ChipRow {
                    DrinkChip("Tous", filters.drink == SommelierDrinkFilter.TOUS) {
                        onDrink(SommelierDrinkFilter.TOUS)
                    }
                    DrinkChip("Vin", filters.drink == SommelierDrinkFilter.VIN) {
                        onDrink(SommelierDrinkFilter.VIN)
                    }
                    DrinkChip("Biere", filters.drink == SommelierDrinkFilter.BIERE) {
                        onDrink(SommelierDrinkFilter.BIERE)
                    }
                }
            }
        }

        if (filters.drink != SommelierDrinkFilter.BIERE) {
            item {
                FilterSection(title = "Couleur") {
                    ChipRow {
                        WineColorChip("Toutes", filters.wineColor == null) {
                            onWineColor(null)
                        }
                        WineColor.values().forEach { color ->
                            WineColorChip(color.label(), filters.wineColor == color) {
                                onWineColor(color)
                            }
                        }
                    }
                }
            }
        }

        item {
            FilterSection(title = "Envie") {
                ChipRow {
                    MoodChip("Libre", filters.mood == null) {
                        onMood(null)
                    }
                    SommelierMood.values().forEach { mood ->
                        MoodChip(mood.label(), filters.mood == mood) {
                            onMood(mood)
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = filters.desire,
                onValueChange = onDesire,
                label = { Text("Ce que tu as envie de boire") },
                placeholder = { Text("Ex.: je veux du vin blanc leger ce soir") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "${state.candidateItems.size} bouteille(s) disponible(s)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Seules ces fiches seront transmises au bromelier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.error != null) {
            item { ErrorBanner(state.error) }
        }

        item {
            Button(
                onClick = onStart,
                enabled = state.candidateItems.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text("Demarrer le bromelier")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun DrinkChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun WineColorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun MoodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ChatContent(
    state: SommelierUiState,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.isSending) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${state.sessionItems.size} bouteille(s) dans la shortlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        items(state.messages.size) { index ->
            MessageBubble(
                message = state.messages[index],
                availableItems = state.sessionItems,
                onItemClick = onItemClick
            )
        }

        if (state.isSending) {
            item {
                TypingBubble()
            }
        }

        if (state.error != null) {
            item { ErrorBanner(state.error) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MessageBubble(
    message: SommelierChatMessage,
    availableItems: List<com.cellier.manager.data.CellarItem>,
    onItemClick: (Long) -> Unit
) {
    val isUser = message.role == SommelierRole.USER
    val recommended = if (isUser) emptyList() else {
        Regex("(?i)(?:id\\s*=\\s*|fiche\\s+)(\\d+)").findAll(message.text)
            .mapNotNull { match -> match.groupValues[1].toLongOrNull() }
            .distinct().mapNotNull { id -> availableItems.firstOrNull { it.id == id } }.toList()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.displayText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                recommended.forEach { item ->
                    TextButton(onClick = { onItemClick(item.id) }) {
                        Text("Ouvrir ${item.producer} — ${item.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                "Le bromelier reflechit...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ChatInput(
    enabled: Boolean,
    onSend: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                enabled = enabled,
                placeholder = { Text("Repondre au bromelier") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (value.isNotBlank()) {
                            onSend(value)
                            value = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (value.isNotBlank()) {
                        onSend(value)
                        value = ""
                    }
                },
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Envoyer")
            }
        }
    }
}

private fun WineColor.label(): String = when (this) {
    WineColor.ROUGE -> "Rouge"
    WineColor.BLANC -> "Blanc"
    WineColor.JAUNE -> "Jaune"
    WineColor.ORANGE -> "Orange"
    WineColor.ROSE -> "Rose"
}

private fun SommelierMood.label(): String = when (this) {
    SommelierMood.LEGER -> "Leger"
    SommelierMood.FRAIS -> "Frais"
    SommelierMood.FUNKY -> "Funky"
    SommelierMood.CLASSIQUE -> "Classique"
    SommelierMood.REPAS -> "Repas"
    SommelierMood.DECOUVERTE -> "Decouverte"
}

private fun SommelierChatMessage.displayText(): String {
    if (role == SommelierRole.USER) return text
    return text
        .replace("**", "")
        .replace(Regex("(?m)^-\\s+"), "• ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
