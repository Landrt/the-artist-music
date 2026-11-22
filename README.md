# The @rtist Music

Lecteur de musique **100 % local / hors-ligne** écrit en **Kotlin + Compose Multiplatform** (Android + Preview Desktop Linux), avec compilation automatique de l'APK via **GitHub Actions**.

## Fonctionnalités
- **100 % hors-ligne** : scan des fichiers audio locaux via Android `MediaStore` et lecture native via **Media3 ExoPlayer** (aucun compte, aucune pub, aucun streaming).
- **Interface épurée** (mode Clair & Sombre, accent corail `#FF1949`) :
  - Cartes d'accès rapide (*Tous les titres*, *Favoris*, *Récents*, *Playlists*)
  - Onglets : **Morceaux**, **Albums**, **Artistes**, **Dossiers**, **Playlists**
  - Recherche instantanée & création de playlists locales
  - Mini-lecteur flottant avec barre de progression
  - Lecteur plein écran immersif (dégradé dynamique, animation pochette, vue paroles `.lrc` locales, file d'attente, aléatoire & répétition)

---

## 1. Tester le frontend en direct sur Linux (sans émulateur Android)

Il suffit d'avoir **JDK 17** et **Gradle** (ou `gradlew`) :

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk gradle
cd the-artist-music
gradle :composeApp:run
```

Une fenêtre au format téléphone (`412×860`) s'ouvre immédiatement avec des pistes de démo (ou les fichiers de `~/Music` / `~/Musique`).

---

## 2. Compiler l'APK automatiquement avec GitHub Actions

1. Initialise Git dans le dossier `the-artist-music` et pousse sur un dépôt GitHub :

```bash
cd the-artist-music
git init
git add .
git commit -m "Initial commit: The @rtist Music"
git branch -M main
git remote add origin https://github.com/<TON_USER>/<TON_REPO>.git
git push -u origin main
```

2. Ouvre l'onglet **Actions** sur GitHub → workflow **Build Android APK & Verify Desktop**.
3. Télécharge l'artifact **`the-artist-music-debug-apk`** (`composeApp-debug.apk`) et installe-le sur ton téléphone Android !
