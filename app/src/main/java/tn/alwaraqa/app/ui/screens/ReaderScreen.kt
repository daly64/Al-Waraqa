package tn.alwaraqa.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import tn.alwaraqa.app.data.Library
import tn.alwaraqa.app.data.ReadingTheme
import tn.alwaraqa.app.data.RecentDoc
import tn.alwaraqa.app.pdf.OpenResult
import tn.alwaraqa.app.pdf.OutlineItem
import tn.alwaraqa.app.pdf.PdfDoc
import tn.alwaraqa.app.pdf.printPdf
import tn.alwaraqa.app.pdf.sharePdf
import tn.alwaraqa.app.ui.components.BookmarkGlyph
import tn.alwaraqa.app.ui.reader.BookPager
import tn.alwaraqa.app.ui.reader.PageSound
import tn.alwaraqa.app.ui.reader.ReaderKeys
import tn.alwaraqa.app.ui.reader.ReadingLook
import tn.alwaraqa.app.ui.reader.ScrollPager
import tn.alwaraqa.app.ui.reader.lookFor
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Paper
import tn.alwaraqa.app.ui.theme.Sand
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Phase { Loading, NeedPassword, Failed, Ready }

private data class SearchHit(val page: Int, val snippet: String)

/**
 * Le lecteur, en plein écran : la page occupe tout l'écran, un appui au centre
 * affiche ou masque les barres (recherche, marque-page, sommaire, partage…).
 */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun ReaderScreen(uri: Uri, startPage: Int?, library: Library, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val settings = library.settings
    val look = lookFor(settings.theme)
    val key = uri.toString()
    val name = remember(uri) { PdfDoc.displayName(context, uri) }

    var phase by remember(uri) { mutableStateOf(Phase.Loading) }
    var password by remember(uri) { mutableStateOf<String?>(null) }
    var attempt by remember(uri) { mutableIntStateOf(0) }
    var doc by remember(uri) { mutableStateOf<PdfDoc?>(null) }
    var index by remember(uri) { mutableIntStateOf(0) }
    var barsVisible by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val pages = remember(uri) { mutableStateMapOf<Int, ImageBitmap>() }
    val currentIndex by rememberUpdatedState(index)
    val renderedFor = remember(uri) { arrayOf("") }
    var zoom by remember { mutableFloatStateOf(1f) }
    // Page courante rendue plus finement quand on zoome, pour que le texte reste net
    var sharp by remember(uri) { mutableStateOf<Pair<Int, ImageBitmap>?>(null) }
    var marked by remember(uri) { mutableStateOf(false) }

    var menuOpen by remember { mutableStateOf(false) }
    var goToOpen by remember { mutableStateOf(false) }
    var outlineOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var outline by remember(uri) { mutableStateOf<List<OutlineItem>?>(null) }

    // Mot recherché, surligné sur les pages, et pages où il apparaît
    var highlight by remember(uri) { mutableStateOf<String?>(null) }
    var hitPages by remember(uri) { mutableStateOf<List<Int>>(emptyList()) }

    // Pages tournées à la demande (touches de volume)
    val flips = remember { MutableSharedFlow<Boolean>(extraBufferCapacity = 4) }

    // Ouverture du document (avec le mot de passe s'il en faut un), reprise à la dernière page lue
    LaunchedEffect(uri, attempt) {
        phase = Phase.Loading
        when (val result = PdfDoc.open(context, uri, password)) {
            is OpenResult.Ok -> {
                val last = startPage ?: library.find(key)?.lastPage ?: 0
                index = last.coerceIn(0, result.doc.pageCount - 1)
                doc = result.doc
                phase = Phase.Ready
            }

            OpenResult.NeedPassword -> phase = Phase.NeedPassword

            OpenResult.Failed -> {
                library.remove(key)
                phase = Phase.Failed
            }
        }
    }

    // Fermeture : on mémorise la page et on libère le fichier
    DisposableEffect(uri) {
        onDispose {
            val d = doc
            if (d != null) {
                library.save(RecentDoc(key, name, d.pageCount, currentIndex, System.currentTimeMillis()))
                GlobalScope.launch { d.close() }
            }
        }
    }

    // Écran toujours allumé pendant la lecture, si le réglage le demande
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val current = doc
    val uniform = settings.uniformCrop
    val bookMode = settings.bookMode
    val night = look.nightPages

    // Touches de volume : volume bas = page suivante, volume haut = page précédente
    DisposableEffect(settings.volumeKeys) {
        ReaderKeys.active = settings.volumeKeys
        onDispose { ReaderKeys.active = false }
    }
    LaunchedEffect(bookMode, current) {
        ReaderKeys.presses.collect { forward ->
            val d = current ?: return@collect
            if (bookMode) {
                flips.tryEmit(forward)
            } else {
                index = (index + if (forward) 1 else -1).coerceIn(0, d.pageCount - 1)
            }
        }
    }

    // Mode Livre : rendu de la page courante et de ses voisines, pour une animation fluide
    LaunchedEffect(current, index, viewSize, uniform, bookMode, night, highlight) {
        if (current == null || viewSize.width == 0 || viewSize.height == 0) return@LaunchedEffect
        if (bookMode) {
            // Changement de taille (rotation) ou de recadrage : les images sont à refaire
            val stamp = "${viewSize.width}x${viewSize.height}-$uniform-$night-$highlight"
            if (renderedFor[0] != stamp) {
                pages.clear()
                renderedFor[0] = stamp
            }
            // Limite la taille des images pour ménager la mémoire
            val limit = min(1f, 1600f / viewSize.width)
            val w = (viewSize.width * limit).roundToInt()
            val h = (viewSize.height * limit).roundToInt()
            for (i in listOf(index, index + 1, index - 1)) {
                if (i in 0 until current.pageCount && !pages.containsKey(i)) {
                    val bitmap = current.render(i, w, h, uniform, night, highlight)
                    if (bitmap != null) pages[i] = bitmap.asImageBitmap()
                }
            }
            pages.keys.filter { it < index - 2 || it > index + 2 }.forEach { pages.remove(it) }
        } else {
            pages.clear()
            renderedFor[0] = ""
        }
        library.save(RecentDoc(key, name, current.pageCount, index, System.currentTimeMillis()))
    }

    // Zoom : après une courte pause, la page est redessinée à la finesse du zoom
    val sharpness = when {
        !bookMode || zoom <= 1.15f -> 1
        zoom <= 2.2f -> 2
        else -> 3
    }
    LaunchedEffect(current, index, sharpness, viewSize, uniform, night, highlight) {
        sharp = null
        if (current == null || sharpness == 1 || viewSize.width == 0 || viewSize.height == 0) {
            sharp = null
            return@LaunchedEffect
        }
        delay(220)
        val bitmap = current.render(
            index, viewSize.width * sharpness, viewSize.height * sharpness, uniform, night, highlight
        )
        if (bitmap != null) sharp = index to bitmap.asImageBitmap()
    }

    LaunchedEffect(index, phase) { marked = library.isBookmarked(key, index) }

    LaunchedEffect(outlineOpen, current) {
        if (outlineOpen && current != null && outline == null) outline = current.outline()
    }

    BackHandler {
        if (searchOpen) searchOpen = false else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(look.ground)
            .onSizeChanged { viewSize = it }
    ) {
        when {
            phase == Phase.Failed -> Message(
                title = "Impossible d'ouvrir ce PDF",
                text = "Le fichier est peut-être abîmé, déplacé ou inaccessible.",
                look = look,
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center)
            )

            phase == Phase.NeedPassword -> PasswordDialog(
                wrong = password != null,
                onSubmit = {
                    password = it
                    attempt++
                },
                onCancel = onClose
            )

            current == null || phase == Phase.Loading ->
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = look.accent)

            else -> {
                if (bookMode) {
                    BookPager(
                        pageCount = current.pageCount,
                        index = index,
                        pages = pages,
                        sharpPage = sharp?.takeIf { it.first == index }?.second,
                        paper = look.paper,
                        filter = look.filter,
                        flipRequests = flips,
                        onIndexChange = { index = it.coerceIn(0, current.pageCount - 1) },
                        onTurned = {
                            if (settings.sound) PageSound.play()
                            if (settings.haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onCenterTap = { barsVisible = !barsVisible },
                        onZoomChange = { zoom = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ScrollPager(
                        doc = current,
                        index = index,
                        widthPx = viewSize.width,
                        uniformCrop = uniform,
                        night = night,
                        highlight = highlight,
                        paper = look.paper,
                        filter = look.filter,
                        onIndexChange = { index = it.coerceIn(0, current.pageCount - 1) },
                        onTap = { barsVisible = !barsVisible },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Barre du haut
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(look.bar)
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = look.text)
                        }
                        Text(
                            name,
                            color = look.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Rechercher dans le document", tint = look.text)
                        }
                        IconButton(
                            onClick = {
                                marked = library.toggleBookmark(key, name, index)
                                if (settings.haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier.semantics {
                                contentDescription = if (marked) "Retirer le marque-page" else "Marquer la page"
                            }
                        ) {
                            BookmarkGlyph(tint = if (marked) look.accent else look.text, filled = marked)
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'actions", tint = look.text)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Sommaire") },
                                    onClick = {
                                        menuOpen = false
                                        outlineOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Aller à la page") },
                                    onClick = {
                                        menuOpen = false
                                        goToOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Thème : ${settings.theme.label}") },
                                    onClick = {
                                        val all = ReadingTheme.entries
                                        settings.theme = all[(settings.theme.ordinal + 1) % all.size]
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Partager") },
                                    onClick = {
                                        menuOpen = false
                                        sharePdf(context, uri, name)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Imprimer") },
                                    onClick = {
                                        menuOpen = false
                                        printPdf(context, uri, name, current.pageCount)
                                    }
                                )
                            }
                        }
                    }
                }

                // Mot surligné : passer d'une page trouvée à l'autre
                val word = highlight
                if (word != null) {
                    HighlightPill(
                        word = word,
                        index = index,
                        hitPages = hitPages,
                        look = look,
                        onGo = { index = it },
                        onClear = {
                            highlight = null
                            hitPages = emptyList()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (barsVisible) 76.dp else 20.dp)
                    )
                }

                // Barre du bas
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    PageBar(
                        index = index,
                        pageCount = current.pageCount,
                        look = look,
                        onJump = { index = it },
                        onLabelClick = { goToOpen = true }
                    )
                }

                if (goToOpen) {
                    GoToDialog(
                        pageCount = current.pageCount,
                        onGo = {
                            index = it
                            goToOpen = false
                        },
                        onDismiss = { goToOpen = false }
                    )
                }

                if (outlineOpen) {
                    OutlineDialog(
                        outline = outline,
                        onPick = {
                            index = it
                            outlineOpen = false
                        },
                        onDismiss = { outlineOpen = false }
                    )
                }

                if (searchOpen) {
                    SearchPanel(
                        doc = current,
                        initial = highlight.orEmpty(),
                        onPick = { page, query, pagesFound ->
                            highlight = query
                            hitPages = pagesFound
                            index = page
                            searchOpen = false
                            barsVisible = false
                        },
                        onClose = { searchOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, text: String, look: ReadingLook, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, color = look.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(text, color = look.textSoft, fontSize = 15.sp, textAlign = TextAlign.Center)
        Button(onClick = onClose) { Text("Retour") }
    }
}

/** Barre du bas : numéro de page (un appui ouvre « Aller à la page ») et curseur. */
@Composable
private fun PageBar(
    index: Int,
    pageCount: Int,
    look: ReadingLook,
    onJump: (Int) -> Unit,
    onLabelClick: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue.roundToInt() else index
    Row(
        Modifier
            .fillMaxWidth()
            .background(look.bar)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onLabelClick) {
            Text(
                "Page ${shown + 1} / $pageCount",
                color = look.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (pageCount > 1) {
            Slider(
                value = if (dragging) dragValue else index.toFloat(),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    onJump(dragValue.roundToInt().coerceIn(0, pageCount - 1))
                    dragging = false
                },
                valueRange = 0f..(pageCount - 1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = look.accent,
                    activeTrackColor = look.accent,
                    inactiveTrackColor = look.line
                ),
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(Modifier.size(1.dp))
        }
    }
}

@Composable
private fun GoToDialog(pageCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    fun submit() {
        val n = value.trim().toIntOrNull()
        if (n != null) onGo(n.coerceIn(1, pageCount) - 1)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aller à la page") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { new -> value = new.filter { it.isDigit() }.take(6) },
                label = { Text("Numéro de page (1 à $pageCount)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() })
            )
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Aller") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun PasswordDialog(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Document protégé") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (wrong) "Mot de passe incorrect. Réessayez."
                    else "Ce PDF est protégé. Saisissez son mot de passe pour l'ouvrir."
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (value.isNotEmpty()) onSubmit(value) })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotEmpty()) onSubmit(value) }) { Text("Ouvrir") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } }
    )
}

@Composable
private fun OutlineDialog(outline: List<OutlineItem>?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sommaire") },
        text = {
            when {
                outline == null -> CircularProgressIndicator(color = Brick)
                outline.isEmpty() -> Text("Ce document n'a pas de sommaire.")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(outline) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onPick(item.page) }
                                .padding(start = (item.depth * 14).dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                item.title,
                                color = Ink,
                                fontSize = 15.sp,
                                fontWeight = if (item.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${item.page + 1}", color = InkSoft, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

/** Recherche d'un mot ou d'une phrase dans tout le document ; chaque résultat mène à sa page. */
@Composable
private fun SearchPanel(
    doc: PdfDoc,
    initial: String,
    onPick: (page: Int, query: String, pages: List<Int>) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf(initial) }
    // Un mot déjà surligné : ses résultats réapparaissent tout de suite
    var running by remember { mutableStateOf<String?>(initial.ifBlank { null }) }
    var searching by remember { mutableStateOf(false) }
    var scanned by remember { mutableIntStateOf(0) }
    var sawText by remember { mutableStateOf(false) }
    val hits = remember { mutableStateListOf<SearchHit>() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(running) {
        hits.clear()
        scanned = 0
        sawText = false
        val wanted = running?.trim()?.lowercase().orEmpty()
        if (wanted.isEmpty()) {
            searching = false
            return@LaunchedEffect
        }
        searching = true
        try {
            for (page in 0 until doc.pageCount) {
                if (hits.size >= MAX_HITS) break
                val text = doc.pageText(page).replace(Regex("\\s+"), " ")
                if (text.isNotBlank()) sawText = true
                val lower = text.lowercase()
                var from = 0
                var perPage = 0
                while (perPage < 3) {
                    val at = lower.indexOf(wanted, from)
                    if (at < 0) break
                    val start = max(0, at - 30)
                    val end = min(text.length, at + wanted.length + 60)
                    val snippet = (if (start > 0) "…" else "") + text.substring(start, end).trim() +
                        (if (end < text.length) "…" else "")
                    hits.add(SearchHit(page, snippet))
                    from = at + wanted.length
                    perPage++
                }
                scanned = page + 1
            }
        } finally {
            searching = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer la recherche", tint = Ink)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Rechercher dans le document") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { running = query }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
            )
        }
        val status = when {
            running.isNullOrBlank() -> "Saisissez un mot puis validez."
            searching -> "Recherche… page $scanned sur ${doc.pageCount} · ${hits.size} résultat(s)"
            hits.isEmpty() && !sawText -> "Ce document ne contient pas de texte (PDF scanné)."
            hits.isEmpty() -> "Aucun résultat."
            else -> "${hits.size} résultat(s)"
        }
        Text(
            status,
            color = InkSoft,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(hits) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPick(hit.page, running?.trim().orEmpty(), hits.map { it.page }.distinct())
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Page ${hit.page + 1}", color = Brick, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(hit.snippet, color = Ink, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = Sand)
            }
        }
    }
}

/** Bandeau du mot surligné : pages précédente et suivante qui le contiennent, et bouton pour l'effacer. */
@Composable
private fun HighlightPill(
    word: String,
    index: Int,
    hitPages: List<Int>,
    look: ReadingLook,
    onGo: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier
) {
    val previous = hitPages.lastOrNull { it < index }
    val next = hitPages.firstOrNull { it > index }
    val position = hitPages.indexOf(index)
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(look.bar)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { previous?.let(onGo) }, enabled = previous != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Page trouvée précédente",
                tint = if (previous != null) look.accent else look.line
            )
        }
        Text(
            if (position >= 0) "« $word » · ${position + 1}/${hitPages.size}" else "« $word »",
            color = look.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = { next?.let(onGo) }, enabled = next != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Page trouvée suivante",
                tint = if (next != null) look.accent else look.line
            )
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = "Effacer le surlignage", tint = look.textSoft)
        }
    }
}

private const val MAX_HITS = 300
