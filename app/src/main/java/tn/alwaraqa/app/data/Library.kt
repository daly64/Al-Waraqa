package tn.alwaraqa.app.data

import android.content.Context
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

/**
 * Données locales de l'app (préférences Android) : écrans de bienvenue déjà vus
 * et documents récents avec la dernière page lue.
 */
class Library(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("alwaraqa", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

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
        write(all.sortedByDescending { it.openedAt }.take(MAX_RECENTS))
    }

    fun remove(uri: String) {
        write(recents().filter { it.uri != uri })
    }

    private fun write(docs: List<RecentDoc>) {
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

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_RECENTS = "recents"
        const val MAX_RECENTS = 50
    }
}
