<p align="center">
  <img src="docs/icone.svg" width="128" height="128" alt="Icône Al Waraqa : une feuille au coin plié portant un W">
</p>

<h1 align="center">Al Waraqa · الورقة</h1>

<p align="center">Lecteur PDF pour Android. Les pages se tournent au doigt, comme dans un vrai livre.</p>

## Identité visuelle

**Nom.** Al Waraqa (الورقة, « la feuille »). Sur l'écran de démarrage, le nom s'affiche en arabe.

**Icône.** Une feuille au coin plié portant un W, sur fond brique.

**Couleurs**

![Palette Al Waraqa](docs/palette.svg)

| Couleur | Code | Usage |
| --- | --- | --- |
| Brique | `#A63A24` | Couleur principale : boutons, icône, splash |
| Terre cuite | `#7A2817` | Brique foncée : ombres du logo, états appuyés |
| Sable | `#E4C9A0` | Coin plié, séparateurs, bordures |
| Papier | `#FFFBF3` | Cartes, barres, feuille du logo |
| Crème | `#F6EEDF` | Fond des écrans |
| Encre | `#2A1D17` | Texte |
| Encre douce | `#6B5547` | Texte secondaire |

**Typographie**

| Rôle | Police |
| --- | --- |
| Titres | Bricolage Grotesque, 700–800 |
| Texte | Police système (Roboto) |
| Nom en arabe | Aref Ruqaa Bold |

**Principes d'interface**

- Pas de bouton flottant : les actions sont dans la mise en page.
- Plusieurs écrans de bienvenue au premier lancement, puis un splash simple.
- Plein écran automatique : les barres du téléphone sont masquées.

## L'application

- 100 % locale, écrite en Kotlin avec Jetpack Compose.
- Rendu des PDF par PDFium, embarqué dans l'app (bibliothèque `io.legere:pdfiumandroid`).
- Mode Livre : la page suit le doigt et se replie comme du papier, avec son et vibration.
- Mode Défilement : les pages se suivent de haut en bas.
- Marges blanches rognées automatiquement : le texte occupe tout l'écran, à taille homogène.
- Zoom à deux doigts ou par double appui, page redessinée finement pour rester nette.
- Thèmes de lecture Clair, Sépia et Nuit.
- Marque-pages, sommaire, « aller à la page », recherche dans le texte.
- Recherche d'un PDF par son nom, reprise à la dernière page lue.
- PDF protégés par mot de passe, partage et impression.

### Structure

```
app/src/main/java/tn/alwaraqa/app/
├── MainActivity.kt          plein écran, PDF reçu d'une autre app
├── data/Library.kt          récents, marque-pages, réglages
├── pdf/PdfDoc.kt            ouverture, rendu, sommaire, texte
├── pdf/Sharing.kt           partage et impression
└── ui/
    ├── AlWaraqaApp.kt       enchaînement des écrans et onglets
    ├── components/          logo et icônes dessinés
    ├── reader/BookPager.kt  page tournée, zoom
    ├── reader/ScrollPager.kt défilement vertical
    ├── reader/ReadingLook.kt thèmes de lecture
    ├── reader/PageSound.kt  son de page tournée
    ├── screens/             bienvenue, splash, documents, marque-pages, réglages, lecteur
    └── theme/Theme.kt       couleurs et polices
```

## Compilation et mises à jour

Chaque envoi sur `main` compile l'APK avec GitHub Actions. L'APK est publié dans les versions du dépôt et sur la branche `apk`.

Les APK sont signés avec une clé de test fixe (`app/alwaraqa-test.keystore`). Une nouvelle version s'installe donc par-dessus la précédente, sans désinstaller ni perdre les données. Pour cela, `versionCode` augmente à chaque version et `applicationId` ne change jamais.

## Licences

Bricolage Grotesque et Aref Ruqaa sont distribuées sous licence SIL Open Font License (voir `licenses/`).

PDFium et pdfiumandroid sont distribués sous licences BSD et Apache 2.0.
