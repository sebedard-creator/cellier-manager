package com.cellier.manager.work

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cellier.manager.data.CellarDatabase
import com.cellier.manager.data.EnrichmentProposal
import com.cellier.manager.data.EnrichmentRequestState
import com.cellier.manager.enrichment.CompanionClient
import com.cellier.manager.enrichment.CompanionException
import com.cellier.manager.enrichment.CompanionStore
import com.cellier.manager.repository.CellarRepository
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class CompanionSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val configuration = CompanionStore(applicationContext).load() ?: return Result.success()
        val client = runCatching { CompanionClient.from(configuration) }.getOrElse { return Result.failure() }
        val database = CellarDatabase.getInstance(applicationContext)
        val dao = database.enrichmentDao()
        var retryableFailure = false
        var waitingForBrowser = false

        dao.getDueOutbox(System.currentTimeMillis()).forEach { operation ->
            dao.markOutboxSending(operation.operationId)
            try {
                val response = when (operation.kind) {
                    "CREATE_REQUEST" -> client.post("/api/v1/requests", operation.payloadJson, operation.operationId)
                    "OPEN_REQUEST" -> client.post(
                        "/api/v1/requests/${operation.aggregateId}/open", operation.payloadJson, operation.operationId
                    )
                    "CANCEL_REQUEST" -> client.post(
                        "/api/v1/requests/${operation.aggregateId}/cancel", "{}", operation.operationId
                    )
                    "ACK_PROPOSAL" -> {
                        val resolution = JSONObject(operation.payloadJson).optString("resolution", "RECEIVED")
                        client.post("/api/v1/proposals/${operation.aggregateId}/ack",
                            JSONObject().put("resolution", resolution).toString(), operation.operationId)
                    }
                    else -> {
                        dao.markOutboxSent(operation.operationId)
                        return@forEach
                    }
                }
                dao.markOutboxSent(operation.operationId)
                if (operation.kind in setOf("CREATE_REQUEST", "OPEN_REQUEST")) {
                    dao.updateRequestState(
                        requestId = operation.aggregateId,
                        state = EnrichmentRequestState.WAITING_BROWSER.name,
                        serverState = response.optString("state", "QUEUED"),
                        serverRevision = response.optLong("serverRevision").takeIf { it > 0 },
                        updatedAt = System.currentTimeMillis(), errorCode = null, errorMessage = null
                    )
                }
            } catch (error: CompanionException) {
                val retryable = error.statusCode >= 500 || error.statusCode in setOf(408, 429)
                if (retryable) {
                    val delayMinutes = (1L shl operation.attempts.coerceAtMost(5)).coerceAtMost(30)
                    dao.markOutboxFailed(
                        operation.operationId,
                        System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes),
                        "HTTP_${error.statusCode}"
                    )
                } else dao.markOutboxDead(operation.operationId, "HTTP_${error.statusCode}")
                if (operation.kind in setOf("CREATE_REQUEST", "OPEN_REQUEST")) dao.updateRequestState(
                    operation.aggregateId,
                    if (retryable) EnrichmentRequestState.LOCAL_PENDING.name else EnrichmentRequestState.EXPIRED.name,
                    null, null, System.currentTimeMillis(), "HTTP_${error.statusCode}", error.message)
                retryableFailure = retryable
            } catch (error: Exception) {
                dao.markOutboxFailed(
                    operation.operationId,
                    System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2),
                    "COMPANION_UNAVAILABLE"
                )
                if (operation.kind in setOf("CREATE_REQUEST", "OPEN_REQUEST")) dao.updateRequestState(
                    operation.aggregateId, EnrichmentRequestState.LOCAL_PENDING.name, null, null,
                    System.currentTimeMillis(), "COMPANION_UNAVAILABLE", error.message)
                retryableFailure = true
            }
        }

        dao.getOpenRequests().forEach { request ->
            try {
                val status = client.get("/api/v1/requests/${request.requestId}") ?: return@forEach
                val serverState = status.optString("state")
                when (serverState) {
                    "READY" -> {
                        val proposal = client.get("/api/v1/requests/${request.requestId}/proposal") ?: return@forEach
                        val proposalId = proposal.getString("proposalId")
                        val payload = proposal.toString()
                        database.withTransaction {
                            val receivedAt = System.currentTimeMillis()
                            val payloadSha256 = sha256(payload)
                            val updated = dao.updatePendingProposal(
                                proposalId, payload, payloadSha256, receivedAt
                            )
                            if (updated == 0) runCatching {
                                dao.insertProposal(EnrichmentProposal(
                                    proposalId = proposalId,
                                    requestId = request.requestId,
                                    payloadJson = payload,
                                    payloadSha256 = payloadSha256,
                                    receivedAt = receivedAt,
                                ))
                            }
                            dao.updateRequestState(
                                request.requestId, EnrichmentRequestState.READY_FOR_REVIEW.name,
                                serverState, status.optLong("serverRevision").takeIf { it > 0 },
                                System.currentTimeMillis(), null, null
                            )
                        }
                        runCatching {
                            client.post("/api/v1/proposals/$proposalId/ack", "{\"resolution\":\"RECEIVED\"}")
                        }
                        val automatic = CellarRepository.getInstance(applicationContext)
                            .applyAutomaticWebProposal(request.requestId)
                        if (automatic == CellarRepository.ApplyProposalResult.Applied) {
                            runCatching {
                                client.post("/api/v1/proposals/$proposalId/ack", "{\"resolution\":\"APPLIED\"}")
                            }
                        }
                    }
                    "CANCELLED" -> dao.updateRequestState(
                        request.requestId, EnrichmentRequestState.CANCELLED.name, serverState,
                        status.optLong("serverRevision").takeIf { it > 0 }, System.currentTimeMillis(), null, null
                    )
                    "EXPIRED" -> dao.updateRequestState(
                        request.requestId, EnrichmentRequestState.EXPIRED.name, serverState,
                        status.optLong("serverRevision").takeIf { it > 0 }, System.currentTimeMillis(), null, null
                    )
                    else -> {
                        waitingForBrowser = true
                        val serverRevision = status.optLong("serverRevision").takeIf { it > 0 }
                        if (request.state != EnrichmentRequestState.WAITING_BROWSER.name ||
                            request.serverState != serverState || request.serverRevision != serverRevision ||
                            request.lastErrorCode != null || request.lastErrorMessage != null
                        ) {
                            dao.updateRequestState(
                                request.requestId, EnrichmentRequestState.WAITING_BROWSER.name, serverState,
                                serverRevision, System.currentTimeMillis(), null, null
                            )
                        }
                    }
                }
            } catch (error: CompanionException) {
                val permanent = error.statusCode in setOf(401, 403, 404, 410, 422)
                if (permanent) dao.updateRequestState(
                    request.requestId, EnrichmentRequestState.EXPIRED.name, null, request.serverRevision,
                    System.currentTimeMillis(), "HTTP_${error.statusCode}", error.message
                ) else {
                    dao.updateRequestState(
                        request.requestId, request.state, request.serverState, request.serverRevision,
                        System.currentTimeMillis(), "COMPANION_UNAVAILABLE",
                        "L’ordinateur ne répond pas. Nouvelle tentative automatique."
                    )
                    retryableFailure = true
                }
            } catch (_: Exception) {
                dao.updateRequestState(
                    request.requestId, request.state, request.serverState, request.serverRevision,
                    System.currentTimeMillis(), "COMPANION_UNAVAILABLE",
                    "L’ordinateur ne répond pas. Nouvelle tentative automatique."
                )
                retryableFailure = true
            }
        }

        return when {
            retryableFailure && runAttemptCount < 5 -> Result.retry()
            waitingForBrowser && runAttemptCount < 8 -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "cellier_companion_sync"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<CompanionSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
