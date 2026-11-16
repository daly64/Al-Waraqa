package tn.alwaraqa.app.pdf

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un PDF présent sur le téléphone. */
data class PhonePdf(val uri: Uri, val name: String, val size: Long, val modified: Long)

/**
 * Tous les PDF du téléphone, trouvés par l'index des fichiers d'Android.
 * Depuis Android 11, il faut l'autorisation « Accès à tous les fichiers ».
 */
object PhoneLibrary {

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** L'écran des réglages Android où l'on donne l'accès aux fichiers (Android 11 et plus). */
    fun openAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
    }

    suspend fun scan(context: Context): List<PhonePdf> = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext emptyList()
        val found = ArrayList<PhonePdf>()
        try {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arrayOf("application/pdf", "%.pdf"),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (cursor.moveToNext() && found.size < MAX_FILES) {
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    val name = cursor.getString(nameCol) ?: continue
                    found.add(
                        PhonePdf(
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                            name = name,
                            size = size,
                            modified = cursor.getLong(dateCol) * 1000L
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        found
    }

    private const val MAX_FILES = 2000
}
