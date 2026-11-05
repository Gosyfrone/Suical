# Suical

Application web installable (PWA) de recettes simples et pas chères : prix estimé de chaque plat,
planning de la semaine, budget du mois, liste de courses et apports nutritionnels.
Pensée pour cuisiner seul avec un air fryer et sans congélateur. Tout fonctionne hors ligne :
aucune donnée ne quitte l'appareil.

L'app Android (`android/`) reste l'outil de suivi calorique ; le site est indépendant.

## Écrans

Pensé pour le téléphone : quatre onglets en bas de l'écran, des pages qui s'ouvrent par-dessus
(le bouton retour du téléphone les ferme) et des panneaux qui montent du bas pour les choix.

| Onglet | Rôle |
|---|---|
| Recettes | 37 repas et 17 petits-déj / goûters, avec recherche, tri et un panneau Filtres (féculent, protéine, favoris, pas chères, sans gluten…). Une recette s'ouvre sur sa fiche : prix, temps, kcal, portions réglables, ingrédients, étapes, valeurs nutritionnelles. Le menu ⋯ permet de la modifier, la dupliquer ou la marquer « Pas pour moi » |
| Semaine | Budget du mois, choix de 5 à 7 jours, une carte par jour (petit-déj, midi, goûter, soir). Toucher un repas ouvre la liste des plats ; changer un soir change aussi le midi du lendemain qui en était le reste |
| Courses | Liste à cocher par rayon, moins le stock, avec alerte sur la viande fraîche qui resterait ; « J'ai fait les courses » ajoute au stock |
| Moi | Profil et besoins estimés, budget, pâtes sans gluten, plafond de gluten, et les pages Aliments et prix, Stock, Recettes mises de côté, Plats déjà cuisinés, Sauvegarde |

La semaine proposée prend les favoris d'abord puis les plats les moins chers, jamais les recettes retirées, au moins un soir de poulet, deux soirs au plus
avec la même viande, une viande rouge au plus, un plafond de repas avec gluten, et finit les barquettes
de viande fraîche entamées. Petit-déj et goûters réutilisent de préférence les produits déjà achetés
(par exemple les wraps restants du sachet). Le plat du soir se cuisine en 2 portions, la 2e fait le midi du lendemain.

## Contenu

| Fichier | Rôle |
|---|---|
| `index.html` | Toute l'application : interface, données par défaut, calculs. Aucun framework, aucune dépendance externe hormis la police Google Fonts |
| `manifest.webmanifest` | Nom, icônes et mode `standalone` pour l'installation |
| `sw.js` | Service worker, cache des fichiers pour le mode hors ligne |
| `icons/` | Icônes 192, 512, 512 maskable et apple-touch |

## Stockage

Les recettes ajoutées dans une mise à jour du site apparaissent chez l'utilisateur au chargement suivant
(liste `known`), sans toucher à ses recettes modifiées ni remettre celles qu'il a supprimées.

Les données sont écrites dans **IndexedDB** (base `suical`, store `state`, clé `recettes-v3`),
avec une copie de secours en `localStorage` à chaque sauvegarde et à la fermeture de l'onglet.
La page Moi › Sauvegarder ou importer permet d'exporter en JSON (fichier ou presse-papier) et de réimporter ; Moi › Tout remettre à zéro réinitialise.
Les données de l'ancienne version du site (clé `main`) restent dans la base, non utilisées.

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
