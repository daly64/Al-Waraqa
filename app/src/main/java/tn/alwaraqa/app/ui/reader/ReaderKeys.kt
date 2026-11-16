package tn.alwaraqa.app.ui.reader

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Touches de volume : pendant la lecture, volume bas tourne la page suivante
 * et volume haut revient en arrière.
 */
object ReaderKeys {
    @Volatile
    var active: Boolean = false

    private val flow = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)

    /** Vrai : page suivante. Faux : page précédente. */
    val presses: SharedFlow<Boolean> = flow

    fun press(forward: Boolean) {
        flow.tryEmit(forward)
    }
}
