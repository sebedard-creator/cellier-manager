# Transmission pour audit — Cellier Manager 2.1.14-dev

Date de mise à jour : 21 septembre 2026.

## État actuel

La refonte sépare l’inventaire Android de l’acquisition web. Android demeure la source de vérité et conserve toute la logique de quantité, de photo, de sauvegarde et de restauration. Le compagnon Windows gère les demandes persistantes, démarre un profil Chrome dédié et analyse les captures envoyées par l’extension. L’extension automatise la recherche et la sélection d’une fiche SAQ, Vivino ou Untappd avec des règles propres à chaque source.

Versions préparées :

- Android : `2.1.14-dev` (`versionCode 224`), schéma Room 7 ;
- compagnon : `0.2.13` ;
- extension Chrome : `0.6.8`.

Les anciens parseurs Android, appels directs aux sites et fournisseurs IA d’indexation ont été retirés du chemin de production. `IndexingWorker` reste volontairement présent comme adaptateur inoffensif : il annule l’ancien travail `cellar_indexing` et retourne un succès si Android restaure un ancien `WorkRequest`. L’entité `SearchCacheEntry` est conservée pour la compatibilité du schéma Room, mais son DAO inutilisé a été retiré.

## Parcours validés

Le parcours téléphone Android ↔ compagnon Windows ↔ Chrome a été exécuté sur le réseau local avec un téléphone réel connecté à Android Studio par Wi-Fi.

- Une demande Android ouvre automatiquement Chrome sur la recherche appropriée.
- SAQ ouvre le premier produit validé, capture la fiche et applique les renseignements.
- Vivino ouvre la fiche correspondant au vin et au millésime demandé.
- Untappd sélectionne le produit exact en tenant compte de la brasserie, du nom et de l’année.
- Les états en cours, échoué et terminé sont visibles sur Android.
- Une proposition plausible met à jour le nom et le producteur avec les valeurs canoniques du site.
- Une réussite affiche **Recherche terminée — renseignements ajoutés** et **Match parfait**.
- Le profil Chrome dédié est fermé automatiquement après l’opération.

Des régressions réelles corrigées couvrent notamment L’Orpailleur Gris, Péché Mortel Bourbon 2024, Metamorfosis 2018 et `50°N - 4°E (Batch 7 - 2020)`. Les règles refusent encore les variantes nommées incompatibles comme `Framboise` ou `Unblended`.

## Invariants à contrôler pendant l’audit

- `CellarRepository.applyProposal` relit la demande, la proposition et la fiche dans la même transaction.
- Une proposition dont l’identité ou la révision est périmée n’est pas appliquée.
- La quantité ne provient jamais d’une proposition web.
- Les augmentations et diminutions de quantité utilisent des mises à jour SQL atomiques.
- Une modification du type, du producteur, du nom ou du millésime invalide les demandes ouvertes.
- Chaque champ appliqué reçoit une origine avec sa source, son URL et l’identifiant de proposition.
- La restauration valide les limites, empreintes, doublons et références avant toute mutation.
- Les sauvegardes excluent jetons, clés API, clés privées TLS, tâches réseau et captures web.
- Les routes `/local/*` ne répondent qu’au loopback ; un client d’origine inconnue est traité comme distant.
- Le compagnon refuse les domaines ressemblants, les URL avec identifiants intégrés et les captures trop grandes.

## Sécurité et dépôt

`local.properties` contient de la configuration propre à la machine et peut contenir des clés API. Il est exclu de Git. Les environnements virtuels, dépendances Node, builds, APK, paquets `dist`, sauvegardes de téléphone, bases SQLite, fichiers de jumelage, certificats et clés privées sont également exclus.

Les anciens fichiers de diagnostic `algolia*.json`, `vivino_sassicaia.html`, les APK copiés à la racine et les programmes temporaires `Test*`/`Patch.kt` sont exclus du dépôt. Les fixtures synthétiques sous `companion/tests/fixtures` restent suivies parce qu’elles alimentent les tests des parseurs.

## Vérifications automatisées

Commandes de référence :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin

.\companion\.venv\Scripts\python.exe -m pytest companion\tests -q

cd extension
npm test
```

Au dernier contrôle ciblé, les 27 tests du compagnon et les 16 tests de l’extension réussissent. Le build Android complet doit être rejoué après toute modification Kotlin. Les tests instrumentés de migration nécessitent un émulateur ou un appareil connecté.

## Limites restantes

- Les structures HTML des trois sites peuvent évoluer ; chaque parseur doit conserver ses fixtures et incrémenter sa version lorsqu’une règle change.
- Le compagnon est lancé par PowerShell/Python et n’est pas distribué comme exécutable Windows signé.
- L’extension est chargée localement en mode développeur et n’est pas publiée dans le Chrome Web Store.
- Les anciennes migrations Room 2 à 5 sont enregistrées, mais le scénario instrumenté actuellement fourni cible surtout 6 vers 7.
- Une sauvegarde externe vérifiée reste recommandée avant toute mise à jour du téléphone principal.

## Artefacts locaux

Le script `scripts/package_release.py` produit les fichiers suivants dans `dist/`, qui n’est pas suivi par Git :

- `cellier-manager-2.1.14-dev-debug.apk` ;
- `cellier-companion-0.2.13-source.zip` ;
- `cellier-extension-0.6.8.zip`.
