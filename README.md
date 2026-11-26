# Mon Montage — monteur vidéo Android pour YouTube

Application Android native (Kotlin, Jetpack Compose, Material 3) qui monte une vidéo à partir
de la galerie et l'exporte **sur le téléphone, sans serveur**, en **MP4 H.264 + AAC 1080p**, prêt pour YouTube.

## Fonctionnalités

| # | Fonction | Où |
|---|----------|----|
| 1 | Sélection photos/vidéos (sélecteur système), ordre (↑/↓), type Illustration / Interview | Écran principal |
| 2 | Découpe : scinder, ajuster début/fin, supprimer des portions, « Lire la découpe » | Toucher un clip vidéo |
| 3 | Générique de début (titre, sous-titre, auteur, date) + logo, aperçu en direct | « Générique de début » |
| 4 | Générique de fin (texte principal, secondaire, crédits) + logo | « Générique de fin » |
| 5 | Musique par défaut embarquée, import d'une autre musique, coupure/sourdine douce pendant les interviews | « Musique de fond » |
| 6 | Légendes « bandeau JT » (nom + fonction) placées à n'importe quel moment d'un clip | Toucher un clip |
| 7 | Aperçu du montage complet | Bouton « Aperçu » |
| 8 | Export MP4 en un bouton, progression visible, enregistrement dans Films/MonMontage, partage | Bouton « Exporter » |

## Design

L'interface suit la maquette graphique validée : thème sombre d'éditeur vidéo (fond `#0E1116`, cartes `#181C24`),
accent bleu `#6F9BFF`, titres en **Space Grotesk**, texte en **Inter** (polices embarquées dans `res/font`,
licence SIL OFL dans `licenses/`). Étiquettes de couleur par type (ambre = photo, bleu = illustration,
orange = interview), barre de découpe, anneau de progression d'export, et sur l'écran Musique une courbe du volume
calculée à partir du vrai montage. Couleurs et polices sont centralisées dans `ui/theme/Theme.kt`.

## Choix techniques

* **Export : Media3 Transformer 1.4.1** — bibliothèque officielle Google (successeur d'ExoPlayer), qui utilise
  les encodeurs matériels du téléphone, sait découper, enchaîner images et vidéos, mixer plusieurs pistes audio,
  appliquer des effets GPU et produire du H.264/AAC ; c'est l'option la plus fiable et maintenue (FFmpeg est plus
  lourd, plus lent en logiciel et pose des questions de licence).
* **Composition à deux séquences** : (1) vidéo + son : générique → photos/segments → générique de fin ;
  (2) audio seul : la musique, répétée pour couvrir toute la durée. Un `AudioProcessor` maison applique
  l'enveloppe de volume (fondu d'entrée 1 s, fondus de 0,8 s autour des interviews, fondu de sortie 2 s,
  courbes « smoothstep »). Il connaît le temps exact en comptant les échantillons, sans dépendre des horodatages.
* **Légendes** : calque GPU (`OverlayEffect` + `BitmapOverlay`) avec fondu de 300 ms.
* **Aperçu** : rendu 480p par **le même pipeline** que l'export, puis lecture avec ExoPlayer. Ce que vous voyez
  est donc exactement ce qui sera exporté (musique, coupures, légendes compris). La découpe d'un clip se
  vérifie instantanément avec « Lire la découpe ».
* **Aucune permission** : sélecteur de photos système, sélecteur de documents pour la musique,
  écriture dans MediaStore (autorisée sans permission depuis Android 10).

## Hypothèses retenues (modifiables dans le code)

* Android 10 minimum (`minSdk 29`), cible Android 15 (`targetSdk 35`).
* Sortie **1920×1080, 16:9, H.264 12 Mb/s, AAC** ; les médias verticaux sont centrés avec bandes noires.
  Images fixes à 30 i/s. Vidéos HDR converties en SDR (H.264 ne transporte pas le HDR proprement).
* Photo : 4 s par défaut (réglable 2–10 s). Génériques : 4 s (réglables 2–8 s), fond dégradé bleu nuit.
* Vidéo importée : proposée en « Illustration » si ≤ 30 s, sinon « Interview » (modifiable d'un geste).
* Son d'origine : **coupé** sur les illustrations (la musique les accompagne), **conservé** sur les interviews.
* Musique pendant les interviews : coupée par défaut, ou atténuée à 12 % (interrupteur).
* **Musique par défaut** : composée par l'application au premier lancement (boucle de 24 s, Do–La m–Fa–Sol,
  nappe + basse + arpège), donc **sans aucun droit d'auteur** et sans fichier binaire dans le dépôt.
  Pour embarquer un autre morceau, voir `DefaultMusicGenerator.kt`.
* Un logo commun aux deux génériques. Légendes attachées à un clip (elles le suivent si on le déplace).
* Le projet vit en mémoire pendant la session (pas de sauvegarde de brouillon), pour rester simple.

## Robustesse mémoire sur les longues vidéos

* Rien n'est chargé entièrement en mémoire : Transformer lit, décode, traite et encode **en flux**, image par image,
  avec les codecs matériels ; l'audio est traité tampon par tampon.
* Les photos sont décodées **sous-échantillonnées** (une photo de 50 Mpx n'est jamais décodée en pleine taille),
  redimensionnées à la taille de sortie, orientation EXIF appliquée, puis écrites en cache une par une.
* Les génériques et bandeaux sont dessinés une fois ; les bitmaps de légende sont créés à la demande et libérés.
* Contrôle d'espace libre avant l'export ; écran maintenu allumé pendant le rendu ; `largeHeap` activé ;
  les erreurs (y compris mémoire insuffisante) sont affichées avec un bouton « Réessayer ».
* Limite connue : l'export tourne dans l'application ; si vous la quittez longtemps, Android peut l'interrompre.

## Structure

```
MonMontage/
├─ .github/workflows/build-apk.yml   (optionnel : APK compilé par GitHub)
├─ .gitignore
├─ build.gradle.kts / settings.gradle.kts / gradle.properties
├─ gradlew, gradlew.bat, gradle/wrapper/
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ res/ (icône, thème, chaînes)
      └─ java/com/monmontage/app/
         ├─ MainActivity.kt                 navigation entre écrans
         ├─ model/Models.kt                 projet, clips, segments, légendes, opérations de découpe
         ├─ render/
         │  ├─ TimelinePlanner.kt           projet → timeline à plat (source unique)
         │  ├─ CompositionFactory.kt        timeline → Composition Media3
         │  ├─ VideoRenderer.kt             Transformer H.264/AAC + progression + annulation
         │  ├─ MusicEnvelope.kt             courbe de volume (fondus, interviews)
         │  ├─ GainEnvelopeProcessor.kt     application du volume sur le PCM
         │  ├─ CaptionOverlay.kt            bandeau JT en calque GPU
         │  ├─ Painter.kt                   dessin des génériques et bandeaux
         │  ├─ ProjectPreparer.kt           préparation (photos, génériques, musique, espace disque)
         │  ├─ DefaultMusicGenerator.kt     musique par défaut libre de droits
         │  ├─ MediaUtils.kt / GallerySaver.kt
         └─ ui/  (ViewModel, thème et icônes, composants, écrans)
```

## 1. Mettre le projet sur GitHub

```bash
cd MonMontage
git init
git add .
git commit -m "Mon Montage : première version"
git branch -M main
git remote add origin https://github.com/<votre-compte>/MonMontage.git
git push -u origin main
```
Le `.gitignore` exclut `build/`, `.gradle/`, `local.properties`, les APK et **tous les fichiers de signature**
(`*.jks`, `*.keystore`, `keystore.properties`).

## 2. Cloner et compiler en local

Prérequis : **Android Studio** récent (Ladybug ou plus) — il fournit le JDK 17 et le SDK Android.

```bash
git clone https://github.com/<votre-compte>/MonMontage.git
```
* **Avec Android Studio** : *File › Open* sur le dossier, attendre la synchronisation Gradle,
  puis *Build › Build App Bundle(s) / APK(s) › Build APK(s)*.
* **En ligne de commande** (le SDK doit être indiqué dans `local.properties` : `sdk.dir=/chemin/vers/Android/Sdk`,
  Android Studio le crée automatiquement) :
  ```bash
  ./gradlew assembleDebug          # Windows : gradlew.bat assembleDebug
  ```
  APK produit : `app/build/outputs/apk/debug/app-debug.apk`

## 3. (Recommandé pour un usage durable) APK release signé

```bash
keytool -genkeypair -v -keystore monmontage.jks -alias monmontage -keyalg RSA -keysize 2048 -validity 10000
```
Créez à la racine un fichier `keystore.properties` (**jamais versionné**) :
```
storeFile=monmontage.jks
storePassword=VOTRE_MOT_DE_PASSE
keyAlias=monmontage
keyPassword=VOTRE_MOT_DE_PASSE
```
puis :
```bash
./gradlew assembleRelease
```
APK : `app/build/outputs/apk/release/app-release.apk`. Conservez précieusement le `.jks` : il faut la même clé
pour installer les mises à jour par-dessus. (Un APK debug convient parfaitement pour un usage personnel.)

## 4. Installer l'APK sur le téléphone

**Par transfert de fichier**
1. Copiez l'APK sur le téléphone (câble USB, Google Drive, e-mail…).
2. Ouvrez-le depuis l'application *Fichiers*.
3. Android demande d'autoriser l'installation depuis cette source : *Paramètres › Autoriser cette source*
   (menu « Installer des applis inconnues »), revenez, puis *Installer*.
4. Si Play Protect affiche un avertissement, choisissez *Installer quand même* (application non publiée sur le Play Store).

**Par câble avec ADB** (options développeur et débogage USB activés) :
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 5. (Optionnel) APK compilé par GitHub Actions

Le workflow `.github/workflows/build-apk.yml` compile l'APK debug à chaque `push` sur `main`
(ou à la demande via l'onglet *Actions › Build APK (debug) › Run workflow*). L'APK se télécharge
dans la section *Artifacts* de l'exécution (fichier zip contenant `app-debug.apk`).

## 6. Tester l'export (checklist)

1. Ajoutez 2 photos, 1 vidéo courte (Illustration) et 1 vidéo plus longue (Interview).
2. Sur l'interview : *Scinder ici* au milieu, supprimez un segment, *Lire la découpe*.
3. Ajoutez une légende sur l'interview (nom + fonction), apparition à 1 s.
4. Remplissez les génériques et choisissez un logo.
5. *Aperçu* : vérifiez l'ordre, les coupes, le bandeau, et que la musique **s'efface avant l'interview
   puis revient en douceur après**.
6. *Exporter* : la progression s'affiche ; à la fin, la vidéo est dans *Galerie › Films/MonMontage*.
7. *Partager* → YouTube, ou vérifiez le fichier sur ordinateur (`ffprobe montage.mp4` doit indiquer `h264` et `aac`).

En cas d'échec, le message affiché et les journaux (`adb logcat -s MonMontage`) indiquent la cause.
