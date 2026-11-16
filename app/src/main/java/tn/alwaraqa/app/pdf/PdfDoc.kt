package tn.alwaraqa.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
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
import kotlin.math.sqrt

/** Un PDF ouvert avec le moteur intégré d'Android. Une seule page peut être rendue à la fois. */
class PdfDoc private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    private val lock = Mutex()
    private var closed = false

    /** Zone utile de chaque page déjà analysée, en fractions de la page (0 à 1). */
    private val contentBounds = HashMap<Int, RectF>()

    val pageCount: Int = renderer.pageCount

    /**
     * Rend la page [index] en image. Les marges blanches sont rognées, puis le contenu est
     * agrandi pour tenir au plus juste dans [maxWidth] × [maxHeight] : le texte est ainsi
     * aussi grand que possible tout en gardant la page entière à l'écran.
     */
    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index !in 0 until pageCount) return@withLock null
            val page = renderer.openPage(index)
            try {
                val area = contentBounds.getOrPut(index) { findContent(page) }
                val contentW = page.width * area.width()
                val contentH = page.height * area.height()
                var scale = min(maxWidth / contentW, maxHeight / contentH)
                // Garde-fou mémoire pour les rendus très agrandis
                val pixels = contentW * scale * contentH * scale
                if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels)
                val w = max(1, (contentW * scale).roundToInt())
                val h = max(1, (contentH * scale).roundToInt())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                val transform = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(-area.left * page.width * scale, -area.top * page.height * scale)
                }
                page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (e: OutOfMemoryError) {
                null
            } finally {
                page.close()
            }
        }
    }

    /**
     * Cherche où se trouve le contenu de la page (texte, images) sur un petit rendu,
     * pour pouvoir rogner les marges blanches. Renvoie des fractions de la page.
     */
    private fun findContent(page: PdfRenderer.Page): RectF {
        val full = RectF(0f, 0f, 1f, 1f)
        return try {
            val scale = PROBE_SIZE / max(page.width, page.height).toFloat()
            val w = max(8, (page.width * scale).roundToInt())
            val h = max(8, (page.height * scale).roundToInt())
            val probe = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            probe.eraseColor(Color.WHITE)
            page.render(probe, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val px = IntArray(w * h)
            probe.getPixels(px, 0, w, 0, 0, w, h)
            probe.recycle()

            val rows = IntArray(h)
            val cols = IntArray(w)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val c = px[y * w + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    if (r < 232 || g < 232 || b < 232) {
                        rows[y]++
                        cols[x]++
                    }
                }
            }
            // Une ligne compte comme du contenu à partir de quelques points sombres (ignore les poussières de scan)
            val need = 2
            val top = rows.indexOfFirst { it >= need }
            val bottom = rows.indexOfLast { it >= need }
            val left = cols.indexOfFirst { it >= need }
            val right = cols.indexOfLast { it >= need }
            if (top < 0 || left < 0 || bottom < top || right < left) return full

            // Petite marge de confort autour du contenu
            val pad = 0.02f
            var l = left / w.toFloat() - pad
            var t = top / h.toFloat() - pad
            var r = (right + 1) / w.toFloat() + pad
            var b = (bottom + 1) / h.toFloat() + pad

            // Une page presque vide n'est pas agrandie démesurément
            if (r - l < MIN_PART) {
                val mid = (l + r) / 2f
                l = mid - MIN_PART / 2f
                r = mid + MIN_PART / 2f
            }
            if (b - t < MIN_PART) {
                val mid = (t + b) / 2f
                t = mid - MIN_PART / 2f
                b = mid + MIN_PART / 2f
            }
            if (l < 0f) { r -= l; l = 0f }
            if (t < 0f) { b -= t; t = 0f }
            if (r > 1f) { l -= r - 1f; r = 1f }
            if (b > 1f) { t -= b - 1f; b = 1f }
            RectF(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
        } catch (e: Throwable) {
            full
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
        private const val PROBE_SIZE = 240
        private const val MIN_PART = 0.5f
        private const val MAX_PIXELS = 14_000_000f

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
