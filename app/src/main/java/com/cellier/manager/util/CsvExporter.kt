package com.cellier.manager.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.cellier.manager.data.CellarItem
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export de la base de données du cellier au format CSV.
 *
 * - Trié par nom de produit (insensible à la casse).
 * - Première ligne : noms de colonnes en français.
 * - Champs avec virgule, retour à la ligne ou guillemets sont échappés
 *   (RFC 4180 : entourés de guillemets, guillemets internes doublés).
 * - Multi-valeurs (cépages) : séparés par "; " dans la cellule.
 *
 * Utilise FileProvider pour partager le fichier via le sélecteur Android.
 * Nécessite la déclaration FileProvider dans le manifest et un xml/file_paths.xml.
 */
object CsvExporter {

    private val HEADERS = listOf(
        "Nom du produit",
        "Producteur",
        "Type",
        "Millésime",
        "Couleur",
        "Quantité",
        "Pays",
        "Région",
        "Cépages",
        "Style/AOC",
        "Alcool (%)",
        "IBU",
        "Lien SAQ",
        "Lien Vivino",
        "Lien Untappd",
        "Date d'ajout"
    )

    /**
     * Génère un CSV depuis la liste des items et lance un Intent de partage.
     * Le fichier est créé dans le cache de l'app et exposé via FileProvider.
     */
    fun exportAndShare(context: Context, items: List<CellarItem>) {
        val file = generateCsv(context, items)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Mon Cellier")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Exporter le cellier").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * Crée le fichier CSV. Public pour faciliter les tests futurs.
     */
    fun generateCsv(context: Context, items: List<CellarItem>): File {
        val sortedItems = items.sortedBy { it.name.lowercase(Locale.FRENCH) }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.FRENCH).format(Date())
        val file = File(context.cacheDir, "cellier_$timestamp.csv")

        FileWriter(file).use { writer ->
            // BOM UTF-8 pour qu'Excel ouvre correctement les accents (sans ça, é → Ã©)
            writer.write("\uFEFF")
            // Header
            writer.write(HEADERS.joinToString(",") { csvEscape(it) })
            writer.write("\r\n")
            // Lignes
            for (item in sortedItems) {
                writer.write(itemToCsvRow(item))
                writer.write("\r\n")
            }
        }
        return file
    }

    private fun itemToCsvRow(item: CellarItem): String {
        val cells = listOf(
            item.name,
            item.producer,
            when (item.type) {
                com.cellier.manager.data.BeverageType.VIN -> "Vin"
                com.cellier.manager.data.BeverageType.BIERE -> "Bière"
            },
            item.vintage ?: "",
            when (item.wineColor) {
                com.cellier.manager.data.WineColor.ROUGE -> "Rouge"
                com.cellier.manager.data.WineColor.BLANC -> "Blanc"
                com.cellier.manager.data.WineColor.JAUNE -> "Jaune"
                com.cellier.manager.data.WineColor.ORANGE -> "Orange"
                com.cellier.manager.data.WineColor.ROSE -> "Rosé"
                null -> ""
            },
            item.quantity.toString(),
            item.country ?: "",
            item.region ?: "",
            item.grapes.joinToString("; "),
            item.style ?: "",
            item.alcoholVolume?.let { "%.1f".format(it).replace(",", ".") } ?: "",
            item.ibu?.toString() ?: "",
            item.saqUrl ?: "",
            item.vivinoUrl ?: "",
            item.untappdUrl ?: "",
            SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).format(Date(item.createdAt))
        )
        return cells.joinToString(",") { csvEscape(it) }
    }

    /**
     * RFC 4180 : si le champ contient virgule, guillemet, retour à la ligne ou
     * point-virgule, on l'entoure de guillemets et on double les guillemets
     * internes. Sinon on retourne tel quel.
     */
    internal fun csvEscape(value: String): String {
        val safeValue = if (value.firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        val needsQuoting = safeValue.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }
        return if (needsQuoting) {
            "\"" + safeValue.replace("\"", "\"\"") + "\""
        } else {
            safeValue
        }
    }
}
