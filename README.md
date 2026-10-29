# Rabux TV

Lecteur IPTV pour Android TV, compatible **Xtream Codes** (serveur + identifiant + mot de passe), avec une interface inspirée de l'Apple TV.

## Fonctionnalités (v0.7)

**Nouveautés v0.7**
- **Connexion depuis le téléphone** : QR code sur l'écran de connexion, saisie au clavier du téléphone (même Wi-Fi)
- **Pause du direct** : OK ouvre la barre de commandes (pause, −1 min, −5 min, retour au direct) ; différé du serveur sur les chaînes avec replay
- **Rappels** : maintiens OK sur un match à venir dans Sport ce soir ; alerte dans l'app et notification
- **Multi-écran** : 2 à 4 chaînes en même temps (limité par l'abonnement), le son suit la case sélectionnée
- **Fiches enrichies TMDB** : bande-annonce (YouTube), casting, « Dans la même veine » limité à ce qui est disponible chez toi, rangées « Parce que tu as regardé… » sur l'accueil
- **Économiseur d'écran** : diaporama des images de fond ou mur d'affiches, avec l'heure
- **Mon bilan** : temps regardé (7 jours, 30 jours, 12 mois), répartition direct/films/séries, palmarès, genres, activité
- **Canal bêta** (Réglages) et **rapports de plantage** (Sentry, identifiants masqués)


**Nouveautés v0.6**
- **Guide TV** : programme en cours (avec avancement) sur chaque chaîne, programme suivant dans le bandeau du lecteur
- **Aperçu au focus** : la vidéo de la chaîne se lance, muette, dans sa carte après un instant (désactivable)
- **Sport ce soir** : pastille « ⚽ » dans Direct, matchs en cours et à venir sur les chaînes sport FR
- **Zapping** : touches 0-9 pour un numéro, ← chaîne précédente, → liste des chaînes avec leur programme
- **Accueil Android TV** : films et séries en cours dans la rangée « À suivre » du lanceur (selon le modèle)
- **Minuteur de veille** (Réglages) : 30 à 120 min, avertissement une minute avant l'arrêt
- **Contrôle parental** : appui long sur une pastille pour masquer ou verrouiller une catégorie, code à 4 chiffres
- **Quoi de neuf** : après une mise à jour, la liste des nouveautés s'affiche une fois
- Nouveau logo et nouvelle bannière

**Navigation**
- **Tri et filtre** des films et séries : Récents, Note ou A → Z, et note minimale (≥ 6, ≥ 7, ≥ 8), mémorisés
- **↻ Actualiser** dans Direct, Films et Séries (et « Actualiser tout le catalogue » dans Réglages) : recharge depuis le serveur sans passer par le cache
- **Tri France en priorité** (désactivable dans Réglages) : sport FR en tête du direct ; catégories FR puis VOSTFR pour les films et séries, les plus récents d'abord ; pastille « ✦ Dernières sorties » ouverte par défaut
- Onglets façon tvOS : Accueil · Direct · Films · Séries · Rechercher · Réglages, horloge en haut à droite
- **Accueil** : rangées « À suivre », chaînes favorites, chaînes récentes, films et séries favoris, films ajoutés récemment, séries mises à jour
- **Recherche** dans tout le catalogue (chaînes, films, séries), sans tenir compte des accents
- **Favoris** : appui long sur OK sur n'importe quelle carte, ou bouton dans les fiches. Pastille « ★ Favoris » en tête de chaque section

**Lecture**
- **Direct** avec zapping, **reconnexion automatique** si le flux coupe
- **Replay** : pastille « ⟲ Replay » dans Direct (chaînes qui le proposent), programmes des jours passés
- **Films et séries** : fiches plein écran, reprise de lecture, avance et retour de 10 s
- **Audio et sous-titres** : ↓ pendant la lecture (VF par défaut quand elle existe)
- **Épisode suivant automatique** : proposé 20 s avant la fin, lancé après un compte à rebours de 10 s

**Technique**
- **Cache disque** : les catalogues s'affichent instantanément au lancement puis se rafraîchissent en arrière-plan
- **Mise à jour intégrée** : Réglages › Rechercher une mise à jour (depuis les Releases GitHub)
- **CI GitHub Actions** : APK compilé à chaque push, publié en Release à chaque tag

### Télécommande

| Écran | Touche | Action |
|---|---|---|
| Cartes | OK maintenu | Ajouter / retirer des favoris (retirer de « À suivre » sur l'accueil) |
| Direct | ↑ / CH− · ↓ / CH+ | Chaîne précédente / suivante |
| Direct | OK | Afficher / masquer le bandeau |
| Film / épisode / replay | OK, ⏯ | Pause / lecture |
| Film / épisode / replay | ← / → | −10 s / +10 s |
| Film / épisode / replay | ↓ | Audio et sous-titres |
| Partout | Retour | Écran précédent |

## Stack

Kotlin 2.0 · Jetpack Compose for TV · Media3 ExoPlayer 1.4 · OkHttp · Coil
minSdk 23 (Android 6) · targetSdk 34

```
app/src/main/java/fr/rabuxx/iptvtv/
├── MainActivity.kt          aiguillage des écrans
├── MainViewModel.kt         navigation, catalogues, recherche, favoris, historique, mises à jour
├── data/
│   ├── XtreamClient.kt      API Xtream (direct, replay, films, séries) + cache
│   ├── DiskCache.kt         cache disque des réponses
│   ├── LibraryStore.kt      favoris, « À suivre », chaînes récentes
│   ├── CredentialsStore.kt  identifiants, dernières catégories, positions de lecture
│   ├── Updater.kt           mise à jour depuis GitHub Releases
│   └── Models.kt
└── ui/
    ├── Theme.kt, Components.kt
    ├── LoginScreen.kt
    ├── MainScreen.kt        onglets, grilles, réglages
    ├── HomeScreen.kt        accueil
    ├── SearchScreen.kt      recherche
    ├── DetailScreens.kt     fiches film et série
    ├── ReplayScreen.kt      programmes en replay
    └── PlayerScreen.kt      lecteurs direct et VOD
```

---

## Services optionnels (secrets GitHub)

| Secret | Rôle | Sans le secret |
|---|---|---|
| `TMDB_API_KEY` | Clé API TMDB (gratuite : themoviedb.org › Paramètres › API) | Fiches enrichies désactivées (une clé peut aussi être saisie depuis le téléphone dans Réglages) |
| `SENTRY_DSN` | DSN d'un projet Sentry (ou GlitchTip auto-hébergé, compatible) | Aucun rapport de plantage |

En local, les mêmes noms peuvent être mis dans `local.properties` (non versionné).

## Publier une bêta

```powershell
git tag v0.7.1-beta.1
git push origin v0.7.1-beta.1
```

Un tag avec un tiret est publié en **pré-version** : seules les TV réglées sur le canal bêta la proposent, et le lien `latest/download/rabux-tv.apk` continue de pointer vers la dernière version stable.

## Qualité (CI)

- **Tests unitaires** (`app/src/test`) : tri FR/VOSTFR/sport, filtres, nettoyage des titres, versions et bêtas, rapprochement TMDB, bilan, masquage des identifiants, serveur de saisie, guide TV, formatage.
- **Tests d'interface** (`app/src/androidTest`) : l'app démarre sur un émulateur Android et affiche l'écran de connexion (job séparé, non bloquant pour les Releases).
  Lancer en local : `./gradlew testDebugUnitTest`
- La CI exécute les tests à chaque push et pull request ; **si un test échoue, aucun APK ni Release n'est publié**.
- Rapport **lint** publié en artefact du workflow (non bloquant).
- **Dependabot** propose chaque semaine les mises à jour des dépendances (pull requests testées par la CI).
- Les **notes de version** sont générées à partir des messages de commit depuis le tag précédent, puis affichées dans l'app.

## Mettre en place GitHub (CI + mises à jour) — à faire une fois

### 1. Créer le dépôt

Crée le dépôt **public** `iptv-tv` sur ton compte GitHub (public : l'app interroge l'API des Releases sans jeton ; aucun identifiant IPTV n'est dans le code).
Si le dépôt a un autre nom, modifie `iptv.updateRepo` dans `gradle.properties`.

```powershell
cd C:\Users\petit\Downloads\iptv-tv\iptv-tv
git init
git add .
git commit -m "Rabux TV v0.7.0"
git branch -M main
git remote add origin https://github.com/Rabuxx/iptv-tv.git
git push -u origin main
```

### 2. Créer la clé de signature

Une clé **stable** est indispensable : Android refuse d'installer une mise à jour signée avec une autre clé.

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore iptv-release.jks -alias iptv -keyalg RSA -keysize 2048 -validity 10000
```

Garde ce fichier et ses mots de passe précieusement (hors du dépôt : `*.jks` est dans `.gitignore`).

### 3. Ajouter les secrets GitHub

Copie la clé en base64 dans le presse-papiers :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("iptv-release.jks")) | Set-Clipboard
```

Puis dans GitHub : *Settings › Secrets and variables › Actions › New repository secret* :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | le contenu du presse-papiers |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | `iptv` |
| `KEY_PASSWORD` | mot de passe de la clé |

### 4. Utiliser la même clé en local

Crée `keystore.properties` à la racine du projet (non versionné) :

```properties
storeFile=iptv-release.jks
storePassword=...
keyAlias=iptv
keyPassword=...
```

Les APK compilés dans Android Studio et ceux de la CI auront ainsi la même signature.

> **Une seule fois** : l'app actuellement sur ta TV est signée avec la clé debug. Désinstalle-la avant d'installer la première version signée avec ta clé (`adb uninstall fr.rabuxx.iptvtv`), puis tu n'auras plus jamais à le refaire.

### 5. Publier une version

```powershell
git tag v0.4.0
git push origin v0.4.0
```

Le workflow compile l'APK et le publie dans *Releases*. Pour les versions suivantes : modifie le code, commit, push, puis tag `v0.3.1`, `v0.4.0`… L'app propose alors la mise à jour dans **Réglages** (un point apparaît sur l'onglet).

Au premier essai, Android TV demande d'autoriser « Rabux TV » à installer des applications : accepte, reviens dans l'app et relance l'installation.

---

## Installer chez quelqu'un d'autre

Lien toujours à jour vers la dernière version :
**https://github.com/Rabuxx/iptv-tv/releases/latest/download/rabux-tv.apk**

- **Avec Downloader** (Play Store de la TV) : saisir ce lien, autoriser Downloader à installer des applis, installer.
- **Avec une clé USB** : copier l'APK sur la clé, installer *X-plore File Manager* ou *File Commander* sur la TV, ouvrir l'APK depuis la clé.
- Si Play Protect s'affiche : *Plus de détails › Installer quand même*.

Chacun saisit ses propres identifiants Xtream. Les mises à jour arrivent ensuite automatiquement dans Réglages.

## Compiler et installer à la main (ADB)

1. Ouvrir le dossier dans **Android Studio**, attendre la synchronisation Gradle.
2. *Build › Generate App Bundles or APKs › Build APK(s)*.
3. Dans un terminal PowerShell, à la racine du projet :

```powershell
Set-Alias adb "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
adb connect 192.168.1.13:5555
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Pistes pour la suite

- Guide TV (EPG) sous les chaînes du direct
- Saisie directe du numéro de chaîne (0-9) et retour à la chaîne précédente
- Masquer des catégories, code parental
- Profils multiples (plusieurs abonnements)

## Dépannage

- **« ce serveur ne semble pas compatible Xtream Codes »** : vérifier l'adresse et le port (`http://domaine:8080`), sans `/player_api.php`.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`** : la version installée est signée avec une autre clé → `adb uninstall fr.rabuxx.iptvtv` puis réinstaller.
- **« Aucune release publiée »** dans Réglages : pousse un tag `v…` et attends la fin du workflow.
- **Pas de pastille Replay** : ton fournisseur ne propose pas de replay, ou le catalogue complet est encore en cours de chargement.
- **Recherche vide au premier lancement** : le catalogue complet se télécharge en arrière-plan (quelques secondes à une minute selon le fournisseur), puis il est mis en cache.
