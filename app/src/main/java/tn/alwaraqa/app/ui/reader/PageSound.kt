package tn.alwaraqa.app.ui.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import tn.alwaraqa.app.R
import kotlin.random.Random

/**
 * Le bruit d'une page qui se tourne : trois vrais enregistrements de papier
 * (« RPG Audio » de Kenney, licence CC0), joués au hasard avec une légère
 * variation de vitesse pour que deux pages ne sonnent jamais pareil.
 */
object PageSound {
    private var pool: SoundPool? = null
    private val sounds = ArrayList<Int>()
    private val ready = HashSet<Int>()
    private var last = -1

    fun init(context: Context) {
        if (pool != null) return
        try {
            val p = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(ready) { ready.add(id) } }
            val app = context.applicationContext
            listOf(R.raw.page_flip_1, R.raw.page_flip_2, R.raw.page_flip_3).forEach { res ->
                sounds.add(p.load(app, res, 1))
            }
            pool = p
        } catch (_: Throwable) {
            // Le son est un agrément : sans lui, les pages se tournent en silence
        }
    }

    fun play() {
        val p = pool ?: return
        val loaded = synchronized(ready) { sounds.filter { it in ready } }
        if (loaded.isEmpty()) return
        // Jamais deux fois de suite le même enregistrement
        var pick = loaded[Random.nextInt(loaded.size)]
        if (pick == last && loaded.size > 1) pick = loaded[(loaded.indexOf(pick) + 1) % loaded.size]
        last = pick
        val rate = 0.94f + Random.nextFloat() * 0.14f
        try {
            p.play(pick, 0.7f, 0.7f, 1, 0, rate)
        } catch (_: Throwable) {
        }
    }
}
