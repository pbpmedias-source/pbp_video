# Mon Montage (PBP Médias) — contexte pour Claude Code

Application Android native de montage vidéo « façon journal télévisé », pensée pour PBP Médias :
on monte des photos et des rushs du téléphone, on ajoute génériques, légendes, musique et transitions,
puis on exporte un **MP4 H.264/AAC 1080p conforme YouTube, entièrement sur le téléphone** (aucun serveur),
et on peut **publier directement sur YouTube**.

- Dépôt : `pbpmedias-source/pbp_video` (GitHub). Package / namespace : `com.monmontage.app`.
- Langue : **toute l'interface et les commentaires sont en français.** Garder ce choix.
- L'utilisateur travaille surtout **depuis l'interface web de GitHub** (upload de fichiers, édition en ligne)
  et récupère l'APK compilé par **GitHub Actions**. Il n'a pas forcément Android Studio.
  ⚠️ Un upload web n'efface jamais de fichier : tout fichier supprimé du projet doit être supprimé à la main
  dans le dépôt (cela a déjà cassé deux compilations : `DefaultMusicGenerator.kt`, `LogoScreen.kt`).

## Pile technique (versions figées volontairement)

- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9 (wrapper inclus), JDK 17, compileSdk/targetSdk 35, **minSdk 29**.
- Jetpack Compose (BOM 2024.12.01), Material 3, activity-compose 1.9.3, lifecycle 2.8.7.
- **Media3 1.4.1** (transformer, effect, exoplayer, ui) — *ne pas monter de version sans vérifier* :
  l'API d'overlay (`OverlaySettings.Builder().setAlphaScale`), `EditedMediaItem.Builder.setDurationUs/setFrameRate`
  et `Composition.Builder.experimentalSetForceAudioTrack` ont été vérifiées dans les sources 1.4.1.
- WorkManager 2.9.1, play-services-auth 21.4.0, kotlinx-coroutines (android + play-services) 1.9.0.
- Lint : `UnsafeOptInUsageError` désactivé (API Media3 `@UnstableApi`), `checkReleaseBuilds = false`.
- Aucune permission de stockage : Photo Picker, `OpenDocument` pour la musique, écriture MediaStore (Films/MonMontage).
  Permissions : INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE(_DATA_SYNC)
  (service WorkManager déclaré `dataSync` dans le manifeste).

## Arborescence utile

```
app/src/main/
  assets/fonts/            Inter, Oswald, Anton, Fraunces (polices des génériques, OFL)
  res/font/                inter_variable, space_grotesk_variable (polices de l'interface)
  res/drawable-nodpi/      logo_pbp_medias.png (logo par défaut, détouré)
  java/com/monmontage/app/
    MainActivity.kt        navigation (sealed interface Screen, pas de navigation-compose)
    model/Models.kt        Project, Clip, Segment, Caption, TitleCard, EndCard, CardStyle,
                           BrandKit/Branding, MusicSettings/MusicTrack, TransitionSettings, SegmentOps
    data/ProjectRepository.kt   JSON (org.json) : un fichier par projet + branding.json, écriture atomique
    render/                moteur de rendu (voir ci-dessous)
    youtube/               YouTubeAuth, YouTubeUploader, UploadWorker, Description
    ui/MontageViewModel.kt ViewModel unique (StateFlow), autosave débouncé 400 ms
    ui/screens/            Projects, Library, Settings(+KitEditor), Home(montage), ClipEdit(+Trim, Carton),
                           CardEditor (génériques), Music, Transitions, RenderScreens (aperçu/export), YouTubeScreens
    ui/theme/              Theme (thème sombre fixe), AppColors, AppIcons (icônes vectorielles maison)
.github/workflows/build-apk.yml   compilation APK debug, signature par secrets, erreurs remontées en annotations
```

## Rendu (cœur du projet) — `render/`

1. `TimelinePlanner.plan(project)` aplatit le projet : générique début → médias (un segment vidéo = un élément,
   photos, **cartons**) → générique fin ; calcule les plages « interview » et ramène les légendes en temps local.
2. `ProjectPreparer.prepare()` (IO) : vérifie les médias manquants, rend en PNG les génériques, cartons et le calque
   du logo (`Painter`), redimensionne les photos (EXIF, sous-échantillonnage), prépare la musique, contrôle l'espace disque.
3. `CompositionFactory.build()` : **séquence 1** vidéo+son (Presentation 1920×1080 SCALE_TO_FIT, puis zoom,
   légendes, logo, fondu) ; **séquence 2** audio seule = musique répétée avec `GainEnvelopeProcessor`
   piloté par `MusicEnvelope` (fondus, coupure/sourdine douce pendant les interviews). HDR → SDR.
4. `VideoRenderer` : Transformer H.264/AAC (12 Mb/s export, 480p pour l'aperçu), progression, annulation.
   L'**aperçu** est un rendu 480p par le même pipeline (fidèle à l'export).

Autres pièces : `Painter` (génériques 3 styles, cartons, légendes, logo ; mesures maquette 960×540 × s),
`KitColors` (couleurs de charte + teintes/contrastes dérivés), `CardFonts`, `CaptionOverlay`/`WatermarkOverlay`
(BitmapOverlay, horloge locale = 1re image reçue), `TransitionEffects` (RgbMatrix fondu noir/blanc,
MatrixTransformation zoom, gain audio des bords), `MusicLibrary` (5 musiques **synthétisées en Kotlin**,
boucles WAV, RMS −16 dBFS : Douce, Énergique, Cinématique, Lo-fi, Rock), `Palette` (couleurs dominantes du logo),
`GallerySaver`, `MediaUtils`.

## Fonctionnalités en place

- **Projets** : liste des récents + **Bibliothèque** (recherche, filtres, tri, grille/liste), renommer, dupliquer,
  supprimer, autosave, badge « média introuvable », URI persistantes.
- **Montage** : photos/vidéos, ordre ↑↓, type Illustration (son coupé) / Interview (son gardé), découpe en segments,
  **légendes bandeau JT** par média, **logo en miniature par média** (aperçu simulé avec la légende sur la page du média),
  **cartons** intertitre/citation.
- **Génériques** propres à chaque projet, **3 styles JT** (Édition du soir / Flash info / Magazine),
  photo d'arrière-plan par projet.
- **Réglages généraux** : plusieurs **chartes** (nom, logo, accent/principale/foncée), une par défaut,
  choix de la charte par projet ; PBP Médias par défaut (#FF8E0A, #72037D, #2A0630).
- **Musique** (5 styles ou import) avec courbe de volume réelle ; **transitions** Aucune/Fondu noir/Flash blanc/Zoom,
  0,3/0,6/1 s, appliquées à tout le projet (pas de vrai fondu enchaîné : volontaire, fiabilité).
- **YouTube** : connexion AuthorizationClient (scopes youtube.upload + userinfo.email, aucun jeton stocké),
  envoi reprenable par blocs de 8 Mo via WorkManager (notification, Wi-Fi seul en option, reprise),
  **description pré-remplie + chapitres automatiques** (légendes et intertitres ; règles YouTube : 0:00, ≥3, ≥10 s),
  `defaultLanguage/defaultAudioLanguage = fr`, **publication programmée** (`publishAt`, privée jusque-là).

## Compilation, signature, YouTube

- CI : `.github/workflows/build-apk.yml` (`on: [push, workflow_dispatch]`, actions v5/v6 Node 24).
  En cas d'échec, les lignes `e:` et « What went wrong » sont publiées en **une annotation** + résumé d'exécution,
  et `build.log` en artefact `journal-compilation`. APK : artefact `MonMontage-debug-apk`.
- Signature fixe **obligatoire pour YouTube** (empreinte SHA-1 stable) : secrets `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` ; en local `keystore.properties` (non versionné).
  Le debug est signé avec cette clé si elle existe. Changer de clé ⇒ désinstaller l'app (perte des projets locaux).
- Google Cloud : YouTube Data API v3 activée, écran de consentement (Externe, testeurs), client **Android**
  (package `com.monmontage.app` + SHA-1). Aucun identifiant dans le code. Détails dans `README.md`.

## Points d'attention / dette

- Le code n'a été compilé que via la CI (pas d'Android Studio côté auteur). Rien n'a été testé sur appareil
  par l'assistant : **les rendus Canvas des génériques/cartons et l'envoi YouTube restent à valider sur téléphone.**
- Corrections de compilation déjà faites : import `rememberSaveable` manquant (LibraryScreen), fichiers obsolètes
  restés dans le dépôt. Garder les imports Compose explicites ; éviter deux imports `items` (lazy et lazy.grid).
- `ProjectRepository` lit les anciens formats (branding v1 = logo seul, projets sans `titleCard` ⇒ titre = nom).
- Si un projet est ouvert pendant un envoi YouTube, l'info `youtube` est réinjectée via `uploadState()` pour éviter
  qu'un autosave l'écrase.
- La maquette de référence (Claude Design) ne montre pas encore : écran Réglages/charte, cartons, écran Publier enrichi.

## Pistes suivantes (validées mais non faites)

6. Léger zoom lent (« Ken Burns ») sur les photos, option par projet.
7. Son d'ambiance des illustrations à ~15 % (option par média) au lieu de coupé.
8. Miniature YouTube générée depuis le générique (thumbnails.set ; chaîne vérifiée requise).
9. Export vertical 9:16 (Shorts/Instagram) avec génériques/légendes recomposés.
10. Sauvegarde/restauration des projets et chartes (fichier).
- Sous-titres : aujourd'hui confiés à YouTube (auto-sous-titres FR, relecture dans YouTube Studio).
  Piste ultérieure : sous-titres incrustés avec transcription sur l'appareil + écran de correction.

## Conventions de travail

- Expliquer les changements en français, de façon concrète pour un utilisateur non développeur.
- Lister **tous** les fichiers ajoutés/modifiés/**supprimés** à chaque livraison (upload web).
- Vérifier les API Media3 / Play Services dans les sources de la version figée avant de les utiliser.
- Conserver la charte PBP Médias et le thème sombre de l'interface ; textes accessibles (contrastes calculés).
