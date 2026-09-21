package com.cellier.manager.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.cellier.manager.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val datasetId: String,
    val createdAt: String,
    val totalItems: Int,
    val inStockItems: Int,
    val depletedItems: Int,
    val photosPresent: Int,
    val photosMissing: Int
)

/** Archive portable de l'inventaire. Les secrets, tâches et caches n'y entrent jamais. */
class BackupManager(private val context: Context) {
    private val database = CellarDatabase.getInstance(context)

    suspend fun export(destination: Uri): BackupSummary = withContext(Dispatchers.IO) {
        maintenance.withLock {
            val (temporary, summary) = createArchive(requireCompletePhotos = false)
            try {
                validateAndExtract(temporary).directory.deleteRecursively()
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    FileInputStream(temporary).use { it.copyTo(output) }
                } ?: error("Impossible d’écrire à cet emplacement.")
                summary
            } finally {
                temporary.delete()
            }
        }
    }

    suspend fun inspect(source: Uri): BackupSummary = withContext(Dispatchers.IO) {
        copySourceToTemp(source).let { archive ->
            try {
                val validated = validateAndExtract(archive)
                try { validated.summary } finally { validated.directory.deleteRecursively() }
            } finally { archive.delete() }
        }
    }

    suspend fun restore(source: Uri): BackupSummary = withContext(Dispatchers.IO) {
        maintenance.withLock {
            val archive = copySourceToTemp(source)
            val validated = try { validateAndExtract(archive) } finally { archive.delete() }
            val newPhotoFiles = mutableListOf<File>()
            try {
                // Une sauvegarde privée complète protège l'inventaire courant avant son remplacement.
                val safety = createArchive(requireCompletePhotos = true).first
                File(context.filesDir, "backups").mkdirs()
                val safetyTarget = File(context.filesDir, "backups/safety-before-restore-${System.currentTimeMillis()}.cellierbackup")
                safety.copyTo(safetyTarget, overwrite = true)
                safety.delete()

                val inventory = JSONArray(File(validated.directory, INVENTORY).readText())
                val originsJson = JSONArray(File(validated.directory, ORIGINS).readText())
                val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
                val restored = ArrayList<CellarItem>(inventory.length())
                val oldPhotoPaths = database.cellarDao().getAllSnapshot().map { it.photoPath }.filter(String::isNotBlank)
                val ids = mutableSetOf<Long>()
                val uuids = mutableSetOf<String>()
                for (index in 0 until inventory.length()) {
                    val json = inventory.getJSONObject(index)
                    val id = json.getLong("id")
                    val uuid = json.getString("itemUuid")
                    require(runCatching { UUID.fromString(uuid) }.isSuccess) { "UUID de fiche invalide." }
                    require(ids.add(id) && uuids.add(uuid)) { "Identifiant de fiche dupliqué." }
                    val asset = json.optString("photoAsset").takeIf(String::isNotBlank)
                    val newPhotoPath = asset?.let {
                        val sourcePhoto = File(validated.directory, it)
                        require(sourcePhoto.isFile) { "Photo référencée absente: $it" }
                        val extension = sourcePhoto.extension.lowercase().takeIf { ext -> ext in PHOTO_EXTENSIONS } ?: "jpg"
                        File(photosDir, "${UUID.randomUUID()}.$extension").also { target ->
                            sourcePhoto.copyTo(target, overwrite = false)
                            newPhotoFiles += target
                        }.absolutePath
                    }.orEmpty()
                    restored += json.toItem(newPhotoPath)
                }
                val validIds = restored.mapTo(mutableSetOf(), CellarItem::id)
                val origins = buildList {
                    for (index in 0 until originsJson.length()) {
                        originsJson.getJSONObject(index).toOrigin().takeIf { it.itemId in validIds }?.let(::add)
                    }
                }
                database.withTransaction {
                    database.enrichmentDao().clearOutbox()
                    database.enrichmentDao().clearRequests()
                    database.cellarDao().deleteAll()
                    restored.forEach { database.cellarDao().insert(it) }
                    database.enrichmentDao().putOrigins(origins)
                    database.enrichmentDao().putMeta(AppMeta(DATASET_ID, validated.summary.datasetId))
                }
                val retained = restored.map { it.photoPath }.toSet()
                oldPhotoPaths.filterNot(retained::contains).forEach { runCatching { File(it).delete() } }
                validated.summary
            } catch (error: Exception) {
                newPhotoFiles.forEach { runCatching { it.delete() } }
                throw error
            } finally {
                validated.directory.deleteRecursively()
            }
        }
    }

    private suspend fun createArchive(requireCompletePhotos: Boolean): Pair<File, BackupSummary> {
        val snapshot = database.withTransaction {
            Triple(
                database.cellarDao().getAllSnapshot(),
                database.enrichmentDao().getAllOrigins(),
                database.enrichmentDao().getMeta(DATASET_ID) ?: UUID.randomUUID().toString().also {
                    database.enrichmentDao().putMeta(AppMeta(DATASET_ID, it))
                }
            )
        }
        val items = snapshot.first
        val photoAssets = mutableMapOf<Long, Pair<String, File>>()
        val missingPhotos = mutableListOf<String>()
        items.forEach { item ->
            if (item.photoPath.isNotBlank()) {
                val photo = File(item.photoPath)
                if (photo.isFile && photo.length() <= MAX_PHOTO_BYTES) {
                    val extension = photo.extension.lowercase().takeIf { it in PHOTO_EXTENSIONS } ?: "jpg"
                    photoAssets[item.id] = "photos/${item.itemUuid}.$extension" to photo
                } else missingPhotos += "${item.producer} — ${item.name}"
            }
        }
        if (requireCompletePhotos && missingPhotos.isNotEmpty()) {
            error("La sauvegarde de sécurité est incomplète: ${missingPhotos.size} photo(s) manquante(s).")
        }
        val createdAt = Instant.now().toString()
        val inventoryBytes = JSONArray().apply {
            items.forEach { put(it.toJson(photoAssets[it.id]?.first)) }
        }.toString(2).toByteArray(Charsets.UTF_8)
        val originBytes = JSONArray().apply { snapshot.second.forEach { put(it.toJson()) } }
            .toString(2).toByteArray(Charsets.UTF_8)
        val entries = linkedMapOf<String, EntryDigest>()
        val archive = File.createTempFile("cellier-export-", ".cellierbackup", context.cacheDir)
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            entries[INVENTORY] = zip.addBytes(INVENTORY, inventoryBytes)
            entries[ORIGINS] = zip.addBytes(ORIGINS, originBytes)
            photoAssets.values.forEach { (name, file) -> entries[name] = zip.addFile(name, file) }
            val summary = BackupSummary(
                snapshot.third, createdAt, items.size, items.count { it.quantity > 0 },
                items.count { it.quantity == 0 }, photoAssets.size, missingPhotos.size
            )
            val manifest = JSONObject()
                .put("format", FORMAT).put("backupVersion", VERSION)
                .put("datasetId", summary.datasetId).put("createdAt", createdAt)
                .put("appVersion", appVersion()).put("totalItems", summary.totalItems)
                .put("inStockItems", summary.inStockItems).put("depletedItems", summary.depletedItems)
                .put("photosExpected", photoAssets.size + missingPhotos.size)
                .put("photosPresent", photoAssets.size).put("photosMissing", JSONArray(missingPhotos))
                .put("entries", JSONObject().apply { entries.forEach { (name, digest) ->
                    put(name, JSONObject().put("size", digest.size).put("sha256", digest.sha256))
                } })
            zip.addBytes(MANIFEST, manifest.toString(2).toByteArray(Charsets.UTF_8))
            return archive to summary
        }
    }

    private data class ValidatedArchive(val directory: File, val summary: BackupSummary)
    private data class EntryDigest(val size: Long, val sha256: String)

    private fun validateAndExtract(archive: File): ValidatedArchive {
        val directory = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val seen = mutableSetOf<String>()
        val actual = linkedMapOf<String, EntryDigest>()
        var total = 0L
        try {
            ZipInputStream(FileInputStream(archive)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    require(!entry.isDirectory && isAllowedBackupEntry(name)) { "Entrée d’archive interdite: $name" }
                    require(seen.add(name)) { "Entrée d’archive dupliquée: $name" }
                    require(seen.size <= MAX_ENTRIES) { "Archive trop volumineuse." }
                    val target = File(directory, name)
                    require(target.canonicalPath.startsWith(directory.canonicalPath + File.separator)) { "Chemin d’archive interdit." }
                    target.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            size += count
                            total += count
                            require(total <= MAX_TOTAL_BYTES) { "Archive décompressée trop volumineuse." }
                            if (name.startsWith("photos/")) require(size <= MAX_PHOTO_BYTES) { "Photo trop volumineuse." }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                    actual[name] = EntryDigest(size, digest.digest().hex())
                }
            }
            require(MANIFEST in seen && INVENTORY in seen && ORIGINS in seen) { "Archive incomplète." }
            val manifest = JSONObject(File(directory, MANIFEST).readText())
            require(manifest.optString("format") == FORMAT && manifest.optInt("backupVersion") == VERSION) {
                "Format de sauvegarde non pris en charge."
            }
            val expected = manifest.getJSONObject("entries")
            val expectedNames = expected.keys().asSequence().toSet()
            require(expectedNames == actual.keys.filterNot { it == MANIFEST }.toSet()) { "Liste des fichiers incohérente." }
            expectedNames.forEach { name ->
                val wanted = expected.getJSONObject(name)
                val got = actual.getValue(name)
                require(wanted.getLong("size") == got.size && wanted.getString("sha256").equals(got.sha256, true)) {
                    "Contenu altéré: $name"
                }
            }
            val inventory = JSONArray(File(directory, INVENTORY).readText())
            require(inventory.length() <= MAX_ITEMS) { "La sauvegarde contient trop de fiches." }
            val summary = BackupSummary(
                datasetId = manifest.getString("datasetId"), createdAt = manifest.getString("createdAt"),
                totalItems = manifest.getInt("totalItems"), inStockItems = manifest.getInt("inStockItems"),
                depletedItems = manifest.getInt("depletedItems"), photosPresent = manifest.getInt("photosPresent"),
                photosMissing = manifest.optJSONArray("photosMissing")?.length() ?: 0
            )
            require(summary.totalItems == inventory.length()) { "Nombre de fiches incohérent." }
            return ValidatedArchive(directory, summary)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    private fun copySourceToTemp(source: Uri): File {
        val target = File.createTempFile("cellier-import-", ".cellierbackup", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count
                        require(total <= MAX_TOTAL_BYTES) { "Fichier de sauvegarde trop volumineux." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Impossible de lire cette sauvegarde.")
            return target
        } catch (error: Exception) { target.delete(); throw error }
    }

    private fun ZipOutputStream.addBytes(name: String, bytes: ByteArray): EntryDigest {
        putNextEntry(ZipEntry(name)); write(bytes); closeEntry()
        return EntryDigest(bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).hex())
    }

    private fun ZipOutputStream.addFile(name: String, file: File): EntryDigest {
        putNextEntry(ZipEntry(name))
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                write(buffer, 0, count); digest.update(buffer, 0, count); size += count
            }
        }
        closeEntry()
        return EntryDigest(size, digest.digest().hex())
    }

    private fun CellarItem.toJson(photoAsset: String?): JSONObject = JSONObject()
        .put("id", id).put("itemUuid", itemUuid).put("name", name).put("producer", producer)
        .putNullable("vintage", vintage).put("type", type.name).putNullable("photoAsset", photoAsset)
        .put("quantity", quantity).putNullable("wineColor", wineColor?.name)
        .putNullable("country", country).putNullable("region", region).put("grapes", JSONArray(grapes))
        .putNullable("style", style).putNullable("alcoholVolume", alcoholVolume).putNullable("ibu", ibu)
        .putNullable("saqUrl", saqUrl).putNullable("vivinoUrl", vivinoUrl).putNullable("untappdUrl", untappdUrl)
        .putNullable("vivinoMatchQuality", vivinoMatchQuality?.name).putNullable("untappdMatchQuality", untappdMatchQuality?.name)
        .put("metadataRevision", metadataRevision).put("identityRevision", identityRevision)
        .put("updatedAt", updatedAt).put("createdAt", createdAt)

    private fun JSONObject.toItem(photoPath: String): CellarItem = CellarItem(
        id = getLong("id"), itemUuid = getString("itemUuid"), name = getString("name"), producer = getString("producer"),
        vintage = nullableString("vintage"), type = BeverageType.valueOf(getString("type")), photoPath = photoPath,
        quantity = getInt("quantity").coerceAtLeast(0), wineColor = nullableString("wineColor")?.let(WineColor::valueOf),
        country = nullableString("country"), region = nullableString("region"), grapes = optJSONArray("grapes")?.let { array ->
            buildList { for (i in 0 until array.length()) array.optString(i).takeIf(String::isNotBlank)?.let(::add) }
        }.orEmpty(), style = nullableString("style"), alcoholVolume = nullableDouble("alcoholVolume"), ibu = nullableInt("ibu"),
        saqUrl = nullableString("saqUrl"), vivinoUrl = nullableString("vivinoUrl"), untappdUrl = nullableString("untappdUrl"),
        vivinoMatchQuality = nullableString("vivinoMatchQuality")?.let(MatchQuality::valueOf),
        untappdMatchQuality = nullableString("untappdMatchQuality")?.let(MatchQuality::valueOf),
        isSyncPending = false, syncFailed = false, syncFailureReason = null, syncAttempts = 0,
        metadataRevision = getLong("metadataRevision"), identityRevision = getLong("identityRevision"),
        updatedAt = getLong("updatedAt"), createdAt = getLong("createdAt")
    )

    private fun FieldOrigin.toJson(): JSONObject = JSONObject().put("itemId", itemId).put("fieldName", fieldName)
        .put("origin", origin).putNullable("source", source).putNullable("sourceUrl", sourceUrl)
        .putNullable("proposalId", proposalId).put("changedAt", changedAt).put("explicitlyCleared", explicitlyCleared)

    private fun JSONObject.toOrigin() = FieldOrigin(
        itemId = getLong("itemId"), fieldName = getString("fieldName"), origin = getString("origin"),
        source = nullableString("source"), sourceUrl = nullableString("sourceUrl"), proposalId = nullableString("proposalId"),
        changedAt = getLong("changedAt"), explicitlyCleared = optBoolean("explicitlyCleared")
    )

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
    private fun JSONObject.nullableDouble(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    private fun appVersion(): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

    companion object {
        private val maintenance = Mutex()
        private const val FORMAT = "cellier-backup"
        private const val VERSION = 1
        private const val DATASET_ID = "datasetId"
        private const val MANIFEST = "manifest.json"
        private const val INVENTORY = "inventory.json"
        private const val ORIGINS = "field-origins.json"
        private const val MAX_ITEMS = 10_000
        private const val MAX_ENTRIES = 20_010
        private const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
        private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}

internal fun isAllowedBackupEntry(name: String): Boolean =
    name == "manifest.json" || name == "inventory.json" || name == "field-origins.json" ||
        (name.startsWith("photos/") && name.count { it == '/' } == 1 &&
            name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") &&
            name.substringAfter('/').substringBeforeLast('.').matches(Regex("[0-9a-fA-F-]{36}")))
