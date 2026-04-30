package com.midas.applications.export

import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.midas.data.storage.AndroidContextHolder
import java.io.File

/**
 * Android implementation: escribe a `cacheDir/midas-export/` y abre el
 * share sheet con `Intent.ACTION_SEND` envuelto en un chooser.
 *
 * El archivo se sirve vía `FileProvider` con authority
 * `${applicationId}.fileprovider` (registrada en AndroidManifest.xml).
 * Esto evita el `FileUriExposedException` en API 24+.
 */
actual class FileShareBridge actual constructor() {

    actual fun shareTextFile(
        filename: String,
        mimeType: String,
        content: String,
        onError: (String) -> Unit,
    ) {
        val context = AndroidContextHolder.context
        try {
            val outDir = File(context.cacheDir, "midas-export").apply { mkdirs() }
            val file = File(outDir, filename)
            file.writeText(content, Charsets.UTF_8)

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Compartir solicitudes").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.w(TAG, "share failed: ${e.message}", e)
            onError(e.message ?: "No se pudo compartir el archivo")
        }
    }

    private companion object {
        const val TAG = "MidasFileShare"
    }
}
