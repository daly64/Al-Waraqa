package tn.alwaraqa.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tn.alwaraqa.app.pdf.EditPage
import tn.alwaraqa.app.pdf.OpenResult
import tn.alwaraqa.app.pdf.PdfDoc
import tn.alwaraqa.app.pdf.PdfEditor
import tn.alwaraqa.app.pdf.saveToDocuments
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Cream
import tn.alwaraqa.app.ui.theme.Heading
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Paper
import tn.alwaraqa.app.ui.theme.Sand
import java.io.File

/**
 * Modifier les pages d'un PDF : pivoter, supprimer, réordonner, ajouter un autre PDF.
 * [onDone] reçoit le PDF à rouvrir (le même, ou la copie enregistrée), ou null si on annule.
 */
@Composable
fun EditorScreen(uri: Uri, name: String, onDone: (Uri?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources = remember { mutableStateListOf(uri) }
    val docs = remember { mutableStateListOf<PdfDoc?>() }
    val pages = remember { mutableStateListOf<EditPage>() }
    val selected = remember { mutableStateListOf<Long>() }
    var nextId by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }

    suspend fun addSource(source: Uri): Boolean {
        return when (val r = PdfDoc.open(context, source, null)) {
            is OpenResult.Ok -> {
                val s = docs.size
                docs.add(r.doc)
                for (p in 0 until r.doc.pageCount) {
                    pages.add(EditPage(nextId++, s, p, 0))
                }
                true
            }

            else -> false
        }
    }

    LaunchedEffect(Unit) {
        if (!addSource(uri)) failed = true
        loading = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            scope.launch {
                sources.add(picked)
                if (addSource(picked)) {
                    changed = true
                } else {
                    sources.removeAt(sources.size - 1)
                    Toast.makeText(context, "Ce PDF est protégé ou illisible", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun leave(result: Uri?) {
        scope.launch {
            docs.forEach { it?.close() }
            onDone(result)
        }
    }

    BackHandler { if (changed) askLeave = true else leave(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(top = 36.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Text("Modifier les pages", color = Ink, fontFamily = Heading, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        Text(
            if (selected.isEmpty()) "${pages.size} pages · touchez des pages pour les sélectionner"
            else "${selected.size} sélectionnée(s) sur ${pages.size}",
            color = InkSoft,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Brick)
                failed -> Text(
                    "Ce PDF est protégé par un mot de passe ou illisible : il ne peut pas être modifié.",
                    color = InkSoft,
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pages, key = { it.id }) { item ->
                        Thumb(
                            doc = docs.getOrNull(item.source),
                            item = item,
                            number = pages.indexOf(item) + 1,
                            selected = item.id in selected,
                            onClick = {
                                if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val one = selected.size == 1
            ToolButton("Pivoter", selected.isNotEmpty()) {
                for (i in pages.indices) {
                    val p = pages[i]
                    if (p.id in selected) pages[i] = p.copy(rotation = (p.rotation + 90) % 360)
                }
                changed = true
            }
            ToolButton("Supprimer", selected.isNotEmpty() && selected.size < pages.size) {
                pages.removeAll { it.id in selected }
                selected.clear()
                changed = true
            }
            ToolButton("Reculer", one && pages.indexOfFirst { it.id == selected[0] } > 0) {
                val i = pages.indexOfFirst { it.id == selected[0] }
                val p = pages.removeAt(i)
                pages.add(i - 1, p)
                changed = true
            }
            ToolButton("Avancer", one && pages.indexOfFirst { it.id == selected[0] } < pages.size - 1) {
                val i = pages.indexOfFirst { it.id == selected[0] }
                val p = pages.removeAt(i)
                pages.add(i + 1, p)
                changed = true
            }
            ToolButton("Ajouter un PDF", !loading && !failed) { picker.launch(arrayOf("application/pdf")) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (changed) askLeave = true else leave(null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("Annuler", color = Brick, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { askSave = true },
                enabled = changed && !saving && !failed,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text(if (saving) "Enregistrement…" else "Enregistrer", fontWeight = FontWeight.SemiBold) }
        }
    }

    fun save(replace: Boolean) {
        askSave = false
        saving = true
        scope.launch {
            val out = File(context.cacheDir, "edition.pdf")
            val ok = PdfEditor.build(context, sources.toList(), pages.toList(), out)
            var result: Uri? = null
            if (ok) {
                if (replace) {
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")!!.use { o ->
                            out.inputStream().use { it.copyTo(o) }
                        }
                        result = uri
                    } catch (e: Exception) {
                        Toast.makeText(context, "Impossible de remplacer l'original : copie enregistrée à la place", Toast.LENGTH_LONG).show()
                    }
                }
                if (result == null) {
                    val copyName = name.removeSuffix(".pdf").removeSuffix(".PDF") + " (modifié).pdf"
                    result = saveToDocuments(context, copyName, "application/pdf", out)
                    if (result != null) {
                        Toast.makeText(context, "Copie enregistrée dans Documents/Al Waraqa", Toast.LENGTH_LONG).show()
                    }
                }
            }
            out.delete()
            saving = false
            if (result != null) {
                leave(result)
            } else {
                Toast.makeText(context, "L'enregistrement a échoué", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (askSave) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = { Text("Enregistrer les modifications") },
            text = { Text("Remplacer le PDF d'origine, ou garder l'original et créer une copie ?") },
            confirmButton = { TextButton(onClick = { save(replace = false) }) { Text("Enregistrer une copie") } },
            dismissButton = { TextButton(onClick = { save(replace = true) }) { Text("Remplacer", color = Brick) } }
        )
    }
    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("Abandonner les modifications ?") },
            confirmButton = { TextButton(onClick = { askLeave = false; leave(null) }) { Text("Abandonner", color = Brick) } },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Continuer") } }
        )
    }
}

@Composable
private fun ToolButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Thumb(doc: PdfDoc?, item: EditPage, number: Int, selected: Boolean, onClick: () -> Unit) {
    var bitmap by remember(doc, item.page) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, item.page) {
        bitmap = doc?.render(item.page, 300, 420, false)?.asImageBitmap()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp))
                .background(Paper)
                .border(if (selected) 3.dp else 1.dp, if (selected) Brick else Sand, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val b = bitmap
            if (b != null) {
                Image(
                    bitmap = b,
                    contentDescription = "Page $number",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .graphicsLayer(rotationZ = item.rotation.toFloat())
                )
            }
        }
        Text("$number", color = if (selected) Brick else InkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
