package com.cellier.manager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CellarDao {

    // --- CRUD ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: CellarItem): Long

    @Update
    suspend fun update(item: CellarItem)

    @Delete
    suspend fun delete(item: CellarItem)

    @Query("DELETE FROM cellar_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    // --- Consultation ---

    /**
     * Toutes les fiches en stock (quantité > 0), triées par ajout récent.
     * Quantité = 0 = fiche retirée automatiquement de l'inventaire.
     */
    @Query("SELECT * FROM cellar_items WHERE quantity > 0 ORDER BY created_at DESC")
    fun observeAll(): Flow<List<CellarItem>>

    @Query("SELECT * FROM cellar_items WHERE id = :id")
    fun observeById(id: Long): Flow<CellarItem?>

    @Query("SELECT * FROM cellar_items WHERE id = :id")
    suspend fun getById(id: Long): CellarItem?

    @Query("SELECT * FROM cellar_items ORDER BY id")
    suspend fun getAllSnapshot(): List<CellarItem>

    @Query("DELETE FROM cellar_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM cellar_items WHERE quantity = 0 ORDER BY updatedAt DESC")
    fun observeDepleted(): Flow<List<CellarItem>>

    // --- Worker d'indexation ---

    @Query(
        "SELECT * FROM cellar_items " +
        "WHERE isSyncPending = 1 AND syncFailed = 0 AND syncAttempts < 3"
    )
    suspend fun getPendingIndexation(): List<CellarItem>

    // --- Stepper quantité ---

    @Query("UPDATE cellar_items SET quantity = quantity + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementQuantity(id: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE cellar_items SET quantity = quantity - 1, updatedAt = :updatedAt WHERE id = :id AND quantity > 0")
    suspend fun decrementQuantity(id: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE cellar_items SET isSyncPending = 0, syncFailed = 0, syncFailureReason = NULL, syncAttempts = 0")
    suspend fun neutralizeLegacyIndexingState()

    // --- Autocomplete / dropdowns intelligents ---

    @Query(
        "SELECT producer AS value, COUNT(*) AS count " +
        "FROM cellar_items GROUP BY producer ORDER BY count DESC, producer ASC"
    )
    suspend fun getProducerSuggestions(): List<Suggestion>

    @Query(
        "SELECT DISTINCT country AS value, COUNT(*) AS count " +
        "FROM cellar_items WHERE country IS NOT NULL AND quantity > 0 " +
        "GROUP BY country ORDER BY count DESC, country ASC"
    )
    suspend fun getCountrySuggestions(): List<Suggestion>

    @Query(
        "SELECT DISTINCT region AS value, COUNT(*) AS count " +
        "FROM cellar_items WHERE region IS NOT NULL AND quantity > 0 " +
        "GROUP BY region ORDER BY count DESC, region ASC"
    )
    suspend fun getRegionSuggestions(): List<Suggestion>

    @Query(
        "SELECT DISTINCT vintage AS value, COUNT(*) AS count " +
        "FROM cellar_items WHERE vintage IS NOT NULL AND quantity > 0 " +
        "GROUP BY vintage ORDER BY vintage DESC"
    )
    suspend fun getVintageSuggestions(): List<Suggestion>
}

/**
 * Projection pour les dropdowns intelligents (valeur + nombre de fiches).
 */
data class Suggestion(
    val value: String,
    val count: Int
)
