package tn.alwaraqa.app.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import java.io.FileOutputStream

/** Envoie le PDF à une autre application (messagerie, e-mail, etc.). */
fun sharePdf(context: Context, uri: Uri, name: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Partager le PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Impossible de partager ce document", Toast.LENGTH_SHORT).show()
    }
}

/** Ouvre l'écran d'impression d'Android pour ce PDF. */
fun printPdf(context: Context, uri: Uri, name: String, pageCount: Int) {
    try {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(name, PdfPrintAdapter(context, uri, name, pageCount), null)
    } catch (e: Exception) {
        Toast.makeText(context, "Impossible d'imprimer ce document", Toast.LENGTH_SHORT).show()
    }
}

/** Remet le fichier PDF tel quel au service d'impression. */
private class PdfPrintAdapter(
    private val context: Context,
    private val uri: Uri,
    private val name: String,
    private val pageCount: Int
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()
        callback?.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null || destination == null) {
                callback?.onWriteFailed("Fichier inaccessible")
                return
            }
            input.use { source ->
                FileOutputStream(destination.fileDescriptor).use { target ->
                    source.copyTo(target)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}
