package com.cellier.manager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EnrichmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRequest(request: EnrichmentRequest)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProposal(proposal: EnrichmentProposal)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutbox(operation: OutboxOperation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: AppMeta)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOrigins(origins: List<FieldOrigin>)

    @Query("SELECT value FROM app_meta WHERE `key` = :key LIMIT 1")
    suspend fun getMeta(key: String): String?

    @Query("SELECT * FROM enrichment_requests ORDER BY createdAt DESC")
    fun observeRequests(): Flow<List<EnrichmentRequest>>

    @Query("SELECT * FROM enrichment_requests WHERE itemId = :itemId ORDER BY createdAt DESC")
    fun observeRequestsForItem(itemId: Long): Flow<List<EnrichmentRequest>>

    @Query("SELECT * FROM enrichment_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getRequest(requestId: String): EnrichmentRequest?

    @Query("SELECT * FROM enrichment_proposals WHERE proposalId = :proposalId LIMIT 1")
    suspend fun getProposal(proposalId: String): EnrichmentProposal?

    @Query("SELECT * FROM enrichment_proposals WHERE requestId = :requestId ORDER BY receivedAt DESC LIMIT 1")
    suspend fun getProposalForRequest(requestId: String): EnrichmentProposal?

    @Query("SELECT * FROM enrichment_proposals WHERE requestId = :requestId ORDER BY receivedAt DESC LIMIT 1")
    fun observeProposalForRequest(requestId: String): Flow<EnrichmentProposal?>

    @Query("""
        UPDATE enrichment_proposals SET payloadJson = :payloadJson,
          payloadSha256 = :payloadSha256, receivedAt = :receivedAt
        WHERE proposalId = :proposalId AND state = 'PENDING'
    """)
    suspend fun updatePendingProposal(
        proposalId: String,
        payloadJson: String,
        payloadSha256: String,
        receivedAt: Long,
    ): Int

    @Query("SELECT * FROM field_origins WHERE itemId = :itemId")
    suspend fun getOrigins(itemId: Long): List<FieldOrigin>

    @Query("SELECT * FROM field_origins ORDER BY itemId, fieldName")
    suspend fun getAllOrigins(): List<FieldOrigin>

    @Query("""
        SELECT r.requestId, r.itemId, r.source, r.identitySnapshotJson,
          r.identityRevisionAtRequest, p.proposalId, p.payloadJson, r.updatedAt
        FROM enrichment_requests r
        JOIN enrichment_proposals p ON p.requestId = r.requestId
        WHERE r.state = 'APPLIED' AND p.state = 'APPLIED'
          AND r.source IN ('SAQ','VIVINO','UNTAPPD')
        ORDER BY r.updatedAt DESC
    """)
    suspend fun getAppliedIdentityCandidates(): List<AppliedIdentityCandidate>

    @Query("DELETE FROM outbox_operations")
    suspend fun clearOutbox()

    @Query("DELETE FROM enrichment_requests")
    suspend fun clearRequests()

    @Query("""
        UPDATE cellar_items SET vivinoMatchQuality = 'PERFECT'
        WHERE vivinoUrl IS NOT NULL AND EXISTS (
          SELECT 1 FROM enrichment_requests r
          WHERE r.itemId = cellar_items.id AND r.source = 'VIVINO'
            AND r.state = 'APPLIED'
            AND r.identityRevisionAtRequest = cellar_items.identityRevision
        )
    """)
    suspend fun backfillAppliedVivinoMatches(): Int

    @Query("""
        UPDATE cellar_items SET untappdMatchQuality = 'PERFECT'
        WHERE untappdUrl IS NOT NULL AND EXISTS (
          SELECT 1 FROM enrichment_requests r
          WHERE r.itemId = cellar_items.id AND r.source = 'UNTAPPD'
            AND r.state = 'APPLIED'
            AND r.identityRevisionAtRequest = cellar_items.identityRevision
        )
    """)
    suspend fun backfillAppliedUntappdMatches(): Int

    @Query("""
        SELECT * FROM enrichment_requests
        WHERE itemId = :itemId AND source = :source AND identityRevisionAtRequest = :identityRevision
          AND state IN ('LOCAL_PENDING','SENDING','WAITING_BROWSER','READY_FOR_REVIEW')
        ORDER BY createdAt DESC LIMIT 1
    """)
    suspend fun findActiveRequest(itemId: Long, source: String, identityRevision: Long): EnrichmentRequest?

    @Query("""
        UPDATE enrichment_requests SET state = :state, serverState = :serverState,
          serverRevision = :serverRevision, updatedAt = :updatedAt,
          lastErrorCode = :errorCode, lastErrorMessage = :errorMessage
        WHERE requestId = :requestId
    """)
    suspend fun updateRequestState(
        requestId: String,
        state: String,
        serverState: String?,
        serverRevision: Long?,
        updatedAt: Long,
        errorCode: String?,
        errorMessage: String?
    ): Int

    @Query("""
        UPDATE enrichment_requests SET identitySnapshotJson = :identitySnapshotJson,
          identityRevisionAtRequest = :identityRevision
        WHERE requestId = :requestId
    """)
    suspend fun updateRequestIdentity(
        requestId: String,
        identitySnapshotJson: String,
        identityRevision: Long,
    ): Int

    @Query("""
        UPDATE enrichment_requests SET state = 'STALE', updatedAt = :updatedAt
        WHERE itemId = :itemId
          AND state IN ('LOCAL_PENDING','SENDING','WAITING_BROWSER','READY_FOR_REVIEW')
    """)
    suspend fun markOpenRequestsStale(itemId: Long, updatedAt: Long): Int

    @Query("""
        UPDATE enrichment_requests SET state = 'STALE', updatedAt = :updatedAt
        WHERE itemId = :itemId AND requestId != :exceptRequestId
          AND state IN ('LOCAL_PENDING','SENDING','WAITING_BROWSER','READY_FOR_REVIEW')
    """)
    suspend fun markOtherOpenRequestsStale(
        itemId: Long,
        exceptRequestId: String,
        updatedAt: Long,
    ): Int

    @Query("""
        UPDATE enrichment_proposals SET state = :state, appliedAt = :appliedAt,
          selectedFieldsJson = :selectedFieldsJson WHERE proposalId = :proposalId AND state = 'PENDING'
    """)
    suspend fun resolveProposal(
        proposalId: String,
        state: String,
        appliedAt: Long?,
        selectedFieldsJson: String?
    ): Int

    @Query("SELECT * FROM outbox_operations WHERE state IN ('PENDING','FAILED','SENDING') AND nextAttemptAt <= :now ORDER BY nextAttemptAt, attempts LIMIT :limit")
    suspend fun getDueOutbox(now: Long, limit: Int = 25): List<OutboxOperation>

    @Query("SELECT * FROM enrichment_requests WHERE state IN ('SENDING','WAITING_BROWSER','READY_FOR_REVIEW') ORDER BY updatedAt LIMIT :limit")
    suspend fun getOpenRequests(limit: Int = 100): List<EnrichmentRequest>

    @Query("UPDATE outbox_operations SET state = 'SENDING' WHERE operationId = :operationId AND state IN ('PENDING','FAILED','SENDING')")
    suspend fun markOutboxSending(operationId: String): Int

    @Query("UPDATE outbox_operations SET state = 'SENT', lastErrorCode = NULL WHERE operationId = :operationId")
    suspend fun markOutboxSent(operationId: String): Int

    @Query("UPDATE outbox_operations SET state = 'FAILED', attempts = attempts + 1, nextAttemptAt = :nextAttemptAt, lastErrorCode = :errorCode WHERE operationId = :operationId")
    suspend fun markOutboxFailed(operationId: String, nextAttemptAt: Long, errorCode: String): Int

    @Query("UPDATE outbox_operations SET state = 'DEAD', attempts = attempts + 1, lastErrorCode = :errorCode WHERE operationId = :operationId")
    suspend fun markOutboxDead(operationId: String, errorCode: String): Int
}
