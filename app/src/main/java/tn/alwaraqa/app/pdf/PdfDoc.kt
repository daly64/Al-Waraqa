package tn.alwaraqa.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Un PDF ouvert avec le moteur intégré d'Android. Une seule page peut être rendue à la fois. */
class PdfDoc private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    private val lock = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Rend la page [index] en image, à la taille qui tient dans [maxWidth] × [maxHeight]. */
    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index !in 0 until pageCount) return@withLock null
            val page = renderer.openPage(index)
            try {
                val scale = min(maxWidth.toFloat() / page.width, maxHeight.toFloat() / page.height)
                val w = max(1, (page.width * scale).roundToInt())
                val h = max(1, (page.height * scale).roundToInt())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!closed) {
                closed = true
                try {
                    renderer.close()
                } catch (_: Exception) {
                }
                try {
                    descriptor.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        /** Ouvre le PDF, ou renvoie null s'il est illisible, protégé par mot de passe ou inaccessible. */
        suspend fun open(context: Context, uri: Uri): PdfDoc? = withContext(Dispatchers.IO) {
            var descriptor: ParcelFileDescriptor? = null
            try {
                descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
                PdfDoc(descriptor, PdfRenderer(descriptor))
            } catch (e: Exception) {
                try {
                    descriptor?.close()
                } catch (_: Exception) {
                }
                null
            }
        }

        /** Le nom du fichier tel que le téléphone le présente. */
        fun displayName(context: Context, uri: Uri): String {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(0)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
            } catch (_: Exception) {
            }
            return uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
        }
    }
}
