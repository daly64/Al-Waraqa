package tn.alwaraqa.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tn.alwaraqa.app.data.Library
import tn.alwaraqa.app.ui.screens.HomeScreen
import tn.alwaraqa.app.ui.screens.OnboardingScreen
import tn.alwaraqa.app.ui.screens.ReaderScreen
import tn.alwaraqa.app.ui.screens.SplashScreen

private enum class Screen { Onboarding, Splash, Home, Reader }

/**
 * Enchaînement des écrans : bienvenue (au premier lancement), splash, puis accueil ou lecteur.
 * [incoming] est un PDF envoyé par une autre application.
 */
@Composable
fun AlWaraqaApp(library: Library, incoming: Uri?, onIncomingHandled: () -> Unit) {
    var screen by remember { mutableStateOf(if (library.onboarded) Screen.Splash else Screen.Onboarding) }
    var readerUri by remember { mutableStateOf<Uri?>(null) }
    var recents by remember { mutableStateOf(library.recents()) }

    // Un PDF arrive d'une autre application alors que l'app est déjà ouverte
    LaunchedEffect(incoming, screen) {
        if (incoming != null && (screen == Screen.Home || screen == Screen.Reader)) {
            readerUri = incoming
            screen = Screen.Reader
            onIncomingHandled()
        }
    }

    when (screen) {
        Screen.Onboarding -> OnboardingScreen(
            onDone = {
                library.onboarded = true
                screen = Screen.Splash
            }
        )

        Screen.Splash -> SplashScreen(onFinished = { screen = Screen.Home })

        Screen.Home -> HomeScreen(
            recents = recents,
            onOpen = { uri ->
                readerUri = uri
                screen = Screen.Reader
            }
        )

        Screen.Reader -> {
            val uri = readerUri
            if (uri == null) {
                screen = Screen.Home
            } else {
                ReaderScreen(
                    uri = uri,
                    library = library,
                    onClose = {
                        screen = Screen.Home
                        readerUri = null
                    }
                )
            }
        }
    }

    // De retour à l'accueil : la liste reflète la dernière lecture
    LaunchedEffect(screen) {
        if (screen == Screen.Home) recents = library.recents()
    }
}
