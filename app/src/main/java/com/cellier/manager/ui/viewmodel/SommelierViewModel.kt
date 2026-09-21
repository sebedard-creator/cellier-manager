package com.cellier.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellier.manager.data.BeverageType
import com.cellier.manager.data.CellarItem
import com.cellier.manager.data.WineColor
import com.cellier.manager.repository.CellarRepository
import com.cellier.manager.sommelier.ClaudeSommelierClient
import com.cellier.manager.sommelier.SommelierChatMessage
import com.cellier.manager.sommelier.SommelierRole
import com.cellier.manager.sommelier.SommelierStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

enum class SommelierDrinkFilter { TOUS, VIN, BIERE }

enum class SommelierMood {
    LEGER,
    FRAIS,
    FUNKY,
    CLASSIQUE,
    REPAS,
    DECOUVERTE
}

data class SommelierFilters(
    val drink: SommelierDrinkFilter = SommelierDrinkFilter.TOUS,
    val wineColor: WineColor? = null,
    val mood: SommelierMood? = null,
    val desire: String = ""
)

data class SommelierUiState(
    val filters: SommelierFilters = SommelierFilters(),
    val candidateItems: List<CellarItem> = emptyList(),
    val sessionItems: List<CellarItem> = emptyList(),
    val messages: List<SommelierChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val isChatStarted: Boolean = false
)

private data class SommelierRuntimeState(
    val messages: List<SommelierChatMessage>,
    val isSending: Boolean,
    val error: String?,
    val sessionItems: List<CellarItem>?
)

class SommelierViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CellarRepository.getInstance(app)
    private val sommelierStore = SommelierStore(app)

    private val allItems = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filters = MutableStateFlow(SommelierFilters())
    private val _messages = MutableStateFlow<List<SommelierChatMessage>>(emptyList())
    private val _isSending = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _sessionItems = MutableStateFlow<List<CellarItem>?>(null)
    private var sessionGeneration = 0L

    val filters: StateFlow<SommelierFilters> = _filters.asStateFlow()

    private val runtime = combine(
        _messages,
        _isSending,
        _error,
        _sessionItems
    ) { messages, isSending, error, sessionItems ->
        SommelierRuntimeState(messages, isSending, error, sessionItems)
    }

    val uiState: StateFlow<SommelierUiState> = combine(
        allItems,
        _filters,
        runtime
    ) { items, filters, runtime ->
        val candidates = runtime.sessionItems ?: filterItems(items, filters)
        SommelierUiState(
            filters = filters,
            candidateItems = candidates,
            sessionItems = runtime.sessionItems.orEmpty(),
            messages = runtime.messages,
            isSending = runtime.isSending,
            error = runtime.error,
            isChatStarted = runtime.sessionItems != null
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SommelierUiState())

    fun setDrinkFilter(value: SommelierDrinkFilter) {
        _filters.value = _filters.value.copy(
            drink = value,
            wineColor = if (value == SommelierDrinkFilter.BIERE) null else _filters.value.wineColor
        )
        _error.value = null
    }

    fun setWineColor(value: WineColor?) {
        _filters.value = _filters.value.copy(
            drink = if (value != null && _filters.value.drink == SommelierDrinkFilter.BIERE) {
                SommelierDrinkFilter.VIN
            } else {
                _filters.value.drink
            },
            wineColor = value
        )
        _error.value = null
    }

    fun setMood(value: SommelierMood?) {
        _filters.value = _filters.value.copy(mood = value)
        _error.value = null
    }

    fun setDesire(value: String) {
        _filters.value = _filters.value.copy(desire = value)
        _error.value = null
    }

    fun startChat() {
        if (_isSending.value) return

        val snapshot = filterItems(allItems.value, _filters.value)
        if (snapshot.isEmpty()) {
            _error.value = "Aucune bouteille disponible pour ces filtres."
            return
        }

        sessionGeneration += 1
        _sessionItems.value = snapshot
        _messages.value = emptyList()
        val opening = _filters.value.desire.trim()
            .ifBlank { "J'aimerais choisir une bouteille a ouvrir ce soir." }
        sendUserMessage(opening)
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return
        if (_sessionItems.value == null) {
            startChat()
            return
        }
        sendUserMessage(trimmed)
    }

    fun resetChat() {
        sessionGeneration += 1
        _sessionItems.value = null
        _messages.value = emptyList()
        _isSending.value = false
        _error.value = null
    }

    private fun sendUserMessage(text: String) {
        val sessionItems = _sessionItems.value ?: return
        val userMessage = SommelierChatMessage(SommelierRole.USER, text)
        val conversation = _messages.value + userMessage
        _messages.value = conversation
        _isSending.value = true
        _error.value = null
        val generation = sessionGeneration

        viewModelScope.launch {
            try {
                val configuration = sommelierStore.load()
                    ?: throw IllegalStateException("Configure la clé de Bromelier dans les réglages.")
                val claude = ClaudeSommelierClient(configuration.apiKey, configuration.model)
                val response = claude.chat(
                    systemPrompt = buildSystemPrompt(sessionItems, _filters.value),
                    messages = conversation
                )
                if (generation == sessionGeneration) {
                    _messages.value = conversation + SommelierChatMessage(
                        role = SommelierRole.ASSISTANT,
                        text = response.text
                    )
                }
            } catch (e: Exception) {
                if (generation == sessionGeneration) _error.value = e.message ?: "Erreur Claude inconnue."
            } finally {
                if (generation == sessionGeneration) _isSending.value = false
            }
        }
    }

    private fun filterItems(items: List<CellarItem>, filters: SommelierFilters): List<CellarItem> {
        val normalizedDesire = filters.desire.normalized()
        val desireWords = normalizedDesire.split(Regex("[^a-z0-9]+"))
            .filter(String::isNotBlank).toSet()
        val inferredDrink = when {
            filters.drink != SommelierDrinkFilter.TOUS -> filters.drink
            "biere" in desireWords || "beer" in desireWords ->
                SommelierDrinkFilter.BIERE
            desireWords.any { it in setOf("vin", "blanc", "rouge", "rose", "orange") } -> SommelierDrinkFilter.VIN
            else -> SommelierDrinkFilter.TOUS
        }
        val inferredColor = filters.wineColor ?: when {
            "blanc" in desireWords -> WineColor.BLANC
            "rouge" in desireWords -> WineColor.ROUGE
            "jaune" in desireWords -> WineColor.JAUNE
            "orange" in desireWords -> WineColor.ORANGE
            "rose" in desireWords -> WineColor.ROSE
            else -> null
        }

        return items
            .asSequence()
            .filter { it.quantity > 0 }
            .filter { item ->
                when (inferredDrink) {
                    SommelierDrinkFilter.TOUS -> true
                    SommelierDrinkFilter.VIN -> item.type == BeverageType.VIN
                    SommelierDrinkFilter.BIERE -> item.type == BeverageType.BIERE
                }
            }
            .filter { item ->
                inferredColor == null || item.wineColor == inferredColor
            }
            .sortedWith(
                compareByDescending<CellarItem> { metadataScore(it) }
                    .thenByDescending { it.createdAt }
            )
            .toList()
    }

    private fun buildSystemPrompt(items: List<CellarItem>, filters: SommelierFilters): String {
        val filterSummary = buildList {
            add("type=${filters.drink.label()}")
            filters.wineColor?.let { add("couleur=${it.label()}") }
            filters.mood?.let { add("envie=${it.label()}") }
            filters.desire.trim().takeIf { it.isNotBlank() }?.let { add("demande=$it") }
        }.joinToString(" | ")

        return """
            Tu es Bromelier, le conseiller personnel de Sebastien pour son application Cellier Manager.
            Reponds en francais, avec un ton naturel, concis, utile et un peu complice.

            Regles strictes:
            - Recommande uniquement des bouteilles presentes dans l'inventaire ci-dessous.
            - Cite toujours l'id exact de la fiche quand tu recommandes une bouteille.
            - Si aucune bouteille ne convient parfaitement, dis-le et propose le meilleur compromis.
            - Si la demande est vague, pose une seule question courte avant de trop conclure.
            - N'invente jamais un producteur, une cuvee, un millesime ou un lien.
            - Favorise une recommandation principale et deux alternatives quand c'est utile.
            - N'utilise pas de Markdown visible: pas d'asterisques, pas de tableaux, pas d'emojis.
            - Formate tes reponses en courts paragraphes lisibles dans une bulle de chat mobile.

            Prefiltre utilisateur: $filterSummary

            Inventaire disponible (${items.size} fiche(s)):
            ${items.joinToString("\n") { it.toSommelierLine() }}
        """.trimIndent()
    }

    private fun CellarItem.toSommelierLine(): String {
        val parts = buildList {
            add("id=$id")
            add("type=${if (type == BeverageType.VIN) "vin" else "biere"}")
            add("producteur=${producer.cleanForPrompt()}")
            add("nom=${name.cleanForPrompt()}")
            vintage?.takeIf { it.isNotBlank() }?.let { add("millesime=${it.cleanForPrompt()}") }
            wineColor?.let { add("couleur=${it.label()}") }
            country?.takeIf { it.isNotBlank() }?.let { add("pays=${it.cleanForPrompt()}") }
            region?.takeIf { it.isNotBlank() }?.let { add("region=${it.cleanForPrompt()}") }
            style?.takeIf { it.isNotBlank() }?.let { add("style=${it.cleanForPrompt()}") }
            if (grapes.isNotEmpty()) add("cepages=${grapes.joinToString(", ") { it.cleanForPrompt() }}")
            alcoholVolume?.let { add("alcool=${"%.1f".format(Locale.US, it)}%") }
            ibu?.let { add("ibu=$it") }
            add("quantite=$quantity")
        }
        return parts.joinToString(" | ")
    }

    private fun metadataScore(item: CellarItem): Int =
        listOfNotNull(
            item.country,
            item.region,
            item.style,
            item.alcoholVolume,
            item.vintage,
            item.wineColor
        ).size + item.grapes.size

    private fun String.cleanForPrompt(): String =
        replace("\n", " ")
            .replace("|", "/")
            .trim()

    private fun String.normalized(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    private fun SommelierDrinkFilter.label(): String = when (this) {
        SommelierDrinkFilter.TOUS -> "tous"
        SommelierDrinkFilter.VIN -> "vin"
        SommelierDrinkFilter.BIERE -> "biere"
    }

    private fun SommelierMood.label(): String = when (this) {
        SommelierMood.LEGER -> "leger"
        SommelierMood.FRAIS -> "frais"
        SommelierMood.FUNKY -> "funky"
        SommelierMood.CLASSIQUE -> "classique"
        SommelierMood.REPAS -> "accord repas"
        SommelierMood.DECOUVERTE -> "decouverte"
    }

    private fun WineColor.label(): String = when (this) {
        WineColor.ROUGE -> "rouge"
        WineColor.BLANC -> "blanc"
        WineColor.JAUNE -> "jaune"
        WineColor.ORANGE -> "orange"
        WineColor.ROSE -> "rose"
    }
}
