package tn.alwaraqa.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tn.alwaraqa.app.pdf.Ocr
import tn.alwaraqa.app.pdf.PdfDoc
import tn.alwaraqa.app.pdf.saveToDocuments
import java.io.File

/** Lit le texte de la page affichée (OCR) ; peut aussi lire tout le document et l'enregistrer en .txt. */
@Composable
fun OcrDialog(doc: PdfDoc, page: Int, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var text by remember(page) { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(page) {
        val bitmap = doc.render(page, 2200, 3000, false)
        text = if (bitmap == null) "" else Ocr.read(context, bitmap)
        bitmap?.recycle()
    }

    AlertDialog(
        onDismissRequest = { if (progress == null) onClose() },
        title = { Text("Texte de la page ${page + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val running = progress
                when {
                    running != null -> {
                        CircularProgressIndicator()
                        Text("Lecture de la page ${running.first} sur ${running.second}…")
                    }

                    text == null -> {
                        CircularProgressIndicator()
                        Text("Lecture en cours…")
                    }

                    text.isNullOrBlank() -> Text("Aucun texte reconnu sur cette page.")
                    else -> SelectionContainer {
                        Text(
                            text.orEmpty(),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                val content = text
                if (progress == null && !content.isNullOrBlank()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(content))
                        Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
                    }) { Text("Copier") }
                }
                if (progress == null) {
                    TextButton(onClick = {
                        scope.launch {
                            val all = StringBuilder()
                            for (i in 0 until doc.pageCount) {
                                progress = (i + 1) to doc.pageCount
                                val bitmap = doc.render(i, 2200, 3000, false)
                                val read = if (bitmap == null) "" else Ocr.read(context, bitmap)
                                bitmap?.recycle()
                                all.append("--- Page ${i + 1} ---\n").append(read).append("\n\n")
                            }
                            val file = File(context.cacheDir, "ocr.txt")
                            file.writeText(all.toString())
                            val saved = saveToDocuments(context, name.removeSuffix(".pdf") + " (texte).txt", "text/plain", file)
                            file.delete()
                            progress = null
                            Toast.makeText(
                                context,
                                if (saved != null) "Texte enregistré dans Documents/Al Waraqa" else "Enregistrement impossible",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }) { Text("Tout le document") }
                }
            }
        },
        dismissButton = {
            if (progress == null) TextButton(onClick = onClose) { Text("Fermer") }
        }
    )
}
