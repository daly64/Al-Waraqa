package tn.alwaraqa.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tn.alwaraqa.app.data.Bookmark
import tn.alwaraqa.app.ui.components.BookmarkGlyph
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Cream
import tn.alwaraqa.app.ui.theme.Heading
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Sand

/** Les pages marquées, regroupées par document. Un appui rouvre le document à cette page. */
@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onOpen: (Uri, Int) -> Unit,
    onRemove: (Bookmark) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(start = 24.dp, end = 24.dp, top = 40.dp)
    ) {
        Text(
            "Marque-pages",
            color = Ink,
            fontFamily = Heading,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp
        )
        Spacer(Modifier.height(20.dp))

        if (bookmarks.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BookmarkGlyph(tint = Sand, filled = true, size = 64.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Aucun marque-page",
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dans le lecteur, touchez le centre de la page puis le signet pour marquer une page.",
                    color = InkSoft,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val groups = bookmarks.groupBy { it.uri }
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                groups.forEach { (uri, list) ->
                    item(key = "titre-$uri") {
                        Text(
                            list.first().name.uppercase(),
                            color = InkSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    list.sortedBy { it.page }.forEach { mark ->
                        item(key = "$uri#${mark.page}") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .clickable { onOpen(Uri.parse(mark.uri), mark.page) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                BookmarkGlyph(tint = Brick, filled = true, size = 22.dp)
                                Text(
                                    "Page ${mark.page + 1}",
                                    color = Ink,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onRemove(mark) }) {
                                    Text("Retirer", color = Brick, fontSize = 14.sp)
                                }
                            }
                            HorizontalDivider(color = Sand)
                        }
                    }
                    item(key = "fin-$uri") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
