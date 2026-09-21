package com.cellier.manager.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Helpers pour la capture et stockage des photos de bouteilles.
 *
 * Les photos sont stockées dans `filesDir/photos/` avec un nom UUID,
 * rendues accessibles à l'app appareil photo via FileProvider.
 */
object PhotoCapture {
    private const val PREFS = "photo_capture"
    private const val KEY_PENDING_PATH = "pending_photo_path"

    /**
     * Crée un nouveau fichier photo vide et retourne (File, Uri content://).
     * Le Uri doit être passé au contract TakePicture qui va écrire dedans.
     */
    fun createPhotoFile(context: Context): Pair<File, Uri> {
        val photosDir = File(context.filesDir, "photos").apply {
            if (!exists()) mkdirs()
        }
        val photoFile = File(photosDir, "${UUID.randomUUID()}.jpg")
        photoFile.createNewFile()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        return photoFile to uri
    }

    fun rememberPendingCapture(context: Context, path: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_PATH, path)
            .apply()
    }

    fun pendingCapturePath(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_PATH, null)
            ?.takeIf { it.isNotBlank() }

    fun clearPendingCapture(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_PATH)
            .apply()
    }

    suspend fun waitForCapturedPhoto(
        path: String?,
        timeoutMs: Long = 3_000L,
        pollMs: Long = 150L
    ): File? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null

        val file = File(path)
        var waitedMs = 0L
        var lastSize = -1L
        var stableReads = 0

        while (waitedMs <= timeoutMs) {
            if (file.exists()) {
                val size = file.length()
                if (size > 0L) {
                    if (size == lastSize) {
                        stableReads += 1
                    } else {
                        stableReads = 0
                        lastSize = size
                    }
                    if (stableReads >= 1) {
                        return@withContext file
                    }
                }
            }

            delay(pollMs)
            waitedMs += pollMs
        }

        file.takeIf { it.exists() && it.length() > 0L }
    }

    /**
     * Supprime le fichier photo associé à une fiche (appelé à la suppression).
     */
    fun deletePhotoFile(path: String) {
        if (path.isBlank()) return
        runCatching { File(path).delete() }
    }

    /** Retire uniquement les captures privées anciennes qui ne sont liées à aucune fiche. */
    fun cleanupOrphans(context: Context, referencedPaths: Set<String>, olderThanMs: Long = 24 * 60 * 60 * 1000L) {
        val directory = File(context.filesDir, "photos")
        val pending = pendingCapturePath(context)
        val cutoff = System.currentTimeMillis() - olderThanMs
        directory.listFiles()?.filter { file ->
            file.isFile && file.absolutePath !in referencedPaths && file.absolutePath != pending && file.lastModified() < cutoff
        }?.forEach { file -> runCatching { file.delete() } }
    }
}
