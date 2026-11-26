# Mon Montage — monteur vidéo Android pour YouTube

Application Android native (Kotlin, Jetpack Compose, Material 3) qui monte des vidéos à partir de la galerie,
les exporte **sur le téléphone, sans serveur**, en **MP4 H.264 + AAC 1080p**, et peut les **publier directement
sur une chaîne YouTube**.

## Fonctionnalités

| Fonction | Où |
|----------|----|
| **Mes projets** : projets récents, nouveau projet, renommage, duplication, suppression (avec confirmation), enregistrement automatique | Écran d'ouverture |
| **Bibliothèque** : tous les projets, recherche, filtres (brouillons, exportés, YouTube), tri, grille ou liste | « Tout voir » |
| Médias de la galerie (sélecteur système), ordre, type Illustration / Interview | Montage d'un projet |
| Découpe : scinder, ajuster début/fin, supprimer des portions, « Lire la découpe » | Toucher un clip vidéo |
| Légendes « bandeau JT » (nom + fonction) placées à n'importe quel moment d'un clip | Toucher un clip |
| **Génériques façon JT** propres à chaque projet, **3 styles** (Édition du soir, Flash info, Magazine) aux couleurs PBP Médias, photo d'arrière-plan au choix | Générique de début / fin |
| **Logo PBP Médias par défaut**, commun à tous les projets (remplaçable) ; **logo en miniature** en bas à droite des photos et rushs (option par projet) | Mes projets › Logo ; Générique |
| **5 musiques embarquées** (Douce, Énergique, Cinématique, Lo-fi, Rock) ou import ; coupure/sourdine douce pendant les interviews | Musique |
| **3 styles de transition** (Fondu au noir, Flash blanc, Zoom doux) + « Aucune », appliqués à tout le projet, durée 0,3 / 0,6 / 1 s | Transitions |
| Aperçu du montage (rendu 480p par le même moteur que l'export) | Aperçu |
| Export MP4 en un bouton, progression, enregistrement dans Films/MonMontage | Exporter |
| **Publication YouTube** : titre, description, visibilité, public visé, Wi-Fi seulement, envoi en arrière-plan reprenable | Après l'export |

## Charte graphique

Couleurs relevées sur le logo PBP Médias : orange `#FF8E0A`, violet `#72037D`, violet très foncé `#2A0630`
(panneaux), blanc. Logo détouré fourni dans `res/drawable-nodpi/logo_pbp_medias.png`.
Polices des génériques (licence SIL OFL, dans `assets/fonts`) : Oswald (Édition du soir), Anton (Flash info),
Fraunces (Magazine), Inter (textes courants). Les bandeaux de légende reprennent aussi la charte.

## Choix techniques

* **Export : Media3 Transformer 1.4.1**, la bibliothèque officielle de Google : encodeurs matériels, découpe,
  enchaînement images/vidéos, mixage de pistes audio, effets GPU, sortie H.264/AAC.
* **Transitions** : calculées sur chaque élément seul (matrice de couleur pour les fondus, transformation pour le
  zoom), sans superposer deux vidéos. Le son des interviews suit le fondu ; micro-fondus de 20 ms partout contre les clics.
  Un vrai « fondu enchaîné » n'est pas proposé : il exigerait de décoder deux vidéos en même temps, ce qui n'est pas
  fiable sur tous les téléphones.
* **Musiques** : composées par l'application au premier usage (synthèse sonore en Kotlin), boucles parfaites,
  même niveau sonore pour les quatre, **aucun droit d'auteur** et aucun fichier audio dans le dépôt.
* **Projets** : un fichier JSON par projet dans le stockage privé de l'application (écriture atomique) ;
  l'accès aux médias choisis est conservé de façon permanente. Si un média est supprimé de la galerie,
  le projet l'indique (« média introuvable ») et l'export explique quoi faire.
* **YouTube** : API YouTube Data v3, envoi « reprenable » par blocs de 8 Mo (reprise après coupure réseau),
  WorkManager avec notification (continue si l'application est quittée, attend le Wi-Fi si demandé).
  Autorisation Google limitée à *envoyer des vidéos* et *voir l'adresse e-mail du compte* : l'application ne peut
  ni lire, ni modifier, ni supprimer vos vidéos ; aucun mot de passe ni jeton longue durée n'est stocké.

## Hypothèses retenues

* Android 10 minimum ; sortie 1920×1080 16:9, H.264 12 Mb/s, AAC ; médias verticaux centrés avec bandes noires ;
  vidéos HDR converties en SDR.
* Photo 4 s par défaut (2–10 s) ; génériques 4 s (2–8 s) ; la date du générique est facultative (il est commun).
* Vidéo importée proposée en « Illustration » si ≤ 30 s, sinon « Interview » ; son d'origine coupé sur les
  illustrations, conservé sur les interviews ; musique coupée pendant les interviews (ou atténuée à 12 %).
* Transition par défaut : Fondu au noir, 0,6 s. Visibilité YouTube par défaut : Privée.
* La vidéo envoyée sur YouTube est le dernier MP4 exporté du projet : exportez à nouveau après une modification.

## Robustesse mémoire

Traitement en flux image par image (codecs matériels), audio tampon par tampon, photos décodées
sous-échantillonnées, bitmaps libérés au fil de l'eau ; envoi YouTube lu par blocs depuis la galerie ;
contrôle d'espace libre avant l'export ; écran maintenu allumé pendant le rendu.

---

## 1. Créer une clé de signature fixe (indispensable pour YouTube)

Google reconnaît l'application à l'empreinte SHA-1 de la clé qui la signe. Sans clé fixe, chaque compilation
GitHub utilise une clé différente et la connexion YouTube échoue.

```bash
keytool -genkeypair -v -keystore monmontage.jks -alias monmontage -keyalg RSA -keysize 2048 -validity 10000
```
(`keytool` est fourni avec Java / Android Studio. Notez le mot de passe choisi.)
Gardez `monmontage.jks` en lieu sûr, **hors du dépôt** (le `.gitignore` l'exclut).

Afficher l'empreinte SHA-1 (à copier pour l'étape 3) :
```bash
keytool -list -v -keystore monmontage.jks -alias monmontage
```

## 2. Donner la clé à GitHub (secrets du dépôt)

Convertir la clé en texte :
* Mac / Linux : `base64 -i monmontage.jks | tr -d '\n' > cle.txt`
* Windows (PowerShell) : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("monmontage.jks")) > cle.txt`

Sur GitHub : dépôt › **Settings › Secrets and variables › Actions › New repository secret**, créez :

| Nom | Valeur |
|-----|--------|
| `KEYSTORE_BASE64` | le contenu de `cle.txt` |
| `KEYSTORE_PASSWORD` | le mot de passe de la clé |
| `KEY_ALIAS` | `monmontage` |
| `KEY_PASSWORD` | le même mot de passe (sauf si vous en avez choisi un autre pour l'alias) |

Supprimez ensuite `cle.txt`. Les APK compilés par GitHub seront désormais tous signés avec cette clé.
Pour compiler en local avec la même clé : créez à la racine un `keystore.properties` (non versionné) :
```
storeFile=monmontage.jks
storePassword=VOTRE_MOT_DE_PASSE
keyAlias=monmontage
keyPassword=VOTRE_MOT_DE_PASSE
```

## 3. Déclarer l'application chez Google (une seule fois, gratuit)

1. Ouvrez la console Google Cloud (console.cloud.google.com), créez un projet « Mon Montage ».
2. **API et services › Bibliothèque** : cherchez **YouTube Data API v3** › **Activer**.
3. **Google Auth Platform** (écran de consentement OAuth) :
   * **Branding** : nom de l'application, e-mail d'assistance ;
   * **Audience** : type **Externe**, puis ajoutez votre adresse Gmail dans **Utilisateurs test** ;
   * **Accès aux données** : ajoutez les champs d'application `.../auth/youtube.upload` et `.../auth/userinfo.email`.
4. **Clients › Créer un client › Android** : nom du paquet `com.monmontage.app`, empreinte **SHA-1** de l'étape 1.

Aucune clé ni identifiant n'est à copier dans le code : Google associe automatiquement l'application signée
(nom de paquet + SHA-1) à ce client.

**Mode test** : tant que l'application reste « en test » chez Google, seuls les comptes ajoutés comme testeurs
peuvent se connecter, et Google peut redemander l'autorisation environ chaque semaine (un écran, un geste).
Pour l'éviter, dans **Audience**, cliquez sur **Publier l'application** (passage en production sans validation) :
Google affichera alors un avertissement « application non validée » à la connexion, à accepter via
*Paramètres avancés › Accéder à Mon Montage* — acceptable pour un usage personnel.

Quotas : l'envoi de vidéos dispose de son propre quota (100 envois par jour par défaut), largement suffisant.

## 4. Compiler et installer

1. Envoyez le projet sur GitHub (commit + push) : l'onglet **Actions** compile l'APK automatiquement.
2. Téléchargez **MonMontage-debug-apk** dans *Artifacts*, décompressez-le.
3. **Si une version précédente signée avec une autre clé est installée, désinstallez-la d'abord**
   (Android refuse une mise à jour signée différemment). ⚠️ Cela efface les projets enregistrés dans l'application ;
   les vidéos exportées restent dans la galerie.
4. Installez `app-debug.apk` depuis l'application Fichiers (autoriser cette source si demandé).

Compilation locale : ouvrez le dossier dans Android Studio, ou `./gradlew assembleDebug`
(APK : `app/build/outputs/apk/debug/app-debug.apk`).

## 5. Tester

1. **Mes projets › Nouveau projet** ; ajoutez photos et vidéos ; renommez le projet en touchant son titre.
2. Choisissez une musique (écoutez les 4 styles), une transition, puis **Aperçu**.
3. **Exporter** : la vidéo arrive dans *Galerie › Films/MonMontage*.
4. **Publier sur YouTube** : connexion la première fois, puis titre, visibilité **Privée** pour le premier essai,
   **Publier**. Quittez l'application : la notification montre la progression.
5. Revenez à **Mes projets** : le projet porte le badge « Sur YouTube ». Rouvrez-le : tout est conservé.

Problème de connexion Google « configuration incomplète » : vérifiez le nom de paquet et la SHA-1 (étape 3),
et que l'APK installé vient bien d'une compilation signée avec la clé fixe.
Journaux : `adb logcat -s MonMontage WM-WorkerWrapper`.
