package tn.alwaraqa.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tn.alwaraqa.app.data.ReadingTheme
import tn.alwaraqa.app.data.Settings
import tn.alwaraqa.app.ui.components.Segmented
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Cream
import tn.alwaraqa.app.ui.theme.Heading
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Sand

@Composable
fun SettingsScreen(settings: Settings) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 24.dp)
    ) {
        Text(
            "Réglages",
            color = Ink,
            fontFamily = Heading,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp
        )
        Spacer(Modifier.height(24.dp))

        Label("Thème de lecture")
        Segmented(
            options = ReadingTheme.entries.toList(),
            selected = settings.theme,
            label = { it.label },
            onSelect = { settings.theme = it }
        )
        Spacer(Modifier.height(24.dp))

        Label("Mode de lecture")
        Segmented(
            options = listOf(true, false),
            selected = settings.bookMode,
            label = { if (it) "Livre" else "Défilement" },
            onSelect = { settings.bookMode = it }
        )
        Note(
            if (settings.bookMode) "Livre : les pages se tournent au doigt, comme du vrai papier."
            else "Défilement : les pages se suivent de haut en bas, à la largeur de l'écran."
        )
        Spacer(Modifier.height(16.dp))

        Toggle(
            title = "Taille de texte homogène",
            note = "Même recadrage sur toutes les pages d'un document.",
            checked = settings.uniformCrop,
            onChange = { settings.uniformCrop = it }
        )
        Toggle(
            title = "Son de page tournée",
            note = null,
            checked = settings.sound,
            onChange = { settings.sound = it }
        )
        Toggle(
            title = "Vibration de page tournée",
            note = null,
            checked = settings.haptic,
            onChange = { settings.haptic = it }
        )
        Toggle(
            title = "Garder l'écran allumé",
            note = "Pendant la lecture.",
            checked = settings.keepScreenOn,
            onChange = { settings.keepScreenOn = it }
        )
        Spacer(Modifier.height(24.dp))

        Label("À propos")
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Al Waraqa", color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("Version $version", color = InkSoft, fontSize = 15.sp)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        color = InkSoft,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun Note(text: String) {
    Text(text, color = InkSoft, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Toggle(title: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ink, fontSize = 16.sp)
            if (note != null) Text(note, color = InkSoft, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Brick,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD9C8AE),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
    HorizontalDivider(color = Sand)
}
