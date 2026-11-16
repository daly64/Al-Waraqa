package tn.alwaraqa.app.ui.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le bruit d'une page qui se tourne. Il est fabriqué par l'app (un souffle de papier
 * synthétique) : aucun fichier audio à embarquer.
 */
object PageSound {
    private const val SAMPLE_RATE = 22050
    private var track: AudioTrack? = null

    fun play() {
        try {
            val t = track ?: build().also { track = it }
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Throwable) {
            // Le son est un agrément : en cas de souci, on tourne la page en silence
        }
    }

    fun release() {
        try {
            track?.release()
        } catch (_: Throwable) {
        }
        track = null
    }

    private fun build(): AudioTrack {
        val samples = makeSwish()
        val t = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            samples.size * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        t.write(samples, 0, samples.size)
        return t
    }

    /** Un souffle bref : bruit filtré dont le timbre s'éclaircit puis retombe, comme du papier qui glisse. */
    private fun makeSwish(): ShortArray {
        val count = (SAMPLE_RATE * 0.26f).toInt()
        val out = ShortArray(count)
        val random = Random(7)
        var low = 0f
        var previous = 0f
        for (i in 0 until count) {
            val p = i / count.toFloat()
            // Enveloppe : montée rapide, retombée douce, petit claquement final
            val body = sin(PI.toFloat() * min(1f, p * 1.15f)).coerceAtLeast(0f)
            val flick = if (p > 0.78f) sin(PI.toFloat() * (p - 0.78f) / 0.22f) * 0.5f else 0f
            val envelope = body * body * 0.75f + flick
            // Filtre passe-bas dont l'ouverture varie pendant le geste
            val open = 0.10f + 0.45f * sin(PI.toFloat() * p)
            val noise = random.nextFloat() * 2f - 1f
            low += open * (noise - low)
            // On retire les graves pour garder un son léger
            val crisp = low - previous * 0.85f
            previous = low
            out[i] = (crisp * envelope * 9000f).toInt().coerceIn(-32000, 32000).toShort()
        }
        return out
    }
}
