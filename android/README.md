# Suical, app Android

Même fonctionnement que le site (plats, semaine, stock, courses, aliments), en app native.
Kotlin + Jetpack Compose + Room (SQLite local). Aucune connexion requise.

## Construire l'APK

```bash
cd android
./gradlew assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest # tests du domaine
```

Ou ouvrir le dossier `android/` dans Android Studio et lancer ▶.
`local.properties` (chemin du SDK) est propre à chaque machine et n'est pas versionné.

## Architecture

| Dossier | Rôle |
|---|---|
| `domain/` | Kotlin pur : modèles, `Nutrients` (liste des nutriments), `Calc` (tous les calculs), `Config` (leviers, sessions), `Defaults` |
| `data/` | Room (`Db.kt`), `Repository` (seul accès à la base), `Backup` (JSON, compatible avec l'export du site) |
| `llm/` | `Prompts` (construction des demandes), `LlmBridge` (envoi vers l'app IA, presse-papier), `LlmImport` (lecture de la réponse) |
| `ui/` | `MainViewModel` (toutes les actions) et un fichier par écran |

Ajouter un nutriment : une ligne dans `Nutrients.kt`. Il apparaît dans l'éditeur, les totaux, la sauvegarde et les prompts.

## Export vers l'IA

Les boutons « Demander à l'IA » envoient un prompt par partage Android vers l'app choisie dans Réglages
(Claude par défaut), et le copient aussi dans le presse-papier. La réponse est un JSON : on la copie dans l'app IA,
puis « Coller la réponse » met à jour les aliments. Les prompts sont tous dans `llm/Prompts.kt`.

## Fonctions ajoutées

| Fonction | Où |
|---|---|
| Recette créée par l'IA (« Avec l'IA » puis « Coller la recette de l'IA ») | Plats |
| Propositions de l'IA applicables en un tap (cases à décocher) | Semaine |
| Photo d'étiquette envoyée à l'IA | Aliments > un aliment |
| Scan de code-barres (Open Food Facts, seul usage d'internet) | Aliments |
| Rappels : stock bas, veille de session de cuisine | Réglages > Rappels |
| Widget : calories du jour + bouton « Mangé » du repas prévu | Écran d'accueil |
| Statistiques et courbe de poids, maintenance observée | « Stats » en haut |
| Repas hors plan (nom + kcal + macros) | Plats |
| Plan de 1 à 28 jours, jour 1 réglable | Réglages > Plan |

Base de données en version 2 (migration automatique depuis la v1, testée).
`Reminders.messages`, `WeightStats`, `OpenFoodFacts.parse` et `LlmImport` sont du Kotlin pur, couverts par `FeaturesTest`.
