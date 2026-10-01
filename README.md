# Suical

Application web installable (PWA) de suivi du stock de courses, des plats et des apports
nutritionnels. Tout fonctionne hors ligne : aucune donnée ne quitte l'appareil.

## Contenu

| Fichier | Rôle |
|---|---|
| `index.html` | Toute l'application : interface, données par défaut, calculs. Aucun framework, aucune dépendance externe hormis la police Google Fonts |
| `manifest.webmanifest` | Nom, icônes et mode `standalone` pour l'installation |
| `sw.js` | Service worker, cache des fichiers pour le mode hors ligne |
| `icons/` | Icônes 192, 512, 512 maskable et apple-touch |

## Stockage

Les données sont écrites dans **IndexedDB** (base `suical`, store `state`, clé `main`),
avec une copie de secours en `localStorage` à chaque sauvegarde et à la fermeture de l'onglet.
L'onglet Stock permet d'exporter en JSON (presse-papier ou fichier), de réimporter et de réinitialiser.

## Mise en ligne sur GitHub Pages

```bash
git init
git add .
git commit -m "Suical, première version"
git branch -M main
git remote add origin git@github.com:<utilisateur>/suical.git
git push -u origin main
```

Puis dans le dépôt : **Settings → Pages → Source : Deploy from a branch → `main` / `root`**.
L'URL `https://<utilisateur>.github.io/suical/` est disponible après une minute environ.

## Installation sur Android

Ouvrir l'URL dans Chrome, puis menu `⋮` → **Installer l'application** (ou « Ajouter à l'écran d'accueil »).
L'app se lance ensuite en plein écran, sans barre d'adresse, et fonctionne sans réseau.

## Déployer une mise à jour

Le service worker sert les fichiers depuis le cache. Après chaque modification :

1. Incrémenter la constante `CACHE` dans `sw.js` (`suical-v2`, `v3`, …).
2. Commit et push : GitHub Pages redéploie automatiquement.
3. Rouvrir l'app deux fois : la première récupère la nouvelle version, la seconde l'affiche.

Sans l'incrément de `CACHE`, l'ancienne version reste servie.

## Développement local

```bash
python3 -m http.server 8000
```

Puis ouvrir `http://localhost:8000`. Le protocole `file://` ne convient pas :
les service workers et IndexedDB y sont bloqués ou instables.
