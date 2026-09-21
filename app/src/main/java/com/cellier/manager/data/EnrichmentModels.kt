package com.cellier.manager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class FieldOriginType { MANUAL, OCR_CONFIRMED, LEGACY, WEB_CONFIRMED }
enum class EnrichmentSource { VIVINO, UNTAPPD, SAQ }
enum class EnrichmentRequestState {
    LOCAL_PENDING, SENDING, WAITING_BROWSER, READY_FOR_REVIEW,
    APPLIED, REJECTED, CANCELLED, STALE, EXPIRED
}
enum class EnrichmentProposalState { PENDING, APPLIED, REJECTED, STALE }
enum class OutboxState { PENDING, SENDING, SENT, FAILED, DEAD }

@Entity(tableName = "app_meta")
data class AppMeta(
    @androidx.room.PrimaryKey val key: String,
    val value: String
)

@Entity(
    tableName = "field_origins",
    primaryKeys = ["itemId", "fieldName"],
    foreignKeys = [ForeignKey(
        entity = CellarItem::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class FieldOrigin(
    val itemId: Long,
    val fieldName: String,
    val origin: String,
    val source: String? = null,
    val sourceUrl: String? = null,
    val proposalId: String? = null,
    val changedAt: Long = System.currentTimeMillis(),
    val explicitlyCleared: Boolean = false
)

@Entity(
    tableName = "enrichment_requests",
    foreignKeys = [ForeignKey(
        entity = CellarItem::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId"), Index("itemUuid"), Index("state")]
)
data class EnrichmentRequest(
    @androidx.room.PrimaryKey val requestId: String,
    val datasetId: String,
    val itemUuid: String,
    val itemId: Long,
    val source: String,
    val identitySnapshotJson: String,
    val identityRevisionAtRequest: Long,
    val state: String = EnrichmentRequestState.LOCAL_PENDING.name,
    val serverState: String? = null,
    val serverRevision: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val supersedesRequestId: String? = null
)

@Entity(
    tableName = "enrichment_proposals",
    foreignKeys = [ForeignKey(
        entity = EnrichmentRequest::class,
        parentColumns = ["requestId"],
        childColumns = ["requestId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("requestId")]
)
data class EnrichmentProposal(
    @androidx.room.PrimaryKey val proposalId: String,
    val requestId: String,
    val payloadJson: String,
    val payloadSha256: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val state: String = EnrichmentProposalState.PENDING.name,
    val appliedAt: Long? = null,
    val selectedFieldsJson: String? = null
)

data class AppliedIdentityCandidate(
    val requestId: String,
    val itemId: Long,
    val source: String,
    val identitySnapshotJson: String,
    val identityRevisionAtRequest: Long,
    val proposalId: String,
    val payloadJson: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "outbox_operations",
    indices = [Index(value = ["kind", "aggregateId", "state"]), Index("nextAttemptAt")]
)
data class OutboxOperation(
    @androidx.room.PrimaryKey val operationId: String,
    val kind: String,
    val aggregateId: String,
    val payloadJson: String,
    val payloadSha256: String,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val state: String = OutboxState.PENDING.name,
    val lastErrorCode: String? = null
)
