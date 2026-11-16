package tn.alwaraqa.app.pdf

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Numérisation de documents en PDF : détection des bords, recadrage et filtres sont faits
 * sur le téléphone par le scanner de Google (ML Kit). Rien n'est envoyé sur Internet.
 */
object Scanner {

    fun findActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /** Prépare l'écran de numérisation ; [onReady] reçoit la requête à lancer. */
    fun prepare(activity: Activity, onReady: (IntentSenderRequest) -> Unit, onError: (String) -> Unit) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(60)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender -> onReady(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { onError(it.message ?: "Scanner indisponible") }
    }

    /** Extrait le PDF produit par le scanner et le range dans Documents/Al Waraqa. */
    fun save(context: Context, data: android.content.Intent?): Uri? {
        val pdf = GmsDocumentScanningResult.fromActivityResultIntent(data)?.pdf ?: return null
        val name = "Scan " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.FRANCE).format(Date()) + ".pdf"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Al Waraqa")
                }
                val target = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                    ?: return pdf.uri
                context.contentResolver.openInputStream(pdf.uri)!!.use { input ->
                    context.contentResolver.openOutputStream(target)!!.use { out -> input.copyTo(out) }
                }
                target
            } else {
                val dir = File(context.getExternalFilesDir(null), "Scans").apply { mkdirs() }
                val file = File(dir, name)
                context.contentResolver.openInputStream(pdf.uri)!!.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
                Uri.fromFile(file)
            }
        } catch (_: Exception) {
            pdf.uri
        }
    }
}
