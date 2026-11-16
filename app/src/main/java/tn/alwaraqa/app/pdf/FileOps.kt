package tn.alwaraqa.app.pdf

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import java.io.File

/** Résultat d'une opération sur un fichier. */
sealed interface FileResult {
    class Ok(val uri: Uri) : FileResult

    /** Android demande une confirmation à l'utilisateur ; [retry] : refaire l'opération ensuite. */
    class Confirm(val request: IntentSenderRequest, val retry: Boolean) : FileResult

    data object Failed : FileResult
}

/** Renommer et supprimer un PDF, quelle que soit sa provenance. */
object FileOps {

    fun rename(context: Context, uri: Uri, newName: String): FileResult {
        val resolver = context.contentResolver
        return try {
            when {
                uri.scheme == "file" -> {
                    val old = File(uri.path ?: return FileResult.Failed)
                    val target = File(old.parentFile, newName)
                    if (target.exists()) FileResult.Failed
                    else if (old.renameTo(target)) FileResult.Ok(Uri.fromFile(target)) else FileResult.Failed
                }

                uri.authority == MediaStore.AUTHORITY -> {
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
                    try {
                        if (resolver.update(uri, values, null, null) > 0) FileResult.Ok(uri) else FileResult.Failed
                    } catch (e: SecurityException) {
                        confirmFor(context, uri, e, write = true) ?: FileResult.Failed
                    }
                }

                else -> {
                    val renamed = DocumentsContract.renameDocument(resolver, uri, newName)
                    if (renamed != null) FileResult.Ok(renamed) else FileResult.Failed
                }
            }
        } catch (e: Exception) {
            FileResult.Failed
        }
    }

    fun delete(context: Context, uri: Uri): FileResult {
        val resolver = context.contentResolver
        return try {
            when {
                uri.scheme == "file" -> {
                    if (File(uri.path ?: return FileResult.Failed).delete()) FileResult.Ok(uri) else FileResult.Failed
                }

                uri.authority == MediaStore.AUTHORITY -> {
                    try {
                        if (resolver.delete(uri, null, null) > 0) FileResult.Ok(uri) else FileResult.Failed
                    } catch (e: SecurityException) {
                        confirmFor(context, uri, e, write = false) ?: FileResult.Failed
                    }
                }

                else -> if (DocumentsContract.deleteDocument(resolver, uri)) FileResult.Ok(uri) else FileResult.Failed
            }
        } catch (e: Exception) {
            FileResult.Failed
        }
    }

    private fun confirmFor(context: Context, uri: Uri, e: SecurityException, write: Boolean): FileResult? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = if (write) MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender
            else MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender
            // Après une demande d'écriture il faut refaire le renommage ; la suppression est faite par Android
            return FileResult.Confirm(IntentSenderRequest.Builder(sender).build(), retry = write)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            val sender = e.userAction.actionIntent.intentSender
            return FileResult.Confirm(IntentSenderRequest.Builder(sender).build(), retry = true)
        }
        return null
    }
}

/** Range un fichier dans Documents/Al Waraqa (ou dans les fichiers de l'app avant Android 10). */
fun saveToDocuments(context: Context, name: String, mime: String, source: File): Uri? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Al Waraqa")
            }
            val target = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
            context.contentResolver.openOutputStream(target)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            target
        } else {
            val dir = File(context.getExternalFilesDir(null), "Documents").apply { mkdirs() }
            val file = File(dir, name)
            source.copyTo(file, overwrite = true)
            Uri.fromFile(file)
        }
    } catch (e: Exception) {
        null
    }
}
