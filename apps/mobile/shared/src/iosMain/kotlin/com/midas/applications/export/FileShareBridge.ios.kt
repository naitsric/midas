package com.midas.applications.export

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * iOS implementation con `UIActivityViewController` — sin tocar Swift.
 * El bridge usa cinterop directo a Foundation/UIKit para escribir el
 * archivo en `NSTemporaryDirectory()` y presentar el share sheet sobre
 * el `keyWindow.rootViewController` activo.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class FileShareBridge actual constructor() {

    actual fun shareTextFile(
        filename: String,
        mimeType: String,
        content: String,
        onError: (String) -> Unit,
    ) {
        try {
            val tempDir = NSTemporaryDirectory()
            val path = "$tempDir$filename"
            val fileUrl = NSURL.fileURLWithPath(path)

            val nsContent = content as NSString
            val data = nsContent.dataUsingEncoding(NSUTF8StringEncoding)
            if (data == null) {
                onError("No se pudo codificar el archivo")
                return
            }
            // writeToURL con atomically=true → escritura segura.
            data.writeToURL(fileUrl, true)

            val activityVc = UIActivityViewController(
                activityItems = listOf(fileUrl),
                applicationActivities = null,
            )

            val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootVc == null) {
                onError("No hay ventana activa para presentar el share")
                NSFileManager.defaultManager.removeItemAtURL(fileUrl, null)
                return
            }
            // Si el rootVc ya está presentando algo (modal), subimos al top
            // para no perder el activity sheet.
            var topVc = rootVc
            while (topVc?.presentedViewController != null) {
                topVc = topVc.presentedViewController
            }
            topVc?.presentViewController(activityVc, animated = true, completion = null)
        } catch (e: Exception) {
            onError(e.message ?: "Error compartiendo archivo")
        }
    }
}
