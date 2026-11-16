package tn.alwaraqa.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import tn.alwaraqa.app.data.RecentDoc
import tn.alwaraqa.app.pdf.PhoneLibrary
import tn.alwaraqa.app.pdf.PhonePdf
import tn.alwaraqa.app.pdf.Scanner
import tn.alwaraqa.app.ui.components.DocGlyph
import tn.alwaraqa.app.ui.components.WaraqaLogo
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Cream
import tn.alwaraqa.app.ui.theme.Heading
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Paper
import tn.alwaraqa.app.ui.theme.Sand
import tn.alwaraqa.app.ui.theme.SandSoft
import tn.alwaraqa.app.ui.theme.Terracotta
import java.text.DateFormat
import java.util.Date

/**
 * Accueil : reprendre la lecture en cours, les documents récents, puis tous les PDF
 * du téléphone. Le champ de recherche filtre les deux listes par nom.
 */
@Composable
fun HomeScreen(recents: List<RecentDoc>, onOpen: (Uri) -> Unit, onRemoveRecent: (RecentDoc) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Garde l'accès au fichier pour pouvoir le rouvrir plus tard
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            onOpen(uri)
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val saved = Scanner.save(context, result.data)
            if (saved != null) {
                Toast.makeText(context, "Document enregistré dans Documents/Al Waraqa", Toast.LENGTH_SHORT).show()
                onOpen(saved)
            }
        }
    }
    fun startScan() {
        val activity = Scanner.findActivity(context)
        if (activity == null) return
        Scanner.prepare(
            activity,
            onReady = { scanLauncher.launch(it) },
            onError = { Toast.makeText(context, "Numérisation indisponible sur cet appareil", Toast.LENGTH_LONG).show() }
        )
    }
    var hasAccess by remember { mutableStateOf(PhoneLibrary.hasAccess(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    var phone by remember { mutableStateOf<List<PhonePdf>?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasAccess = PhoneLibrary.hasAccess(context)
        refresh++
    }

    // Au retour des réglages Android, l'accès a peut-être été donné
    LifecycleResumeEffect(Unit) {
        hasAccess = PhoneLibrary.hasAccess(context)
        refresh++
        onPauseOrDispose { }
    }
    LaunchedEffect(hasAccess, refresh) {
        phone = if (hasAccess) PhoneLibrary.scan(context) else emptyList()
    }

    var query by rememberSaveable { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<RecentDoc?>(null) }
    val wanted = query.trim()
    val phoneList = phone.orEmpty()
    val recentShown = if (wanted.isEmpty()) recents else recents.filter { it.name.contains(wanted, ignoreCase = true) }
    val phoneShown = if (wanted.isEmpty()) phoneList else phoneList.filter { it.name.contains(wanted, ignoreCase = true) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 16.dp)
    ) {
        Text(
            "Documents",
            color = Ink,
            fontFamily = Heading,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Rechercher un PDF") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Reprendre la lecture (hors recherche)
            if (wanted.isEmpty() && recents.isNotEmpty()) {
                val current = recents.first()
                item(key = "reprendre") {
                    SectionLabel("Reprendre la lecture")
                    Spacer(Modifier.height(10.dp))
                    ResumeCard(current) { onOpen(Uri.parse(current.uri)) }
                    Spacer(Modifier.height(24.dp))
                }
            }

            val recentRows = if (wanted.isEmpty()) recentShown.drop(1) else recentShown
            if (recentRows.isNotEmpty()) {
                item(key = "titre-recents") { SectionLabel("Récents") }
                items(recentRows, key = { "r-" + it.uri }) { doc ->
                    RecentRow(
                        doc = doc,
                        onClick = { onOpen(Uri.parse(doc.uri)) },
                        onLongClick = { toRemove = doc }
                    )
                    HorizontalDivider(color = Sand)
                }
                item(key = "espace-recents") { Spacer(Modifier.height(24.dp)) }
            }

            item(key = "titre-telephone") {
                SectionLabel(
                    if (hasAccess && phone != null) "Sur le téléphone · ${phoneShown.size}" else "Sur le téléphone"
                )
                Spacer(Modifier.height(4.dp))
            }
            if (!hasAccess) {
                item(key = "acces") {
                    AccessCard(
                        onAllow = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                PhoneLibrary.openAccessSettings(context)
                            } else {
                                askPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }
                    )
                }
            } else if (phone != null && phoneShown.isEmpty()) {
                item(key = "vide") {
                    Text(
                        if (wanted.isEmpty()) "Aucun PDF trouvé sur le téléphone." else "Aucun PDF ne correspond.",
                        color = InkSoft,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(phoneShown, key = { "t-" + it.uri }) { pdf ->
                    PhoneRow(pdf) { onOpen(pdf.uri) }
                    HorizontalDivider(color = Sand)
                }
            }

            if (recents.isEmpty() && !hasAccess) {
                item(key = "bienvenue") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WaraqaLogo(height = 72.dp, page = Sand, fold = Color(0xFFD9BF98), letter = Cream)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Les PDF que vous ouvrez apparaîtront aussi ici.",
                            color = InkSoft,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { startScan() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("Numériser", color = Brick, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("Ouvrir un PDF", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    toRemove?.let { doc ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Retirer des récents ?") },
            text = { Text("« ${doc.name} » ne sera plus dans la liste. Le fichier n'est pas supprimé du téléphone.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveRecent(doc)
                    toRemove = null
                }) { Text("Retirer") }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = InkSoft,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun AccessCard(onAllow: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SandSoft)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Retrouver tous vos PDF", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Autorisez l'accès aux fichiers pour voir ici tous les PDF du téléphone (Téléchargements, WhatsApp, documents…). " +
                "Al Waraqa ne lit que les PDF et rien ne quitte votre téléphone.",
            color = InkSoft,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        OutlinedButton(onClick = onAllow, shape = RoundedCornerShape(12.dp)) {
            Text("Autoriser l'accès", color = Brick, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResumeCard(doc: RecentDoc, onClick: () -> Unit) {
    val progress = if (doc.pageCount > 0) (doc.lastPage + 1f) / doc.pageCount else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brick)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WaraqaLogo(height = 64.dp, page = Paper, fold = Sand, letter = Brick)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                doc.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Page ${doc.lastPage + 1} sur ${doc.pageCount}",
                color = Color(0xFFFBE9DC),
                fontSize = 14.sp
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Terracotta)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(Paper)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(doc: RecentDoc, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Retirer des récents")
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DocGlyph(Brick)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                doc.name,
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Page ${doc.lastPage + 1} sur ${doc.pageCount}",
                color = InkSoft,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PhoneRow(pdf: PhonePdf, onClick: () -> Unit) {
    val context = LocalContext.current
    val details = remember(pdf) {
        val size = Formatter.formatShortFileSize(context, pdf.size)
        val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(pdf.modified))
        "$size · $date"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DocGlyph(InkSoft)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                pdf.name,
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(details, color = InkSoft, fontSize = 13.sp)
        }
    }
}
