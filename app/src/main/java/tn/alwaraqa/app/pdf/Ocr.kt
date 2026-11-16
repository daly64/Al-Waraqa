package tn.alwaraqa.app.pdf

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reconnaissance de texte (OCR) sur le téléphone, avec Tesseract : français, arabe et anglais.
 * Les modèles de langue sont dans l'app, rien n'est envoyé sur Internet.
 */
object Ocr {
    private const val LANGUAGES = "fra+ara+eng"
    private val lock = Mutex()
    private var api: TessBaseAPI? = null

    private fun ensureEngine(context: Context): TessBaseAPI {
        api?.let { return it }
        val root = File(context.filesDir, "tess")
        val data = File(root, "tessdata").apply { mkdirs() }
        for (lang in listOf("fra", "ara", "eng")) {
            val target = File(data, "$lang.traineddata")
            if (!target.exists() || target.length() == 0L) {
                context.assets.open("tessdata/$lang.traineddata").use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
            }
        }
        val engine = TessBaseAPI()
        if (!engine.init(root.absolutePath, LANGUAGES)) {
            engine.recycle()
            throw IllegalStateException("OCR indisponible")
        }
        api = engine
        return engine
    }

    /** Le texte lu sur l'image ; vide si rien n'est reconnu. */
    suspend fun read(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        lock.withLock {
            try {
                val engine = ensureEngine(context.applicationContext)
                engine.setImage(bitmap)
                val text = engine.getUTF8Text().orEmpty()
                engine.clear()
                text.trim()
            } catch (e: Throwable) {
                ""
            }
        }
    }
}
