package com.cellier.manager.repository

import android.content.Context
import androidx.room.withTransaction
import com.cellier.manager.data.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.net.URI
import com.cellier.manager.work.CompanionSyncWorker
import com.cellier.manager.enrichment.proposalIdentityMatches
import org.json.JSONArray

/** Compatibilité temporaire avec l'écran existant : found signifie « mise en file ». */
data class ManualSearchOutcome(val found: Boolean, val failureReason: SyncFailureReason? = null) {
    companion object {
        val Queued = ManualSearchOutcome(found = true)
        fun failed(reason: SyncFailureReason?) = ManualSearchOutcome(false, reason ?: SyncFailureReason.NO_RESULT)
    }
}

class CellarRepository private constructor(
    private val context: Context,
    private val database: CellarDatabase,
    private val dao: CellarDao,
    private val enrichmentDao: EnrichmentDao
) {
    fun observeAll(): Flow<List<CellarItem>> = dao.observeAll()
    fun observeDepleted(): Flow<List<CellarItem>> = dao.observeDepleted()
    fun observeById(id: Long): Flow<CellarItem?> = dao.observeById(id)
    fun observeEnrichmentRequests(): Flow<List<EnrichmentRequest>> = enrichmentDao.observeRequests()
    fun observeEnrichmentRequests(itemId: Long): Flow<List<EnrichmentRequest>> =
        enrichmentDao.observeRequestsForItem(itemId)
    fun observeProposal(requestId: String): Flow<EnrichmentProposal?> =
        enrichmentDao.observeProposalForRequest(requestId)
    suspend fun getEnrichmentRequest(requestId: String): EnrichmentRequest? =
        enrichmentDao.getRequest(requestId)

    suspend fun addItem(item: CellarItem): Long = database.withTransaction {
        dao.insert(item.copy(
            itemUuid = item.itemUuid.ifBlank { UUID.randomUUID().toString() },
            isSyncPending = false, syncFailed = false, syncFailureReason = null,
            syncAttempts = 0, updatedAt = System.currentTimeMillis()
        ))
    }

    /** Fusionne les champs éditables dans la fiche récente et préserve la quantité. */
    suspend fun updateItem(edited: CellarItem) = database.withTransaction {
        val current = dao.getById(edited.id) ?: return@withTransaction
        val identityChanged = current.producer != edited.producer || current.name != edited.name ||
            current.vintage != edited.vintage || current.type != edited.type
        val now = System.currentTimeMillis()
        dao.update(current.copy(
            producer = edited.producer, name = edited.name, vintage = edited.vintage, type = edited.type,
            photoPath = edited.photoPath,
            wineColor = if (edited.type == BeverageType.VIN) edited.wineColor else null,
            country = edited.country, region = edited.region, grapes = edited.grapes,
            style = edited.style, alcoholVolume = edited.alcoholVolume, ibu = edited.ibu,
            vivinoMatchQuality = if (identityChanged) null else current.vivinoMatchQuality,
            untappdMatchQuality = if (identityChanged) null else current.untappdMatchQuality,
            metadataRevision = current.metadataRevision + 1,
            identityRevision = current.identityRevision + if (identityChanged) 1 else 0,
            updatedAt = now
        ))
        if (identityChanged) enrichmentDao.markOpenRequestsStale(current.id, now)
    }

    suspend fun deleteItem(item: CellarItem) = database.withTransaction {
        dao.getById(item.id)?.let { dao.delete(it) }
    }
    suspend fun getById(id: Long): CellarItem? = dao.getById(id)

    /**
     * Mise à niveau unique des recherches déjà appliquées avant que le nom et
     * le producteur officiels soient sélectionnés automatiquement.
     */
    suspend fun backfillCanonicalWebIdentities() = database.withTransaction {
        if (enrichmentDao.getMeta(CANONICAL_IDENTITY_BACKFILL) == "done") return@withTransaction
        val latestByItem = enrichmentDao.getAppliedIdentityCandidates().distinctBy { it.itemId }
        latestByItem.forEach { candidate ->
            val item = dao.getById(candidate.itemId) ?: return@forEach
            if (item.identityRevision != candidate.identityRevisionAtRequest) return@forEach
            val proposal = runCatching { JSONObject(candidate.payloadJson) }.getOrNull() ?: return@forEach
            val snapshot = runCatching { JSONObject(candidate.identitySnapshotJson) }.getOrNull() ?: return@forEach
            val validated = proposal.optString("identityAssessment") in setOf("PLAUSIBLE", "MATCH") ||
                proposalIdentityMatches(proposal, snapshot)
            if (!validated || proposal.optString("vintageAssessment") == "MISMATCH") return@forEach
            val fields = proposal.optJSONObject("fields") ?: return@forEach
            fun official(field: String, current: String): String =
                fields.optJSONObject(field)?.opt("value")
                    ?.takeUnless { it == JSONObject.NULL }
                    ?.toString()?.trim()?.takeIf(String::isNotBlank) ?: current
            val producer = official("producer", item.producer)
            val name = official("name", item.name)
            if (producer == item.producer && name == item.name) return@forEach
            val now = System.currentTimeMillis()
            val updated = item.copy(
                producer = producer,
                name = name,
                metadataRevision = item.metadataRevision + 1,
                identityRevision = item.identityRevision + 1,
                updatedAt = now,
            )
            dao.update(updated)
            val canonicalIdentity = JSONObject()
                .put("type", updated.type.name)
                .put("producer", updated.producer)
                .put("name", updated.name)
                .put("vintage", updated.vintage ?: JSONObject.NULL)
            enrichmentDao.updateRequestIdentity(
                candidate.requestId,
                canonicalIdentity.toString(),
                updated.identityRevision,
            )
            enrichmentDao.markOtherOpenRequestsStale(item.id, candidate.requestId, now)
            val sourceUrl = proposal.optString("sourceUrl").takeIf(String::isNotBlank)
            enrichmentDao.putOrigins(listOf("producer", "name").map { field ->
                FieldOrigin(
                    itemId = item.id,
                    fieldName = field,
                    origin = FieldOriginType.WEB_CONFIRMED.name,
                    source = candidate.source,
                    sourceUrl = sourceUrl,
                    proposalId = candidate.proposalId,
                    changedAt = now,
                )
            })
        }
        enrichmentDao.putMeta(AppMeta(CANONICAL_IDENTITY_BACKFILL, "done"))
    }
    suspend fun incrementQuantity(id: Long) = dao.incrementQuantity(id)
    suspend fun decrementQuantity(id: Long) = dao.decrementQuantity(id)
    suspend fun producerSuggestions(): List<Suggestion> = dao.getProducerSuggestions()
    suspend fun countrySuggestions(): List<Suggestion> = dao.getCountrySuggestions()
    suspend fun regionSuggestions(): List<Suggestion> = dao.getRegionSuggestions()
    suspend fun vintageSuggestions(): List<Suggestion> = dao.getVintageSuggestions()

    suspend fun retryIndexation(itemId: Long) {
        val item = dao.getById(itemId) ?: return
        queueEnrichment(itemId, if (item.type == BeverageType.VIN) EnrichmentSource.VIVINO else EnrichmentSource.UNTAPPD)
    }

    suspend fun resetResolvedLinksAndMetadata(itemId: Long) = database.withTransaction {
        val current = dao.getById(itemId) ?: return@withTransaction
        dao.update(current.copy(
            country = null, region = null, grapes = emptyList(), style = null,
            alcoholVolume = null, ibu = null, saqUrl = null, vivinoUrl = null, untappdUrl = null,
            vivinoMatchQuality = null, untappdMatchQuality = null,
            isSyncPending = false, syncFailed = false, syncFailureReason = null, syncAttempts = 0,
            metadataRevision = current.metadataRevision + 1, updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun searchSaqManual(id: Long) = queueOutcome(id, EnrichmentSource.SAQ)
    suspend fun searchVivinoManual(id: Long) = queueOutcome(id, EnrichmentSource.VIVINO)
    suspend fun searchUntappdManual(id: Long) = queueOutcome(id, EnrichmentSource.UNTAPPD)

    private suspend fun queueOutcome(id: Long, source: EnrichmentSource): ManualSearchOutcome =
        if (queueEnrichment(id, source) != null) ManualSearchOutcome.Queued
        else ManualSearchOutcome.failed(SyncFailureReason.NO_RESULT)

    suspend fun queueEnrichment(itemId: Long, source: EnrichmentSource): String? {
        val requestId = database.withTransaction {
            val item = dao.getById(itemId) ?: return@withTransaction null
            enrichmentDao.findActiveRequest(itemId, source.name, item.identityRevision)?.let {
                val payload = JSONObject().put("requestId", it.requestId).toString()
                enrichmentDao.insertOutbox(OutboxOperation(
                    operationId = UUID.randomUUID().toString(), kind = "OPEN_REQUEST",
                    aggregateId = it.requestId, payloadJson = payload, payloadSha256 = sha256(payload)
                ))
                return@withTransaction it.requestId
            }
            val datasetId = enrichmentDao.getMeta(DATASET_ID) ?: UUID.randomUUID().toString().also {
                enrichmentDao.putMeta(AppMeta(DATASET_ID, it))
            }
            val requestId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val identity = JSONObject().put("type", item.type.name).put("producer", item.producer)
                .put("name", item.name).put("vintage", item.vintage ?: JSONObject.NULL)
            val payload = JSONObject().put("protocolVersion", 1).put("requestId", requestId)
                .put("datasetId", datasetId).put("itemUuid", item.itemUuid).put("source", source.name)
                .put("identityRevision", item.identityRevision).put("identity", identity)
                .put("createdAt", Instant.ofEpochMilli(now).toString()).toString()
            enrichmentDao.insertRequest(EnrichmentRequest(
                requestId = requestId, datasetId = datasetId, itemUuid = item.itemUuid, itemId = item.id,
                source = source.name, identitySnapshotJson = identity.toString(),
                identityRevisionAtRequest = item.identityRevision, createdAt = now, updatedAt = now
            ))
            enrichmentDao.insertOutbox(OutboxOperation(
                operationId = UUID.randomUUID().toString(), kind = "CREATE_REQUEST", aggregateId = requestId,
                payloadJson = payload, payloadSha256 = sha256(payload)
            ))
            requestId
        }
        requestId?.let { CompanionSyncWorker.schedule(context) }
        return requestId
    }

    suspend fun cancelEnrichment(requestId: String): Boolean {
        val cancelled = database.withTransaction {
            val request = enrichmentDao.getRequest(requestId) ?: return@withTransaction false
            if (request.state !in OPEN_REQUEST_STATES) return@withTransaction false
            val now = System.currentTimeMillis()
            enrichmentDao.updateRequestState(
                requestId, EnrichmentRequestState.CANCELLED.name, request.serverState,
                request.serverRevision, now, null, null
            )
            val payload = JSONObject().put("requestId", requestId).toString()
            enrichmentDao.insertOutbox(OutboxOperation(
                operationId = UUID.randomUUID().toString(), kind = "CANCEL_REQUEST",
                aggregateId = requestId, payloadJson = payload, payloadSha256 = sha256(payload)
            ))
            true
        }
        if (cancelled) CompanionSyncWorker.schedule(context)
        return cancelled
    }

    sealed interface ApplyProposalResult {
        data object Applied : ApplyProposalResult
        data object AlreadyResolved : ApplyProposalResult
        data object IdentityChanged : ApplyProposalResult
        data object MetadataChanged : ApplyProposalResult
        data object ProductConfirmationRequired : ApplyProposalResult
        data class Invalid(val message: String) : ApplyProposalResult
    }

    /** Applique une proposition sur la version relue en transaction. La quantité n'est jamais copiée. */
    suspend fun applyProposal(
        requestId: String,
        expectedMetadataRevision: Long,
        selectedFields: Set<String>,
        productConfirmed: Boolean,
        scheduleAcknowledgement: Boolean = true
    ): ApplyProposalResult {
        val result = database.withTransaction {
            val request = enrichmentDao.getRequest(requestId)
                ?: return@withTransaction ApplyProposalResult.Invalid("Recherche introuvable.")
            val proposal = enrichmentDao.getProposalForRequest(requestId)
                ?: return@withTransaction ApplyProposalResult.Invalid("Résultat introuvable.")
            if (proposal.state != EnrichmentProposalState.PENDING.name) {
                return@withTransaction ApplyProposalResult.AlreadyResolved
            }
            val item = dao.getById(request.itemId)
                ?: return@withTransaction ApplyProposalResult.Invalid("La fiche n’existe plus.")
            if (item.identityRevision != request.identityRevisionAtRequest) {
                enrichmentDao.resolveProposal(proposal.proposalId, EnrichmentProposalState.STALE.name, null, null)
                enrichmentDao.updateRequestState(requestId, EnrichmentRequestState.STALE.name, request.serverState,
                    request.serverRevision, System.currentTimeMillis(), "IDENTITY_CHANGED", "La fiche a changé.")
                return@withTransaction ApplyProposalResult.IdentityChanged
            }
            if (item.metadataRevision != expectedMetadataRevision) {
                return@withTransaction ApplyProposalResult.MetadataChanged
            }

            val json = runCatching { JSONObject(proposal.payloadJson) }.getOrElse {
                return@withTransaction ApplyProposalResult.Invalid("Résultat illisible.")
            }
            if (json.optString("itemUuid") != item.itemUuid ||
                json.optLong("identityRevision", -1) != item.identityRevision) {
                return@withTransaction ApplyProposalResult.IdentityChanged
            }
            val assessment = json.optString("identityAssessment")
            if (assessment !in setOf("PLAUSIBLE", "MATCH") && !productConfirmed) {
                return@withTransaction ApplyProposalResult.ProductConfirmationRequired
            }
            val fields = json.optJSONObject("fields")
                ?: return@withTransaction ApplyProposalResult.Invalid("Aucun renseignement à appliquer.")
            val allowed = selectedFields.intersect(APPLICABLE_FIELDS)
            val values = allowed.associateWith { field -> fields.optJSONObject(field)?.opt("value") }
            fun text(name: String, current: String?): String? = values[name]?.let { value ->
                if (value == JSONObject.NULL) null else value.toString().trim().takeIf(String::isNotBlank)
            } ?: current
            fun double(name: String, current: Double?): Double? = values[name]?.let { value ->
                (value as? Number)?.toDouble() ?: value.toString().replace(',', '.').toDoubleOrNull()
            } ?: current
            fun int(name: String, current: Int?): Int? = values[name]?.let { value ->
                (value as? Number)?.toInt() ?: value.toString().toIntOrNull()
            } ?: current
            fun stringList(name: String, current: List<String>): List<String> = values[name]?.let { value ->
                when (value) {
                    is JSONArray -> buildList {
                        for (index in 0 until value.length()) value.optString(index).trim()
                            .takeIf(String::isNotBlank)?.let(::add)
                    }
                    else -> value.toString().split(',', ';').map(String::trim).filter(String::isNotBlank)
                }
            } ?: current

            val newProducer = text("producer", item.producer) ?: item.producer
            val newName = text("name", item.name) ?: item.name
            val newVintage = text("vintage", item.vintage)
            val identityChanged = newProducer != item.producer || newName != item.name || newVintage != item.vintage
            val source = json.optString("source").takeIf { it in EnrichmentSource.entries.map { source -> source.name } }
                ?: request.source
            val sourceUrl = json.optString("sourceUrl").takeIf { it.startsWith("https://") }
            if (sourceUrl != null && !isAllowedSourceUrl(sourceUrl, source)) {
                return@withTransaction ApplyProposalResult.Invalid("L’adresse de la source est invalide.")
            }
            val now = System.currentTimeMillis()
            val updated = item.copy(
                producer = newProducer, name = newName, vintage = newVintage,
                country = text("country", item.country), region = text("region", item.region),
                grapes = stringList("grapes", item.grapes), style = text("style", item.style),
                alcoholVolume = double("alcoholVolume", item.alcoholVolume)?.takeIf { it in 0.0..100.0 },
                ibu = int("ibu", item.ibu)?.takeIf { it in 0..1000 },
                saqUrl = if (source == EnrichmentSource.SAQ.name) sourceUrl ?: item.saqUrl else item.saqUrl,
                vivinoUrl = if (source == EnrichmentSource.VIVINO.name) sourceUrl ?: item.vivinoUrl else item.vivinoUrl,
                untappdUrl = if (source == EnrichmentSource.UNTAPPD.name) sourceUrl ?: item.untappdUrl else item.untappdUrl,
                vivinoMatchQuality = if (source == EnrichmentSource.VIVINO.name) {
                    MatchQuality.PERFECT
                } else item.vivinoMatchQuality,
                untappdMatchQuality = if (source == EnrichmentSource.UNTAPPD.name) {
                    MatchQuality.PERFECT
                } else item.untappdMatchQuality,
                metadataRevision = item.metadataRevision + 1,
                identityRevision = item.identityRevision + if (identityChanged) 1 else 0,
                updatedAt = now
            )
            dao.update(updated)
            if (identityChanged) {
                val canonicalIdentity = JSONObject()
                    .put("type", updated.type.name)
                    .put("producer", updated.producer)
                    .put("name", updated.name)
                    .put("vintage", updated.vintage ?: JSONObject.NULL)
                enrichmentDao.updateRequestIdentity(
                    requestId,
                    canonicalIdentity.toString(),
                    updated.identityRevision,
                )
                enrichmentDao.markOtherOpenRequestsStale(item.id, requestId, now)
            }
            enrichmentDao.putOrigins(allowed.map { field -> FieldOrigin(
                itemId = item.id, fieldName = field, origin = FieldOriginType.WEB_CONFIRMED.name,
                source = source, sourceUrl = sourceUrl, proposalId = proposal.proposalId, changedAt = now
            ) })
            val selectedJson = JSONArray(allowed.sorted()).toString()
            enrichmentDao.resolveProposal(proposal.proposalId, EnrichmentProposalState.APPLIED.name, now, selectedJson)
            enrichmentDao.updateRequestState(requestId, EnrichmentRequestState.APPLIED.name, "APPLIED",
                request.serverRevision, now, null, null)
            val ack = JSONObject().put("proposalId", proposal.proposalId).put("resolution", "APPLIED").toString()
            enrichmentDao.insertOutbox(OutboxOperation(
                operationId = UUID.randomUUID().toString(), kind = "ACK_PROPOSAL",
                aggregateId = proposal.proposalId, payloadJson = ack, payloadSha256 = sha256(ack)
            ))
            ApplyProposalResult.Applied
        }
        if (result == ApplyProposalResult.Applied && scheduleAcknowledgement) {
            CompanionSyncWorker.schedule(context)
        }
        return result
    }

    /**
     * Applique sans écran intermédiaire un résultat web dont l'identité a déjà
     * été validée. Le producteur et le nom adoptent l'orthographe officielle
     * de la source; le millésime et la quantité restent inchangés.
     */
    suspend fun applyAutomaticWebProposal(requestId: String): ApplyProposalResult {
        val plan = database.withTransaction {
            val request = enrichmentDao.getRequest(requestId) ?: return@withTransaction null
            if (request.source !in setOf(
                    EnrichmentSource.SAQ.name,
                    EnrichmentSource.VIVINO.name,
                    EnrichmentSource.UNTAPPD.name,
                )) {
                return@withTransaction null
            }
            val proposal = enrichmentDao.getProposalForRequest(requestId) ?: return@withTransaction null
            if (proposal.state != EnrichmentProposalState.PENDING.name) return@withTransaction null
            val item = dao.getById(request.itemId) ?: return@withTransaction null
            val json = runCatching { JSONObject(proposal.payloadJson) }.getOrNull()
                ?: return@withTransaction null
            val companionValidated = json.optString("identityAssessment") in setOf("PLAUSIBLE", "MATCH")
            val androidValidated = runCatching {
                proposalIdentityMatches(json, JSONObject(request.identitySnapshotJson))
            }.getOrDefault(false)
            if ((!companionValidated && !androidValidated) ||
                json.optString("vintageAssessment") == "MISMATCH") return@withTransaction null
            val sourceUrl = json.optString("sourceUrl")
            if (!isAutomaticWebProductUrl(request.source, sourceUrl)) return@withTransaction null
            val fields = json.optJSONObject("fields") ?: return@withTransaction null
            val origins = enrichmentDao.getOrigins(item.id).associateBy(FieldOrigin::fieldName)
            fun replaceable(name: String, empty: Boolean): Boolean {
                val origin = origins[name]
                return empty || origin?.origin == FieldOriginType.WEB_CONFIRMED.name
            }
            fun proposed(name: String): Boolean {
                val value = fields.optJSONObject(name)?.opt("value")
                return value != null && value != JSONObject.NULL && when (value) {
                    is JSONArray -> value.length() > 0
                    else -> value.toString().isNotBlank()
                }
            }
            val selected = buildSet {
                if (proposed("producer")) add("producer")
                if (proposed("name")) add("name")
                if (replaceable("country", item.country.isNullOrBlank()) && proposed("country")) add("country")
                if (replaceable("region", item.region.isNullOrBlank()) && proposed("region")) add("region")
                if (replaceable("grapes", item.grapes.isEmpty()) && proposed("grapes")) add("grapes")
                if (replaceable("style", item.style.isNullOrBlank()) && proposed("style")) add("style")
                if (replaceable("alcoholVolume", item.alcoholVolume == null) && proposed("alcoholVolume")) add("alcoholVolume")
                if (replaceable("ibu", item.ibu == null) && proposed("ibu")) add("ibu")
            }
            item.metadataRevision to selected
        } ?: return ApplyProposalResult.Invalid("Le résultat ne correspond pas assez clairement à la fiche.")
        return applyProposal(
            requestId, plan.first, plan.second,
            productConfirmed = true,
            scheduleAcknowledgement = false,
        )
    }

    suspend fun rejectProposal(requestId: String): Boolean {
        val rejected = database.withTransaction {
            val request = enrichmentDao.getRequest(requestId) ?: return@withTransaction false
            val proposal = enrichmentDao.getProposalForRequest(requestId) ?: return@withTransaction false
            val now = System.currentTimeMillis()
            if (enrichmentDao.resolveProposal(
                    proposal.proposalId, EnrichmentProposalState.REJECTED.name, now, "[]"
                ) == 0) return@withTransaction false
            enrichmentDao.updateRequestState(
                requestId, EnrichmentRequestState.REJECTED.name, "REJECTED",
                request.serverRevision, now, null, null
            )
            val ack = JSONObject().put("proposalId", proposal.proposalId).put("resolution", "REJECTED").toString()
            enrichmentDao.insertOutbox(OutboxOperation(
                operationId = UUID.randomUUID().toString(), kind = "ACK_PROPOSAL",
                aggregateId = proposal.proposalId, payloadJson = ack, payloadSha256 = sha256(ack)
            ))
            true
        }
        if (rejected) CompanionSyncWorker.schedule(context)
        return rejected
    }

    /** Ancien bouton de confirmation : avis local uniquement, sans requête web. */
    suspend fun confirmVivinoMatch(id: Long) = database.withTransaction {
        val current = dao.getById(id) ?: return@withTransaction
        dao.update(current.copy(vivinoMatchQuality = MatchQuality.PERFECT, updatedAt = System.currentTimeMillis()))
    }
    suspend fun confirmUntappdMatch(id: Long) = database.withTransaction {
        val current = dao.getById(id) ?: return@withTransaction
        dao.update(current.copy(untappdMatchQuality = MatchQuality.PERFECT, updatedAt = System.currentTimeMillis()))
    }

    companion object {
        private const val DATASET_ID = "datasetId"
        private const val CANONICAL_IDENTITY_BACKFILL = "canonicalIdentityBackfillV1"
        private val OPEN_REQUEST_STATES = setOf(
            EnrichmentRequestState.LOCAL_PENDING.name, EnrichmentRequestState.SENDING.name,
            EnrichmentRequestState.WAITING_BROWSER.name, EnrichmentRequestState.READY_FOR_REVIEW.name
        )
        private val APPLICABLE_FIELDS = setOf(
            "producer", "name", "vintage", "country", "region", "grapes", "style",
            "alcoholVolume", "ibu"
        )
        @Volatile private var INSTANCE: CellarRepository? = null
        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun isAllowedSourceUrl(value: String, source: String): Boolean = runCatching {
            val uri = URI(value)
            val expected = when (source) {
                EnrichmentSource.VIVINO.name -> "vivino.com"
                EnrichmentSource.UNTAPPD.name -> "untappd.com"
                EnrichmentSource.SAQ.name -> "saq.com"
                else -> return false
            }
            val host = uri.host?.lowercase() ?: return false
            uri.scheme == "https" && uri.userInfo == null && (host == expected || host.endsWith(".$expected"))
        }.getOrDefault(false)
        private fun isAutomaticWebProductUrl(source: String, value: String): Boolean = runCatching {
            val uri = URI(value)
            val host = uri.host?.lowercase() ?: return false
            if (uri.scheme != "https" || uri.userInfo != null) return false
            when (source) {
                EnrichmentSource.SAQ.name ->
                    (host == "saq.com" || host.endsWith(".saq.com")) &&
                        Regex("^/(?:fr|en)/\\d+/?$").matches(uri.path.orEmpty())
                EnrichmentSource.VIVINO.name ->
                    (host == "vivino.com" || host.endsWith(".vivino.com")) &&
                        Regex("^/[a-z]{2}/.+/w/\\d+/?$", RegexOption.IGNORE_CASE)
                            .matches(uri.path.orEmpty())
                EnrichmentSource.UNTAPPD.name ->
                    (host == "untappd.com" || host.endsWith(".untappd.com")) &&
                        Regex("^/b/[^/]+/\\d+/?$", RegexOption.IGNORE_CASE)
                            .matches(uri.path.orEmpty())
                else -> false
            }
        }.getOrDefault(false)
        fun getInstance(context: Context): CellarRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: CellarDatabase.getInstance(context).let { db ->
                CellarRepository(context.applicationContext, db, db.cellarDao(), db.enrichmentDao())
            }.also { INSTANCE = it }
        }
    }
}
