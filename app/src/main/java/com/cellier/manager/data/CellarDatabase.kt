package com.cellier.manager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.util.UUID

@Database(
    entities = [
        CellarItem::class,
        SearchCacheEntry::class,
        AppMeta::class,
        FieldOrigin::class,
        EnrichmentRequest::class,
        EnrichmentProposal::class,
        OutboxOperation::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(CellarConverters::class)
abstract class CellarDatabase : RoomDatabase() {

    abstract fun cellarDao(): CellarDao
    abstract fun enrichmentDao(): EnrichmentDao

    companion object {
        @Volatile
        private var INSTANCE: CellarDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cellar_items ADD COLUMN ibu INTEGER")
                db.execSQL("ALTER TABLE cellar_items ADD COLUMN untappdMatchQuality TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cellar_items ADD COLUMN wineColor TEXT")
            }
        }

        /**
         * Migration v4 → v5 (v1.3.2) : nouvelle table `search_cache`.
         * Aucune modification aux fiches existantes.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_cache (
                        queryHash TEXT NOT NULL PRIMARY KEY,
                        url TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cellar_items ADD COLUMN syncFailureReason TEXT")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `app_meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")

                val datasetId = UUID.randomUUID().toString()
                db.execSQL("INSERT OR REPLACE INTO `app_meta` (`key`, `value`) VALUES ('datasetId', ?)", arrayOf(datasetId))
                db.execSQL("INSERT OR REPLACE INTO `app_meta` (`key`, `value`) VALUES ('backupFormatVersion', '1')")

                db.execSQL("ALTER TABLE `cellar_items` ADD COLUMN `itemUuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cellar_items` ADD COLUMN `metadataRevision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cellar_items` ADD COLUMN `identityRevision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cellar_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")

                db.query("SELECT `id`, `grapes`, `created_at` FROM `cellar_items`").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val grapesIndex = cursor.getColumnIndexOrThrow("grapes")
                    val createdAtIndex = cursor.getColumnIndexOrThrow("created_at")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val uuid = UUID.nameUUIDFromBytes(
                            "$datasetId:item:$id".toByteArray(StandardCharsets.UTF_8)
                        ).toString()
                        val grapes = CellarConverters.decodeStringList(cursor.getString(grapesIndex).orEmpty())
                        db.execSQL(
                            "UPDATE `cellar_items` SET `itemUuid` = ?, `grapes` = ?, `updatedAt` = ? WHERE `id` = ?",
                            arrayOf(uuid, JSONArray(grapes).toString(), cursor.getLong(createdAtIndex), id)
                        )
                    }
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cellar_items_itemUuid` ON `cellar_items` (`itemUuid`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `field_origins` (
                        `itemId` INTEGER NOT NULL, `fieldName` TEXT NOT NULL, `origin` TEXT NOT NULL,
                        `source` TEXT, `sourceUrl` TEXT, `proposalId` TEXT, `changedAt` INTEGER NOT NULL,
                        `explicitlyCleared` INTEGER NOT NULL,
                        PRIMARY KEY(`itemId`, `fieldName`),
                        FOREIGN KEY(`itemId`) REFERENCES `cellar_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_origins_itemId` ON `field_origins` (`itemId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `enrichment_requests` (
                        `requestId` TEXT NOT NULL, `datasetId` TEXT NOT NULL, `itemUuid` TEXT NOT NULL,
                        `itemId` INTEGER NOT NULL, `source` TEXT NOT NULL, `identitySnapshotJson` TEXT NOT NULL,
                        `identityRevisionAtRequest` INTEGER NOT NULL, `state` TEXT NOT NULL,
                        `serverState` TEXT, `serverRevision` INTEGER, `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL, `lastErrorCode` TEXT, `lastErrorMessage` TEXT,
                        `supersedesRequestId` TEXT, PRIMARY KEY(`requestId`),
                        FOREIGN KEY(`itemId`) REFERENCES `cellar_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_enrichment_requests_itemId` ON `enrichment_requests` (`itemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_enrichment_requests_itemUuid` ON `enrichment_requests` (`itemUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_enrichment_requests_state` ON `enrichment_requests` (`state`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `enrichment_proposals` (
                        `proposalId` TEXT NOT NULL, `requestId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL,
                        `payloadSha256` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `state` TEXT NOT NULL,
                        `appliedAt` INTEGER, `selectedFieldsJson` TEXT, PRIMARY KEY(`proposalId`),
                        FOREIGN KEY(`requestId`) REFERENCES `enrichment_requests`(`requestId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_enrichment_proposals_requestId` ON `enrichment_proposals` (`requestId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `outbox_operations` (
                        `operationId` TEXT NOT NULL, `kind` TEXT NOT NULL, `aggregateId` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL, `payloadSha256` TEXT NOT NULL, `attempts` INTEGER NOT NULL,
                        `nextAttemptAt` INTEGER NOT NULL, `state` TEXT NOT NULL, `lastErrorCode` TEXT,
                        PRIMARY KEY(`operationId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_operations_kind_aggregateId_state` ON `outbox_operations` (`kind`, `aggregateId`, `state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_operations_nextAttemptAt` ON `outbox_operations` (`nextAttemptAt`)")

                val fields = listOf(
                    "name", "producer", "vintage", "type", "photoPath", "wineColor", "country",
                    "region", "grapes", "style", "alcoholVolume", "ibu", "saqUrl", "vivinoUrl", "untappdUrl"
                )
                val now = System.currentTimeMillis()
                db.query("SELECT `id` FROM `cellar_items`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val itemId = cursor.getLong(0)
                        fields.forEach { field ->
                            db.execSQL(
                                "INSERT OR IGNORE INTO `field_origins` (`itemId`,`fieldName`,`origin`,`source`,`sourceUrl`,`proposalId`,`changedAt`,`explicitlyCleared`) VALUES (?,?,'LEGACY',NULL,NULL,NULL,?,0)",
                                arrayOf(itemId, field, now)
                            )
                        }
                    }
                }
                db.execSQL("UPDATE `cellar_items` SET `isSyncPending` = 0, `syncFailed` = 0, `syncFailureReason` = NULL, `syncAttempts` = 0")
            }
        }

        fun getInstance(context: Context): CellarDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CellarDatabase::class.java,
                    "cellier.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
