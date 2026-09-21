# Installation de Cellier Manager 2.1.14-dev

## 1. Protéger le cellier actuel

Avant toute installation sur le téléphone principal, ouvrir l’ancienne application et conserver son export existant. La première ouverture de la version 2.1 migre automatiquement la base Room 6 vers 7. Après cette ouverture, aller dans **Réglages → Créer une sauvegarde** et vérifier qu’un fichier `.cellierbackup` a été créé.

Ne pas désinstaller l’application : une désinstallation supprimerait sa base locale et ses photos. La nouvelle version doit être installée comme mise à jour du même `applicationId` et avec la même signature que la version déjà installée.

## 2. Installer et démarrer le compagnon Windows

Installer Python 3.11 ou plus récent, puis lancer `companion/start-companion.ps1`. Au premier démarrage, le script crée l’environnement Python et installe les dépendances déclarées. Les démarrages suivants réutilisent cet environnement.

Le navigateur ouvre `http://127.0.0.1:18765`. Windows peut demander l’autorisation d’ouvrir le port privé 8766. L’autoriser uniquement sur le réseau privé utilisé par le téléphone et l’ordinateur.

## 3. Jumeler Android

Sur la page locale du compagnon, cliquer **Créer un fichier d’appairage Android**. Le fichier est valable cinq minutes et ne peut servir qu’une fois.

Transférer le fichier au téléphone par un moyen local de confiance. Dans Cellier Manager, ouvrir **Réglages → Importer le fichier de jumelage** et choisir ce fichier. Android vérifie le certificat inclus puis échange le secret temporaire contre un jeton propre à l’appareil.

## 4. Installer et jumeler l’extension

Dans `extension`, exécuter `npm ci`, puis `npm test`. Ouvrir `chrome://extensions`, activer le mode développeur, cliquer **Charger l’extension non empaquetée** et choisir `extension/dist`.

Sur la page du compagnon, cliquer **Créer un code d’appairage extension**. Ouvrir la fenêtre de l’extension et recopier l’identifiant et le secret affichés. L’adresse reste `http://127.0.0.1:18765`. Le script `scripts/setup_chrome_extension.mjs` peut aussi créer le profil Chrome dédié, charger l’extension et effectuer ce jumelage automatiquement.

## 5. Vérifier le parcours avant le vrai inventaire

Créer une fiche de démonstration, demander une recherche, puis :

1. vérifier que Chrome s’ouvre automatiquement sur les résultats de la bonne source ;
2. pour chaque source, vérifier que le premier résultat suffisamment concordant s’ouvre automatiquement ;
3. vérifier qu’une concordance insuffisante devient une erreur explicite sur Android et n’applique aucune fiche ;
4. synchroniser Android et vérifier les renseignements importés ;
5. vérifier que le nom et le producteur adoptent les valeurs canoniques de la source, que le millésime proposé est cohérent et que la quantité demeure inchangée ;
6. vérifier que la fiche Android suit les étapes affichées dans Chrome et termine par **Recherche terminée — renseignements ajoutés** ou par une erreur explicite ;
7. vérifier qu’un résultat réussi affiche **Match parfait** et que le profil Chrome dédié se ferme automatiquement.

Les trois sites évoluent indépendamment du projet. Une capture réelle doit donc être validée pour chaque source avant de considérer son adaptateur comme confirmé.

## 6. Désinstaller ou réinitialiser le compagnon

Les données du compagnon sont dans `%LOCALAPPDATA%\CellierManagerCompanion`. La suppression de ce dossier révoque de fait les anciens jumelages, mais supprime aussi les recherches en attente. Le cellier Android demeure utilisable. Il faut ensuite refaire les deux jumelages.
