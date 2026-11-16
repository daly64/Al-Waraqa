package tn.alwaraqa.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tn.alwaraqa.app.data.Library
import tn.alwaraqa.app.ui.components.BookmarkGlyph
import tn.alwaraqa.app.ui.components.DocGlyph
import tn.alwaraqa.app.ui.components.SlidersGlyph
import tn.alwaraqa.app.ui.screens.BookmarksScreen
import tn.alwaraqa.app.ui.screens.EditorScreen
import tn.alwaraqa.app.ui.screens.HomeScreen
import tn.alwaraqa.app.ui.screens.OnboardingScreen
import tn.alwaraqa.app.ui.screens.ReaderScreen
import tn.alwaraqa.app.ui.screens.SettingsScreen
import tn.alwaraqa.app.ui.screens.SplashScreen
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Paper
import tn.alwaraqa.app.ui.theme.Sand

private enum class Screen { Onboarding, Splash, Main, Reader, Editor }

private enum class Tab(val label: String) {
    Documents("Documents"),
    Bookmarks("Marque-pages"),
    Settings("Réglages")
}

/**
 * Enchaînement des écrans : bienvenue (au premier lancement), splash, puis les trois onglets
 * ou le lecteur. [incoming] est un PDF envoyé par une autre application.
 */
@Composable
fun AlWaraqaApp(library: Library, incoming: Uri?, onIncomingHandled: () -> Unit) {
    var screen by remember { mutableStateOf(if (library.onboarded) Screen.Splash else Screen.Onboarding) }
    var tab by remember { mutableStateOf(Tab.Documents) }
    var readerUri by remember { mutableStateOf<Uri?>(null) }
    var readerPage by remember { mutableStateOf<Int?>(null) }
    var editorName by remember { mutableStateOf("Document.pdf") }
    var recents by remember { mutableStateOf(library.recents()) }
    var bookmarks by remember { mutableStateOf(library.bookmarks()) }

    fun open(uri: Uri, page: Int?) {
        readerUri = uri
        readerPage = page
        screen = Screen.Reader
    }

    // Un PDF arrive d'une autre application alors que l'app est déjà ouverte
    LaunchedEffect(incoming, screen) {
        if (incoming != null && (screen == Screen.Main || screen == Screen.Reader)) {
            open(incoming, null)
            onIncomingHandled()
        }
    }

    // De retour aux onglets : les listes reflètent la dernière lecture
    LaunchedEffect(screen, tab) {
        if (screen == Screen.Main) {
            recents = library.recents()
            bookmarks = library.bookmarks()
        }
    }

    when (screen) {
        Screen.Onboarding -> OnboardingScreen(
            onDone = {
                library.onboarded = true
                screen = Screen.Splash
            }
        )

        Screen.Splash -> SplashScreen(onFinished = { screen = Screen.Main })

        Screen.Main -> Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (tab) {
                    Tab.Documents -> HomeScreen(
                        recents = recents,
                        onOpen = { open(it, null) },
                        onRemoveRecent = {
                            library.remove(it.uri)
                            recents = library.recents()
                        },
                        onRenamed = { old, new, name ->
                            library.rename(old, new, name)
                            recents = library.recents()
                            bookmarks = library.bookmarks()
                        },
                        onDeleted = {
                            library.forget(it)
                            recents = library.recents()
                            bookmarks = library.bookmarks()
                        }
                    )
                    Tab.Bookmarks -> BookmarksScreen(
                        bookmarks = bookmarks,
                        onOpen = { uri, page -> open(uri, page) },
                        onRemove = {
                            library.removeBookmark(it.uri, it.page)
                            bookmarks = library.bookmarks()
                        }
                    )

                    Tab.Settings -> SettingsScreen(library.settings)
                }
            }
            TabBar(selected = tab, onSelect = { tab = it })
        }

        Screen.Editor -> {
            val uri = readerUri
            if (uri == null) {
                screen = Screen.Main
            } else {
                EditorScreen(
                    uri = uri,
                    name = editorName,
                    onDone = { result ->
                        if (result != null) readerUri = result
                        screen = Screen.Reader
                    }
                )
            }
        }

        Screen.Reader -> {
            val uri = readerUri
            if (uri == null) {
                screen = Screen.Main
            } else {
                ReaderScreen(
                    uri = uri,
                    startPage = readerPage,
                    library = library,
                    onEdit = { n ->
                        editorName = n
                        screen = Screen.Editor
                    },
                    onClose = {
                        screen = Screen.Main
                        readerUri = null
                        readerPage = null
                    }
                )
            }
        }
    }
}

/** Barre d'onglets en bas de l'écran (pas de bouton flottant). */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(Paper)) {
        HorizontalDivider(color = Sand)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val tint: Color = if (active) Brick else InkSoft
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (tab) {
                        Tab.Documents -> DocGlyph(tint)
                        Tab.Bookmarks -> BookmarkGlyph(tint, filled = active)
                        Tab.Settings -> SlidersGlyph(tint)
                    }
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
