package com.cellier.manager.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {
    private val databaseName = "migration-6-7-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CellarDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun preservesInventoryAndConvertsLegacyGrapes() {
        helper.createDatabase(databaseName, 6).apply {
            execSQL(
                """INSERT INTO cellar_items
                (id,name,producer,vintage,type,photoPath,quantity,wineColor,country,region,grapes,style,
                 alcoholVolume,ibu,saqUrl,vivinoUrl,untappdUrl,vivinoMatchQuality,untappdMatchQuality,
                 isSyncPending,syncFailed,syncFailureReason,syncAttempts,created_at)
                VALUES (1,'Cuvée','Domaine','2020','VIN','/photo.jpg',0,'ROUGE','France','Rhône',
                'Grenache\|Syrah|Cinsault','Rouge',13.5,NULL,NULL,NULL,NULL,NULL,NULL,1,1,'NETWORK',2,1234)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName, 7, true, CellarDatabase.MIGRATION_6_7
        )
        migrated.query("SELECT * FROM cellar_items WHERE id=1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("quantity")))
            assertEquals("[\"Grenache|Syrah\",\"Cinsault\"]", cursor.getString(cursor.getColumnIndexOrThrow("grapes")))
            assertTrue(cursor.getString(cursor.getColumnIndexOrThrow("itemUuid")).isNotBlank())
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isSyncPending")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("syncFailed")))
            assertEquals(1234L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
        }
        migrated.query("SELECT COUNT(*) FROM field_origins WHERE itemId=1 AND origin='LEGACY'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(15, cursor.getInt(0))
        }
        migrated.close()
    }
}
