package com.cellier.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellier.manager.data.CellarItem
import com.cellier.manager.repository.CellarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ProposedField(
    val name: String,
    val label: String,
    val currentValue: String,
    val proposedValue: String,
    val evidence: String?,
    val selected: Boolean
)

data class ProposalUiState(
    val loading: Boolean = true,
    val item: CellarItem? = null,
    val source: String = "",
    val sourceUrl: String = "",
    val identityAssessment: String = "",
    val vintageAssessment: String = "",
    val fields: List<ProposedField> = emptyList(),
    val productConfirmed: Boolean = false,
    val applying: Boolean = false,
    val applied: Boolean = false,
    val resolved: Boolean = false,
    val message: String? = null
)

class ProposalViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CellarRepository.getInstance(application)
    private val _ui = MutableStateFlow(ProposalUiState())
    val ui: StateFlow<ProposalUiState> = _ui.asStateFlow()
    private var requestId: String? = null
    private var expectedMetadataRevision: Long? = null

    fun load(value: String) {
        if (requestId == value) return
        requestId = value
        viewModelScope.launch {
            repository.observeProposal(value).collect { proposal ->
                if (proposal == null) {
                    _ui.update { it.copy(loading = false, message = "Le résultat n’est pas encore disponible.") }
                    return@collect
                }
                val json = runCatching { JSONObject(proposal.payloadJson) }.getOrNull()
                // L'association sûre vient de la demande locale; le JSON n'est jamais utilisé comme identifiant Room.
                val requestItemId = repository.getEnrichmentRequest(value)?.itemId
                val item = requestItemId?.let { repository.getById(it) }
                if (json == null || item == null) {
                    _ui.update { it.copy(loading = false, message = "Ce résultat ne peut pas être affiché.") }
                    return@collect
                }
                expectedMetadataRevision = item.metadataRevision
                val fieldsJson = json.optJSONObject("fields") ?: JSONObject()
                val fields = FIELD_LABELS.mapNotNull { (name, label) ->
                    val fieldJson = fieldsJson.optJSONObject(name) ?: return@mapNotNull null
                    val raw = fieldJson.opt("value")?.takeUnless { it == JSONObject.NULL } ?: return@mapNotNull null
                    val proposed = display(raw)
                    if (proposed.isBlank()) return@mapNotNull null
                    ProposedField(
                        name, label, current(item, name), proposed,
                        fieldJson.optString("evidence").takeIf(String::isNotBlank),
                        selected = name !in IDENTITY_FIELDS
                    )
                }
                _ui.value = ProposalUiState(
                    loading = false, item = item, source = json.optString("source"),
                    sourceUrl = json.optString("sourceUrl"),
                    identityAssessment = json.optString("identityAssessment"),
                    vintageAssessment = json.optString("vintageAssessment"), fields = fields,
                    productConfirmed = json.optString("identityAssessment") in setOf("PLAUSIBLE", "MATCH"),
                    applied = proposal.state == com.cellier.manager.data.EnrichmentProposalState.APPLIED.name,
                    resolved = proposal.state != com.cellier.manager.data.EnrichmentProposalState.PENDING.name,
                    message = when (proposal.state) {
                        com.cellier.manager.data.EnrichmentProposalState.APPLIED.name -> "Renseignements appliqués. La quantité est inchangée."
                        com.cellier.manager.data.EnrichmentProposalState.REJECTED.name -> "Résultat refusé."
                        com.cellier.manager.data.EnrichmentProposalState.STALE.name -> "Cette fiche a changé. Relance une recherche."
                        else -> null
                    }
                )
            }
        }
    }

    fun toggle(field: String) = _ui.update { state ->
        state.copy(fields = state.fields.map { if (it.name == field) it.copy(selected = !it.selected) else it })
    }

    fun confirmProduct(value: Boolean) = _ui.update { it.copy(productConfirmed = value) }

    fun apply() {
        val id = requestId ?: return
        val revision = expectedMetadataRevision ?: return
        val snapshot = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(applying = true, message = null) }
            val result = repository.applyProposal(
                id, revision, snapshot.fields.filter(ProposedField::selected).map(ProposedField::name).toSet(),
                snapshot.productConfirmed
            )
            val message = when (result) {
                CellarRepository.ApplyProposalResult.Applied -> "Renseignements appliqués. La quantité est inchangée."
                CellarRepository.ApplyProposalResult.AlreadyResolved -> "Ce résultat a déjà été traité."
                CellarRepository.ApplyProposalResult.IdentityChanged -> "Cette fiche a changé. Relance une recherche."
                CellarRepository.ApplyProposalResult.MetadataChanged -> "Cette fiche a changé. Vérifie la comparaison mise à jour."
                CellarRepository.ApplyProposalResult.ProductConfirmationRequired -> "Confirme d’abord qu’il s’agit du bon produit."
                is CellarRepository.ApplyProposalResult.Invalid -> result.message
            }
            _ui.update { it.copy(
                applying = false,
                applied = result == CellarRepository.ApplyProposalResult.Applied,
                resolved = result == CellarRepository.ApplyProposalResult.Applied || result == CellarRepository.ApplyProposalResult.AlreadyResolved,
                message = message
            ) }
        }
    }

    fun reject() {
        val id = requestId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(applying = true, message = null) }
            val rejected = repository.rejectProposal(id)
            _ui.update { it.copy(
                applying = false, resolved = rejected || it.resolved,
                message = if (rejected) "Résultat refusé." else "Ce résultat a déjà été traité."
            ) }
        }
    }

    companion object {
        private val IDENTITY_FIELDS = setOf("producer", "name", "vintage")
        private val FIELD_LABELS = linkedMapOf(
            "producer" to "Producteur", "name" to "Nom", "vintage" to "Millésime",
            "country" to "Pays", "region" to "Région", "grapes" to "Cépages",
            "style" to "Style", "alcoholVolume" to "Alcool", "ibu" to "IBU"
        )
        private fun display(value: Any): String = when (value) {
            is JSONArray -> buildList { for (i in 0 until value.length()) add(value.optString(i)) }.joinToString(", ")
            else -> value.toString()
        }
        private fun current(item: CellarItem, name: String): String = when (name) {
            "producer" -> item.producer; "name" -> item.name; "vintage" -> item.vintage.orEmpty()
            "country" -> item.country.orEmpty(); "region" -> item.region.orEmpty()
            "grapes" -> item.grapes.joinToString(", "); "style" -> item.style.orEmpty()
            "alcoholVolume" -> item.alcoholVolume?.toString().orEmpty(); "ibu" -> item.ibu?.toString().orEmpty()
            else -> ""
        }
    }
}
