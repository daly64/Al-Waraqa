package tn.alwaraqa.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import tn.alwaraqa.app.data.RecentDoc
import tn.alwaraqa.app.ui.components.WaraqaLogo
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Cream
import tn.alwaraqa.app.ui.theme.Heading
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Paper
import tn.alwaraqa.app.ui.theme.Sand
import tn.alwaraqa.app.ui.theme.Terracotta

/** Accueil : reprendre la lecture en cours, rouvrir un document récent ou ouvrir un nouveau PDF. */
@Composable
fun HomeScreen(recents: List<RecentDoc>, onOpen: (Uri) -> Unit) {
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

    var query by rememberSaveable { mutableStateOf("") }
    val wanted = query.trim()

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
        if (recents.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Rechercher un PDF") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
        }

        if (recents.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WaraqaLogo(height = 96.dp, page = Sand, fold = Color(0xFFD9BF98), letter = Cream)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Aucun document pour l'instant",
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Les PDF que vous ouvrez apparaîtront ici.",
                    color = InkSoft,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (wanted.isNotEmpty()) {
            // Recherche par nom parmi les documents déjà ouverts
            val found = recents.filter { it.name.contains(wanted, ignoreCase = true) }
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    SectionLabel(if (found.isEmpty()) "Aucun document trouvé" else "${found.size} document(s)")
                }
                items(found, key = { it.uri }) { doc ->
                    RecentRow(doc) { onOpen(Uri.parse(doc.uri)) }
                    HorizontalDivider(color = Sand)
                }
            }
        } else {
            val current = recents.first()
            val others = recents.drop(1)
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    SectionLabel("Reprendre la lecture")
                    Spacer(Modifier.height(10.dp))
                    ResumeCard(current) { onOpen(Uri.parse(current.uri)) }
                    Spacer(Modifier.height(24.dp))
                }
                if (others.isNotEmpty()) {
                    item { SectionLabel("Récents") }
                    items(others, key = { it.uri }) { doc ->
                        RecentRow(doc) { onOpen(Uri.parse(doc.uri)) }
                        HorizontalDivider(color = Sand)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { picker.launch(arrayOf("application/pdf")) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Ouvrir un PDF", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
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

@Composable
private fun RecentRow(doc: RecentDoc, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
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
