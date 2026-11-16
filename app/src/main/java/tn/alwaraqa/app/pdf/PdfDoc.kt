package tn.alwaraqa.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Une entrée du sommaire du document. */
data class OutlineItem(val title: String, val page: Int, val depth: Int)

/** Résultat de l'ouverture d'un PDF. */
sealed interface OpenResult {
    class Ok(val doc: PdfDoc) : OpenResult
    data object NeedPassword : OpenResult
    data object Failed : OpenResult
}

/**
 * Un PDF ouvert avec le moteur PDFium (embarqué dans l'app) : rendu des pages,
 * sommaire, texte et documents protégés par mot de passe, sur toutes les versions d'Android.
 * Les appels sont faits un par un, hors du fil d'affichage.
 */
class PdfDoc private constructor(private val document: PdfDocument) {
    private val lock = Mutex()
    private var closed = false

    /** Zone utile de chaque page déjà analysée, en fractions de la page (0 à 1). */
    private val contentBounds = HashMap<Int, RectF>()

    /** Zone utile commune à tout le document (réglage « taille de texte homogène »). */
    private var commonBounds: RectF? = null

    val pageCount: Int = document.getPageCount()

    /**
     * Rend la page [index] en image. Les marges blanches sont rognées, puis le contenu est
     * agrandi pour tenir au plus juste dans [maxWidth] × [maxHeight]. Avec [uniform], le même
     * recadrage sert à toutes les pages : la taille du texte ne change pas d'une page à l'autre.
     */
    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int, uniform: Boolean): Bitmap? =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (closed || index !in 0 until pageCount) return@withLock null
                try {
                    val area = if (uniform) commonArea() else areaOf(index)
                    val page = document.openPage(index)
                    try {
                        val pageW = max(1, page.getPageWidthPoint()).toFloat()
                        val pageH = max(1, page.getPageHeightPoint()).toFloat()
                        val contentW = pageW * area.width()
                        val contentH = pageH * area.height()
                        var scale = min(maxWidth / contentW, maxHeight / contentH)
                        // Garde-fou mémoire pour les rendus très agrandis
                        val pixels = contentW * scale * contentH * scale
                        if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels)
                        val w = max(1, (contentW * scale).roundToInt())
                        val h = max(1, (contentH * scale).roundToInt())
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.renderPageBitmap(
                            bitmap,
                            -(area.left * pageW * scale).roundToInt(),
                            -(area.top * pageH * scale).roundToInt(),
                            (pageW * scale).roundToInt(),
                            (pageH * scale).roundToInt(),
                            renderAnnot = true,
                            canvasColor = Color.WHITE,
                            pageBackgroundColor = Color.WHITE
                        )
                        bitmap
                    } finally {
                        page.close()
                    }
                } catch (e: Throwable) {
                    null
                }
            }
        }

    /** Le sommaire du document, à plat, avec le niveau de chaque titre. Vide s'il n'y en a pas. */
    suspend fun outline(): List<OutlineItem> = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) return@withLock emptyList()
            try {
                val out = ArrayList<OutlineItem>()
                collectOutline(document.getTableOfContents(), 0, out)
                out
            } catch (e: Throwable) {
                emptyList()
            }
        }
    }

    private fun collectOutline(items: List<PdfDocument.Bookmark>, depth: Int, out: MutableList<OutlineItem>) {
        for (b in items) {
            if (out.size >= MAX_OUTLINE || depth > 8) return
            val title = b.title?.trim().orEmpty()
            val page = b.pageIdx.toInt()
            if (title.isNotEmpty() && page in 0 until pageCount) {
                out.add(OutlineItem(title, page, depth))
            }
            collectOutline(b.children, depth + 1, out)
        }
    }

    /** Le texte de la page [index], vide pour une page scannée ou illisible. */
    suspend fun pageText(index: Int): String = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index !in 0 until pageCount) return@withLock ""
            try {
                val page = document.openPage(index)
                try {
                    val text = page.openTextPage()
                    try {
                        val count = text.textPageCountChars()
                        if (count > 0) text.textPageGetText(0, count).orEmpty() else ""
                    } finally {
                        text.close()
                    }
                } finally {
                    page.close()
                }
            } catch (e: Throwable) {
                ""
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!closed) {
                closed = true
                try {
                    document.close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    // ---- Recadrage des marges ----

    private fun areaOf(index: Int): RectF {
        contentBounds[index]?.let { return it }
        val page = document.openPage(index)
        val area = try {
            findContent(page)
        } finally {
            page.close()
        }
        contentBounds[index] = area
        return area
    }

    /** La plus petite zone qui contient le contenu d'un échantillon de pages du document. */
    private fun commonArea(): RectF {
        commonBounds?.let { return it }
        val step = max(1, pageCount / SAMPLE_PAGES)
        val union = RectF(1f, 1f, 0f, 0f)
        var index = 0
        var seen = 0
        while (index < pageCount && seen < SAMPLE_PAGES + 2) {
            val a = try {
                areaOf(index)
            } catch (e: Throwable) {
                null
            }
            if (a != null) {
                union.left = min(union.left, a.left)
                union.top = min(union.top, a.top)
                union.right = max(union.right, a.right)
                union.bottom = max(union.bottom, a.bottom)
            }
            index += step
            seen++
        }
        val result = if (union.right > union.left && union.bottom > union.top) union else RectF(0f, 0f, 1f, 1f)
        commonBounds = result
        return result
    }

    /**
     * Cherche où se trouve le contenu de la page (texte, images) sur un petit rendu,
     * pour pouvoir rogner les marges blanches. Renvoie des fractions de la page.
     */
    private fun findContent(page: PdfPage): RectF {
        val full = RectF(0f, 0f, 1f, 1f)
        try {
            val pageW = max(1, page.getPageWidthPoint())
            val pageH = max(1, page.getPageHeightPoint())
            val scale = PROBE_SIZE / max(pageW, pageH).toFloat()
            val w = max(8, (pageW * scale).roundToInt())
            val h = max(8, (pageH * scale).roundToInt())
            val probe = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            probe.eraseColor(Color.WHITE)
            page.renderPageBitmap(
                probe, 0, 0, w, h,
                renderAnnot = true,
                canvasColor = Color.WHITE,
                pageBackgroundColor = Color.WHITE
            )
            val px = IntArray(w * h)
            probe.getPixels(px, 0, w, 0, 0, w, h)
            probe.recycle()

            val rows = IntArray(h)
            val cols = IntArray(w)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val c = px[y * w + x]
                    val red = (c shr 16) and 0xFF
                    val green = (c shr 8) and 0xFF
                    val blue = c and 0xFF
                    if (red < 232 || green < 232 || blue < 232) {
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
            if (l < 0f) {
                r -= l
                l = 0f
            }
            if (t < 0f) {
                b -= t
                t = 0f
            }
            if (r > 1f) {
                l -= r - 1f
                r = 1f
            }
            if (b > 1f) {
                t -= b - 1f
                b = 1f
            }
            return RectF(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
        } catch (e: Throwable) {
            return full
        }
    }

    companion object {
        private const val PROBE_SIZE = 240
        private const val MIN_PART = 0.5f
        private const val MAX_PIXELS = 14_000_000f
        private const val SAMPLE_PAGES = 12
        private const val MAX_OUTLINE = 2000

        /** Ouvre le PDF, avec son mot de passe s'il en a un. */
        suspend fun open(context: Context, uri: Uri, password: String?): OpenResult =
            withContext(Dispatchers.IO) {
                var descriptor: ParcelFileDescriptor? = null
                try {
                    val opened = context.contentResolver.openFileDescriptor(uri, "r")
                    if (opened == null) {
                        OpenResult.Failed
                    } else {
                        descriptor = opened
                        val core = PdfiumCore(context.applicationContext)
                        val document = core.newDocument(opened, password)
                        if (document.getPageCount() <= 0) {
                            document.close()
                            OpenResult.Failed
                        } else {
                            OpenResult.Ok(PdfDoc(document))
                        }
                    }
                } catch (e: PdfPasswordException) {
                    closeQuietly(descriptor)
                    OpenResult.NeedPassword
                } catch (e: Throwable) {
                    closeQuietly(descriptor)
                    OpenResult.Failed
                }
            }

        private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
            try {
                descriptor?.close()
            } catch (_: Throwable) {
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
