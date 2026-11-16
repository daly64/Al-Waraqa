package tn.alwaraqa.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Un PDF déjà ouvert : où il se trouve et où la lecture s'est arrêtée. */
data class RecentDoc(
    val uri: String,
    val name: String,
    val pageCount: Int,
    val lastPage: Int,
    val openedAt: Long
)

/** Une page marquée par l'utilisateur. */
data class Bookmark(
    val uri: String,
    val name: String,
    val page: Int,
    val addedAt: Long
)

enum class ReadingTheme(val label: String) {
    Clair("Clair"),
    Sepia("Sépia"),
    Nuit("Nuit")
}

/** Réglages de l'app. Chaque changement est enregistré aussitôt et met l'écran à jour. */
class Settings(private val prefs: SharedPreferences) {
    private var themeState by mutableStateOf(
        ReadingTheme.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ReadingTheme.Clair
    )
    private var bookModeState by mutableStateOf(prefs.getBoolean("bookMode", true))
    private var keepScreenOnState by mutableStateOf(prefs.getBoolean("keepScreenOn", true))
    private var soundState by mutableStateOf(prefs.getBoolean("sound", true))
    private var hapticState by mutableStateOf(prefs.getBoolean("haptic", true))
    private var uniformCropState by mutableStateOf(prefs.getBoolean("uniformCrop", true))
    private var volumeKeysState by mutableStateOf(prefs.getBoolean("volumeKeys", true))

    /** Les touches de volume tournent les pages pendant la lecture. */
    var volumeKeys: Boolean
        get() = volumeKeysState
        set(value) {
            volumeKeysState = value
            prefs.edit().putBoolean("volumeKeys", value).apply()
        }

    var theme: ReadingTheme
        get() = themeState
        set(value) {
            themeState = value
            prefs.edit().putString("theme", value.name).apply()
        }

    /** Vrai : mode Livre (pages tournées). Faux : défilement vertical. */
    var bookMode: Boolean
        get() = bookModeState
        set(value) {
            bookModeState = value
            prefs.edit().putBoolean("bookMode", value).apply()
        }

    var keepScreenOn: Boolean
        get() = keepScreenOnState
        set(value) {
            keepScreenOnState = value
            prefs.edit().putBoolean("keepScreenOn", value).apply()
        }

    var sound: Boolean
        get() = soundState
        set(value) {
            soundState = value
            prefs.edit().putBoolean("sound", value).apply()
        }

    var haptic: Boolean
        get() = hapticState
        set(value) {
            hapticState = value
            prefs.edit().putBoolean("haptic", value).apply()
        }

    /** Même recadrage sur toutes les pages d'un document, pour une taille de texte constante. */
    var uniformCrop: Boolean
        get() = uniformCropState
        set(value) {
            uniformCropState = value
            prefs.edit().putBoolean("uniformCrop", value).apply()
        }
}

/**
 * Données locales de l'app (préférences Android) : écrans de bienvenue déjà vus,
 * documents récents avec la dernière page lue, marque-pages et réglages.
 */
class Library(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("alwaraqa", Context.MODE_PRIVATE)

    val settings = Settings(prefs)

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    // ---- Documents récents ----

    fun recents(): List<RecentDoc> {
        val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                RecentDoc(
                    uri = o.getString("uri"),
                    name = o.optString("name", "Document.pdf"),
                    pageCount = o.optInt("pageCount", 0),
                    lastPage = o.optInt("lastPage", 0),
                    openedAt = o.optLong("openedAt", 0L)
                )
            }.sortedByDescending { it.openedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun find(uri: String): RecentDoc? = recents().firstOrNull { it.uri == uri }

    fun save(doc: RecentDoc) {
        val all = recents().filter { it.uri != doc.uri } + doc
        writeRecents(all.sortedByDescending { it.openedAt }.take(MAX_RECENTS))
    }

    fun remove(uri: String) {
        writeRecents(recents().filter { it.uri != uri })
    }

    /** Le fichier a été renommé : récents et marque-pages suivent. */
    fun rename(oldUri: String, newUri: String, newName: String) {
        writeRecents(recents().map { if (it.uri == oldUri) it.copy(uri = newUri, name = newName) else it })
        writeBookmarks(bookmarks().map { if (it.uri == oldUri) it.copy(uri = newUri, name = newName) else it })
    }

    /** Le fichier a été supprimé du téléphone : on l'oublie partout. */
    fun forget(uri: String) {
        remove(uri)
        writeBookmarks(bookmarks().filterNot { it.uri == uri })
    }

    private fun writeRecents(docs: List<RecentDoc>) {
        val array = JSONArray()
        docs.forEach { d ->
            array.put(
                JSONObject()
                    .put("uri", d.uri)
                    .put("name", d.name)
                    .put("pageCount", d.pageCount)
                    .put("lastPage", d.lastPage)
                    .put("openedAt", d.openedAt)
            )
        }
        prefs.edit().putString(KEY_RECENTS, array.toString()).apply()
    }

    // ---- Marque-pages ----

    fun bookmarks(): List<Bookmark> {
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Bookmark(
                    uri = o.getString("uri"),
                    name = o.optString("name", "Document.pdf"),
                    page = o.optInt("page", 0),
                    addedAt = o.optLong("addedAt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isBookmarked(uri: String, page: Int): Boolean =
        bookmarks().any { it.uri == uri && it.page == page }

    /** Ajoute ou retire le marque-page ; renvoie vrai si la page est maintenant marquée. */
    fun toggleBookmark(uri: String, name: String, page: Int): Boolean {
        val all = bookmarks()
        return if (all.any { it.uri == uri && it.page == page }) {
            writeBookmarks(all.filterNot { it.uri == uri && it.page == page })
            false
        } else {
            writeBookmarks(all + Bookmark(uri, name, page, System.currentTimeMillis()))
            true
        }
    }

    fun removeBookmark(uri: String, page: Int) {
        writeBookmarks(bookmarks().filterNot { it.uri == uri && it.page == page })
    }

    private fun writeBookmarks(list: List<Bookmark>) {
        val array = JSONArray()
        list.forEach { b ->
            array.put(
                JSONObject()
                    .put("uri", b.uri)
                    .put("name", b.name)
                    .put("page", b.page)
                    .put("addedAt", b.addedAt)
            )
        }
        prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_RECENTS = "recents"
        const val KEY_BOOKMARKS = "bookmarks"
        const val MAX_RECENTS = 50
    }
}
