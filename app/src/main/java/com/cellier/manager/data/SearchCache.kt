package com.cellier.manager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité conservée uniquement pour que Room continue de reconnaître la table
 * créée par les anciennes versions. La refonte n'utilise plus ce cache web.
 */
@Entity(tableName = "search_cache")
data class SearchCacheEntry(
    @PrimaryKey
    val queryHash: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)
