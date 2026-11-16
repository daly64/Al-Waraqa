package tn.alwaraqa.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import tn.alwaraqa.app.data.Library
import tn.alwaraqa.app.ui.AlWaraqaApp
import tn.alwaraqa.app.ui.reader.PageSound
import tn.alwaraqa.app.ui.reader.ReaderKeys
import tn.alwaraqa.app.ui.theme.AlWaraqaTheme

class MainActivity : ComponentActivity() {
    /** PDF envoyé par une autre application, en attente d'ouverture. */
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // L'app s'étend aussi autour de l'encoche
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        enterFullScreen()
        PageSound.init(this)
        incoming = pdfFrom(intent)
        val library = Library(this)
        setContent {
            AlWaraqaTheme {
                AlWaraqaApp(
                    library = library,
                    incoming = incoming,
                    onIncomingHandled = { incoming = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pdfFrom(intent)?.let { incoming = it }
    }

    /** Pendant la lecture, les touches de volume tournent les pages au lieu de changer le son. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volume = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (volume && ReaderKeys.active) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                ReaderKeys.press(forward = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Plein écran automatique : rétabli à chaque retour dans l'app
        if (hasFocus) enterFullScreen()
    }

    /** Masque les barres du téléphone ; un glissement depuis le bord les fait réapparaître un instant. */
    private fun enterFullScreen() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun pdfFrom(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
}
