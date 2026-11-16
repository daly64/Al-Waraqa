package tn.alwaraqa.app.ui.components

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import tn.alwaraqa.app.pdf.FileOps
import tn.alwaraqa.app.pdf.FileResult

/** Renommer ou supprimer un PDF, avec la confirmation d'Android quand il la demande. */
class FileActions internal constructor(private val context: Context) {
    internal var launcher: ((IntentSenderRequest) -> Unit)? = null
    private var onResult: ((Boolean) -> Unit)? = null

    internal fun result(ok: Boolean) {
        val callback = onResult
        onResult = null
        callback?.invoke(ok)
    }

    /** [done] reçoit le nouvel emplacement, ou null si ça n'a pas marché. */
    fun rename(uri: Uri, newName: String, done: (Uri?) -> Unit) {
        when (val r = FileOps.rename(context, uri, newName)) {
            is FileResult.Ok -> done(r.uri)
            is FileResult.Confirm -> {
                onResult = { ok ->
                    if (!ok) {
                        done(null)
                    } else {
                        when (val again = FileOps.rename(context, uri, newName)) {
                            is FileResult.Ok -> done(again.uri)
                            else -> done(null)
                        }
                    }
                }
                launcher?.invoke(r.request) ?: done(null)
            }

            FileResult.Failed -> done(null)
        }
    }

    /** [done] reçoit vrai si le fichier a bien été supprimé. */
    fun delete(uri: Uri, done: (Boolean) -> Unit) {
        when (val r = FileOps.delete(context, uri)) {
            is FileResult.Ok -> done(true)
            is FileResult.Confirm -> {
                onResult = { ok -> done(ok) }
                launcher?.invoke(r.request) ?: done(false)
            }

            FileResult.Failed -> done(false)
        }
    }
}

@Composable
fun rememberFileActions(): FileActions {
    val context = LocalContext.current
    val actions = remember { FileActions(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        actions.result(it.resultCode == Activity.RESULT_OK)
    }
    actions.launcher = { launcher.launch(it) }
    return actions
}

/** Boîte de saisie du nouveau nom ; l'extension .pdf est ajoutée si besoin. */
@Composable
fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentName.removeSuffix(".pdf").removeSuffix(".PDF")) }
    val clean = text.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = { Text(".pdf") },
                keyboardOptions = KeyboardOptions.Default
            )
        },
        confirmButton = {
            TextButton(enabled = clean.isNotEmpty(), onClick = { onConfirm("$clean.pdf") }) { Text("Renommer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer ce PDF ?") },
        text = { Text("« $name » sera supprimé du téléphone. Cette action est définitive.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
