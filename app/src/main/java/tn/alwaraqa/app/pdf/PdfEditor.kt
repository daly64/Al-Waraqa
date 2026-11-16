package tn.alwaraqa.app.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Une page du nouveau document : d'où elle vient et de combien on la tourne. */
data class EditPage(val id: Long, val source: Int, val page: Int, val rotation: Int)

/** Assemble un nouveau PDF à partir de pages d'un ou plusieurs PDF (texte et liens conservés). */
object PdfEditor {

    /** Écrit le résultat dans [out] ; renvoie faux en cas de problème (mot de passe, fichier abîmé…). */
    suspend fun build(context: Context, sources: List<Uri>, pages: List<EditPage>, out: File): Boolean =
        withContext(Dispatchers.IO) {
            PDFBoxResourceLoader.init(context.applicationContext)
            val opened = ArrayList<PDDocument>()
            val temps = ArrayList<File>()
            try {
                for (uri in sources) {
                    val temp = File.createTempFile("src", ".pdf", context.cacheDir)
                    temps.add(temp)
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        temp.outputStream().use { o -> input.copyTo(o) }
                    }
                    opened.add(PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()))
                }
                val result = PDDocument()
                try {
                    for (item in pages) {
                        val original = opened[item.source].getPage(item.page)
                        val base = original.rotation
                        val copy = result.importPage(original)
                        copy.rotation = ((base + item.rotation) % 360 + 360) % 360
                    }
                    result.save(out)
                } finally {
                    result.close()
                }
                true
            } catch (e: Throwable) {
                false
            } finally {
                opened.forEach {
                    try {
                        it.close()
                    } catch (_: Throwable) {
                    }
                }
                temps.forEach { it.delete() }
            }
        }
}
