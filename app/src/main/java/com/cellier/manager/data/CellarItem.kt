package com.cellier.manager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray
import java.util.UUID

/**
 * Type de boisson. Choix obligatoire de l'utilisateur à la création.
 */
enum class BeverageType {
    VIN, BIERE
}

/**
 * Qualité historique de correspondance d’un lien produit.
 *
 * - PERFECT : producteur + nom + année correspondent à la fiche web après normalisation
 *   (accents/ponctuation/casse ignorés). Les données de la page ont été appliquées
 *   à la fiche.
 * - APPROX : un lien a été trouvé mais la correspondance n'est pas exacte.
 *   Seule l'URL est stockée, les champs de la fiche ne sont pas modifiés.
 *   L'utilisateur peut confirmer manuellement via le bouton ✓ pour passer en PERFECT.
 */
enum class MatchQuality {
    PERFECT, APPROX
}

enum class SyncFailureReason {
    NO_RESULT, RATE_LIMIT, PROVIDER_BUSY, NETWORK
}

/**
 * Couleur d'un vin.
 * Affichée comme pastille à côté du millésime sur la fiche et le listing.
 *
 * - ROUGE : rouge bourgogne foncé (#722F37)
 * - BLANC : doré pâle (#F4E4BC)
 * - JAUNE : jaune vif doré (#F5D04A)
 * - ORANGE : orange brûlé (#D97706)
 * - ROSE : rose doux (#F4A6B8)
 */
enum class WineColor {
    ROUGE, BLANC, JAUNE, ORANGE, ROSE
}

/**
 * Fiche d'un produit du cellier.
 * Voir README.md section "Modèle de données" pour les détails.
 */
@Entity(tableName = "cellar_items", indices = [Index(value = ["itemUuid"], unique = true)])
data class CellarItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Identifiant portable utilisé par le protocole compagnon et les sauvegardes. */
    @ColumnInfo(defaultValue = "''")
    val itemUuid: String = UUID.randomUUID().toString(),

    // --- Saisie manuelle ---
    val name: String,
    val producer: String,
    val vintage: String? = null,
    val type: BeverageType,
    val photoPath: String,
    val quantity: Int = 1,
    val wineColor: WineColor? = null,

    // --- Métadonnées facultatives, saisies ou validées depuis une proposition ---
    val country: String? = null,
    val region: String? = null,
    val grapes: List<String> = emptyList(),
    val style: String? = null,
    val alcoholVolume: Double? = null,
    val ibu: Int? = null,

    // --- Liens ---
    val saqUrl: String? = null,
    val vivinoUrl: String? = null,
    val untappdUrl: String? = null,

    // --- Qualité du match (null = pas encore indexé ou pas de lien) ---
    val vivinoMatchQuality: MatchQuality? = null,
    val untappdMatchQuality: MatchQuality? = null,

    // --- État d'indexation ---
    val isSyncPending: Boolean = true,
    val syncFailed: Boolean = false,
    val syncFailureReason: SyncFailureReason? = null,
    val syncAttempts: Int = 0,

    /** Révision des métadonnées, indépendante de la quantité. */
    @ColumnInfo(defaultValue = "0")
    val metadataRevision: Long = 0,

    /** Révision de producteur/nom/millésime/type. */
    @ColumnInfo(defaultValue = "0")
    val identityRevision: Long = 0,

    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Les listes sont écrites en JSON depuis le schéma 7. La lecture accepte encore
 * l'ancien format délimité afin qu'une base en cours de migration reste lisible.
 */
class CellarConverters {
    @TypeConverter
    fun listToString(list: List<String>): String =
        JSONArray(list).toString()

    @TypeConverter
    fun stringToList(value: String): List<String> =
        decodeStringList(value)

    @TypeConverter
    fun beverageTypeToString(type: BeverageType): String = type.name

    @TypeConverter
    fun stringToBeverageType(value: String): BeverageType = BeverageType.valueOf(value)

    @TypeConverter
    fun matchQualityToString(quality: MatchQuality?): String? = quality?.name

    @TypeConverter
    fun stringToMatchQuality(value: String?): MatchQuality? =
        value?.let { MatchQuality.valueOf(it) }

    @TypeConverter
    fun syncFailureReasonToString(reason: SyncFailureReason?): String? = reason?.name

    @TypeConverter
    fun stringToSyncFailureReason(value: String?): SyncFailureReason? =
        value?.let { runCatching { SyncFailureReason.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun wineColorToString(color: WineColor?): String? = color?.name

    @TypeConverter
    fun stringToWineColor(value: String?): WineColor? =
        value?.let { WineColor.valueOf(it) }

    companion object {
        fun decodeStringList(value: String): List<String> {
            if (value.isBlank()) return emptyList()
            if (value.trimStart().startsWith("[")) {
                return runCatching {
                    val json = JSONArray(value)
                    buildList {
                        for (index in 0 until json.length()) {
                            json.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }.getOrElse { emptyList() }
            }

            val result = mutableListOf<String>()
            val current = StringBuilder()
            var escaped = false
            value.forEach { char ->
                when {
                    escaped -> {
                        current.append(char)
                        escaped = false
                    }
                    char == '\\' -> escaped = true
                    char == '|' -> {
                        current.toString().trim().takeIf(String::isNotBlank)?.let(result::add)
                        current.clear()
                    }
                    else -> current.append(char)
                }
            }
            if (escaped) current.append('\\')
            current.toString().trim().takeIf(String::isNotBlank)?.let(result::add)
            return result
        }
    }
}
