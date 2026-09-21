# Changelog

## Préparation du dépôt - 2026-09-21

- Corrige une faille critique du compagnon : `/local/v1/pairings` était servie sur le LAN sans garde, ce qui permettait à n'importe quel appareil du réseau d'obtenir un secret d'appairage puis un jeton permanent. Les routes `/local/*` refusent maintenant tout client non loopback, y compris un client d'origine inconnue.
- Réécrit le README en anglais et documente les versions et parcours actuellement validés.
- Ajoute la licence MIT et renforce les exclusions Git des secrets, sauvegardes et artefacts générés.
- Retire le DAO de l'ancien cache web et les ressources Android inutilisées tout en conservant l'entité pour la compatibilité Room.
- Retire un paramètre TypeScript inutilisé, corrige l'accessibilité de la sélection OCR et actualise les guides d'installation, d'architecture et d'audit.

## Companion v0.2.13 - 2026-09-21

- Répare les symboles de degré perdus dans certains noms capturés depuis Untappd.
- Reconnaît une coordonnée locale abrégée comme `50N` dans la fiche `50°N - 4°E`.
- Conserve l'année lorsqu'elle appartient au nom d'un lot, comme `Batch 7 - 2020`.

## Extension v0.6.8 - 2026-09-21

- Accepte un résultat Untappd unique lorsque seule une courte coordonnée numérotée manque dans le nom local.
- Uniformise les coordonnées comme `50°N` et `50N` avant la comparaison.
- Continue de refuser les variantes nommées comme `Framboise` ou `Unblended`.

## Extension v0.6.7 - 2026-09-21

- Attend le nombre de résultats annoncé par Untappd avant de comparer les fiches.
- Utilise une attente maximale bornée lorsque le compteur de résultats n'est pas disponible.

## Extension v0.6.6 - 2026-09-21

- Attend que la liste dynamique Untappd se stabilise avant de choisir une fiche.
- Corrige le cas où le premier résultat apparaissait seul un court instant avant le résultat exact.

## Extension v0.6.5 - 2026-09-21

- Démarre l'analyse d'une page de recherche Untappd même si des publicités la maintiennent indéfiniment en chargement.
- Examine les résultats Untappd jusqu'au premier produit qui correspond réellement au nom, au producteur et au millésime.
- Évite qu'un résultat populaire au nom proche passe devant la fiche exacte, comme « Metamorfosis Unblended 2018 (Still) » devant « Metamorfosis (2018) ».

## Companion v0.2.12 - 2026-09-21

- Retire le millésime déjà présent dans le nom avant de l'ajouter une seule fois aux termes de recherche.
- Nettoie les parenthèses devenues vides après le retrait du millésime.
- Arrête Chrome et marque la recherche comme échouée si aucune capture n'arrive après 45 secondes.

## Extension v0.6.4 - 2026-09-21

- Applique la même déduplication du millésime lors d'une recherche lancée depuis le menu de l'extension.

## Companion v0.2.11 - 2026-09-21

- Conserve le processus Chrome lancé pour une recherche et termine tout son arbre après la capture.
- Garantit la fermeture même lorsque Chrome ignore la demande provenant de l'extension.

## Extension v0.6.3 - 2026-09-21

- Ferme toujours toutes les fenêtres du profil Chrome dédié après une recherche terminée ou échouée.
- Ignore les anciennes associations d'onglets qui pouvaient empêcher la fermeture du navigateur.

## Companion v0.2.10 - 2026-09-21

- Démarre et redémarre silencieusement sans ouvrir Chrome sur la page d'accueil du compagnon.
- Réserve l'ouverture de Chrome aux recherches lancées depuis Android.

## v2.1.14-dev - 2026-09-21

- Accepte automatiquement un produit lorsque son année figure à la fois dans le nom saisi et dans le champ millésime.
- Réévalue les propositions déjà téléchargées afin d'appliquer immédiatement le résultat corrigé.

## Companion v0.2.9 - 2026-09-21

- Évite de comparer deux fois le millésime après l'avoir retiré du nom Untappd extrait.

## v2.1.13-dev - 2026-09-21

- Applique une fois la graphie officielle aux résultats web déjà validés, afin que le changement soit visible sans nouvelle recherche.
- Ignore cette mise à niveau lorsqu'une fiche a été modifiée après sa recherche web.

## v2.1.12-dev - 2026-09-21

- Remplace automatiquement le producteur et le nom par leur graphie officielle sur la fiche web validée.
- Conserve le millésime saisi et la quantité de la bouteille.
- Met à jour la révision d'identité et rend périmées les autres recherches encore ouvertes pour l'ancienne graphie.

## v2.1.11-dev - 2026-09-21

- Reconnaît sur Android une fiche Vivino valide même lorsque Vivino préfixe le producteur et réordonne les mots du nom.
- Réévalue automatiquement les résultats Vivino déjà téléchargés, sans relancer Chrome.
- Précise qu'un éventuel résultat ambigu se vérifie sur le téléphone.

## Companion v0.2.8 - 2026-09-21

- Reconnaît « Bourgogne Pinot noir » dans le titre Vivino « Athénaïs Pinot Noir Bourgogne » tout en exigeant le bon producteur.

## Extension v0.6.2 - 2026-09-21

- Classe un premier résultat non confirmé comme un échec automatique et ferme le Chrome dédié.
- Ne demande plus d'intervention sur le PC, qui peut rester sans écran ni clavier.

## v2.1.10-dev - 2026-09-21

- Rétablit la coche verte dans le cellier après une correspondance Vivino ou Untappd validée.
- Rétablit le rond vert et le libellé « Match parfait » dans la fiche produit.
- Rattrape automatiquement les propositions déjà appliquées lorsque l’identité de la fiche n’a pas changé.
- Retire la qualité de correspondance lorsqu’un utilisateur modifie l’identité du produit.

## Companion v0.2.7 - 2026-09-21

- Lance le profil Chrome dédié sans processus d’arrière-plan persistant lorsque sa dernière fenêtre est fermée.

## Extension v0.6.1 - 2026-09-21

- Ferme automatiquement le Chrome dédié après la dernière recherche terminée avec succès.
- Ferme seulement l’onglet terminé lorsqu’une autre recherche attend encore dans Chrome.
- Laisse Chrome ouvert lorsqu’un choix manuel ou une correction est nécessaire.

## v2.1.9-dev - 2026-09-21

- Affiche sur la fiche Android la progression réelle de chaque recherche : attente du PC, recherche Chrome, ouverture du résultat, capture, analyse et fin.
- Signale distinctement un compagnon inaccessible, une extension Chrome qui ne répond pas, une intervention requise et un échec dans le navigateur.
- Actualise automatiquement ces états pendant que la fiche ou la liste des recherches est affichée.

## Companion v0.2.6 - 2026-09-21

- Transmet à Android les étapes de travail signalées par l’extension Chrome.
- Rend visibles les échecs de lancement de Chrome et l’étape d’analyse des données capturées.

## Extension v0.6.0 - 2026-09-21

- Signale au compagnon le début de la recherche, la navigation vers le produit, la capture, le besoin d’intervention et les erreurs.

## v2.1.8-dev - 2026-09-20

- Applique automatiquement les propositions Untappd validées.
- Enregistre l'URL de la fiche et met à jour le style, le taux d'alcool et l'IBU lorsqu'ils sont disponibles.

## Companion v0.2.5 - 2026-09-20

- Lit la brasserie, le style, le taux d'alcool et l'IBU depuis l'en-tête produit Untappd.
- Sépare correctement le nom de la bière, l'année et la brasserie du bloc JSON-LD.

## Extension v0.5.0 - 2026-09-20

- Sélectionne automatiquement le premier résultat Untappd seulement si la bière, la brasserie et l'année correspondent exactement.
- Ouvre et capture automatiquement la fiche `/b/...` validée.
- Capture la zone produit propre aux pages Untappd.

## Extension v0.4.2 - 2026-09-20

- Reprend de façon déterministe une recherche Cellier ouverte pendant le démarrage de l'extension.
- Libère automatiquement une automatisation si une page dynamique cesse de répondre.

## v2.1.7-dev - 2026-09-20

- Applique automatiquement les propositions Vivino dont le produit et le millésime sont validés.
- Remplace les anciennes métadonnées web lors d'une nouvelle recherche, tout en conservant les champs saisis manuellement, l'identité et la quantité.

## Companion v0.2.4 - 2026-09-20

- Lit les faits Vivino depuis les lignes produit structurées plutôt que depuis les libellés génériques de la page.
- Extrait précisément le producteur, le pays, la région, les cépages et le type de vin.
- Ignore les liens « Grapes » et « Regions » du pied de page ainsi que la carte générale du producteur.

## Extension v0.4.1 - 2026-09-20

- Capture la racine réelle des fiches Vivino, qui n'utilisent pas d'élément HTML `main`.
- Rend les erreurs de capture communes à toutes les sources.

## Extension v0.4.0 - 2026-09-20

- Sélectionne automatiquement le premier résultat Vivino seulement si le nom, le producteur et le millésime correspondent.
- Ouvre la fiche `/w/...` avec le millésime demandé, puis la capture automatiquement.
- Reprend une recherche déjà ouverte après le rechargement de l'extension.

## v2.1.6-dev - 2026-09-20

- Met à jour une proposition encore en attente lorsqu'un parseur compagnon corrigé la republie.
- Permet d'appliquer automatiquement le résultat réparé sans supprimer la fiche ni modifier sa quantité.

## Companion v0.2.3 - 2026-09-20

- Décode les entités HTML dans les noms de producteurs SAQ avant la comparaison d'identité.
- Reconnaît notamment `Vignoble de l&#039;Orpailleur Inc.` comme correspondant à `L'Orpailleur`.
- Le parseur SAQ passe à `saq-4`.

## v2.1.5-dev - 2026-09-20

**Recherche SAQ automatisée dans Chrome.**

- Chrome ouvre la recherche SAQ, vérifie que le premier résultat contient le nom demandé et correspond au producteur, puis ouvre automatiquement la fiche produit.
- L’extension capture la fiche SAQ chargée et le compagnon déduplique les blocs JSON-LD répétés par le site.
- Les champs SAQ fiables sont appliqués automatiquement sur Android lorsqu’ils sont vides ou proviennent déjà de SAQ; l’identité, le millésime saisi et la quantité sont préservés.
- La fiche « Raisin Brin » a validé le parcours complet réel : pays, région, classification, cépages, alcool et lien canonique SAQ.
- Le bouton de synchronisation Android remplace désormais une attente différée afin de récupérer immédiatement un résultat prêt.
- Aucun changement de schéma Room; la base et les photos existantes sont conservées.

## v2.0.8 - 2026-05-24

**Désélection facile des filtres.**

- Ajout d'une icône "X" (Close) sur les filtres actifs dans l'écran de consultation de l'inventaire.
- Il est maintenant possible de cliquer directement sur cette icône pour désélectionner un filtre sans avoir à rouvrir le menu de sélection ou redémarrer l'application.

## v2.0.7 - 2026-05-15

**Photo plein écran sur les fiches produit.**

- Un tap sur la grande photo d'une fiche vin ou biere ouvre maintenant la photo
  complete en plein ecran.
- L'image plein ecran utilise un affichage `Fit`, donc elle n'est plus croppee.
- Aucun changement de schema Room: la base existante du telephone est conservee.

## v2.0.6 - 2026-05-15

**Millésimes ajoutés manuellement après un match Vivino.**

- L'écran d'édition recharge maintenant ses champs quand la fiche Room change,
  ce qui évite de sauvegarder un ancien état après un match Vivino.
- Le menu principal normalise les millésimes avec `trim()` pour l'affichage et
  les filtres, afin qu'un millésime ajouté manuellement apparaisse bien dans
  les cartes et le dropdown.
- Aucun changement de schéma Room: la base existante du téléphone est conservée.

## v2.0.5 - 2026-05-14

**Ranking Untappd par millesime.**

- Les recherches Untappd reessaient le millesime exact avant les variantes sans
  millesime.
- Quand plusieurs fiches existent, le ranking prefere le millesime exact,
  puis une fiche generique, puis rejette les mauvais millesimes.
- Les fiches `draft version` sont penalisees si une fiche normale du meme
  millesime est disponible.
- Une URL IA Untappd avec un millesime parse different de la fiche est
  maintenant rejetee au lieu d'etre sauvegardee en approximatif.

## v2.0.4 - 2026-05-14

**Filtre par couleur de vin.**

- Ajout d'un filtre `Couleur` dans le menu Mon cellier pour les vins.
- Le filtre propose seulement les couleurs deja presentes dans l'inventaire.
- Ajout de la couleur de vin `Jaune` avec pastille dediee.
- Aucun changement de schema Room: la base existante du telephone est conservee.

## v2.0.3 - 2026-05-14

**Garde-fou Untappd contre les faux positifs.**

- La recherche Untappd essaie maintenant des variantes sans millesime avant de
  retomber sur une URL proposee par l'IA.
- Une page Untappd parseable mais hors sujet n'est plus sauvegardee comme
  match approximatif.
- Les donnees metadata Untappd ne sont appliquees que si le producteur/nom
  parse de la page ont un vrai recouvrement avec la fiche.

## v2.0.2 - 2026-05-14

**Match Untappd plus tolerant sur les millesimes absents.**

- Les URLs Untappd ne sont plus rejetees seulement parce que le millesime de
  la fiche n'apparait pas dans le slug.
- Le parser Untappd accepte un match parfait producteur + nom meme si la page
  Untappd n'expose pas de millesime dans son titre.
- Si l'OCR a mis une partie du nom de biere dans le champ producteur, le match
  peut quand meme etre reconnu et corriger producteur/nom depuis Untappd.

## v2.0.1 - 2026-05-14

**Bromelier et mise en page du chat.**

- Renommage des libelles visibles `Sommelier` vers `Bromelier`.
- Le prompt Claude demande maintenant des reponses sans Markdown visible,
  sans tableaux et sans emojis pour garder les bulles de chat propres.
- L'affichage retire aussi les marqueurs Markdown simples comme `**` et
  transforme les listes `-` en puces lisibles.

## v2.0.0 - 2026-05-14

**Sommelier IA integre.**

- Ajout d'un bouton `SOMMELIER` dans le menu principal.
- Nouvel ecran de prefiltre local avant conversation: type, couleur de vin,
  envie rapide et demande libre.
- Le prefiltre comprend aussi les intentions simples dans le texte, par
  exemple `vin blanc`, afin de reduire automatiquement la shortlist envoyee.
- Ajout d'un chat Claude via l'API Anthropic, avec Haiku 4.5 par defaut.
- Le prompt systeme force Claude a recommander seulement des bouteilles
  presentes dans la shortlist, avec l'ID exact de fiche.
- Nouvelle configuration `ANTHROPIC_API_KEY` et `ANTHROPIC_MODEL` dans
  `local.properties`.

## v1.3.30 - 2026-05-14

**Correction reset + match parfait Vivino.**

- La resolution Vivino conserve maintenant les donnees canoniques retournees
  par l'index de recherche Vivino, pas seulement l'URL de la fiche.
- Si le parsing HTML de la fiche Vivino revient incomplet apres un reset,
  l'app utilise ces donnees fiables en fallback pour remettre a jour le
  producteur et le nom lorsqu'un match parfait est retrouve.
- Le calcul du match parfait Vivino tient compte du producteur, du nom et du
  millesime exact quand Vivino expose ce millesime dans ses resultats.

## v1.3.29 - 2026-05-14

**Resolution Vivino sans lien de recherche.**

- La resolution Vivino utilise maintenant l'index Algolia public appele par la
  barre de recherche Vivino pour trouver de vraies fiches `/w/<id>`.
- Cas valide: `Podere Pradarolo Velius Rosato 2019` resout vers la fiche
  `https://www.vivino.com/en/podere-pradarolo-velius-rosato/w/7662365`.
- L'app ne sauvegarde plus une URL Vivino `/search/wines` comme lien final:
  si aucune fiche produit n'est validee, elle affiche `Aucun resultat`.

## v1.3.28 - 2026-05-14

**Capture photo plus fiable.**

- Le fichier cible de la camera est cree physiquement avant l'ouverture de
  l'appareil photo.
- Le chemin de la photo en cours est memorise temporairement dans les
  preferences de l'app pour survivre aux recreations d'ecran Android.
- Au retour de la camera, l'app attend brievement que le JPEG soit vraiment
  ecrit et stabilise avant de conclure a un echec.
- Evite les faux echecs ou la photo disparait apres avoir appuye sur OK dans
  l'appareil photo.

## v1.3.27 - 2026-05-14

**Bouton reset sur la fiche produit.**

- Le bouton en haut a droite d'une fiche produit ne relance plus l'ancienne
  indexation automatique.
- Il ouvre maintenant une confirmation puis efface les liens Vivino, Untappd
  et SAQ, les confirmations de match et les champs trouves automatiquement.
- Producteur, produit, millesime, photo, type et quantite restent conserves
  pour pouvoir relancer les recherches manuelles proprement.

## v1.3.26 - 2026-05-14

**Retour des corrections automatiques depuis la fiche source.**

- Quand une vraie fiche Untappd ou Vivino est resolue, l'app reparse maintenant
  la page source avant de fusionner les champs.
- Les noms et producteurs canoniques viennent donc de la fiche produit reelle,
  pas seulement du JSON propose par l'IA.
- Le calcul `PERFECT` accepte maintenant les producteurs abrégés et certains
  ecarts de nom bases sur les mots communs, par exemple `Cantillon` versus
  `Brasserie Cantillon`.

## v1.3.25 - 2026-05-14

**Correction Groq/Qwen pour Untappd.**

- Le prompt IA est plus compact et priorise seulement la source principale
  du produit courant: Untappd pour une biere, Vivino pour un vin.
- Pour `qwen/qwen3-32b` sur Groq, l'app envoie maintenant
  `reasoning_effort=none` afin d'eviter que le modele depense ses tokens en
  raisonnement avant de fermer le JSON.
- Le plafond de sortie JSON passe a 1200 tokens pour eviter l'erreur Groq
  `json_validate_failed`.

## v1.3.24 - 2026-05-14

**Badge de quota Groq dans Mon cellier.**

- Ajoute un petit badge `IA` a gauche du bouton `CSV` dans le menu principal.
- Le compteur utilise les headers Groq officiels
  `x-ratelimit-remaining-requests` et `x-ratelimit-remaining-tokens` quand ils
  sont presents.
- L'app memorise aussi les requetes Groq observees et les tokens `usage`
  retournes par l'API, incluant les appels echoues qui retournent quand meme
  des headers de rate-limit.

## v1.3.23 — 2026-05-14

**SAQ remplit aussi les champs de fiche.**

- Le resolver SAQ lit maintenant les attributs Live Search:
  `pays_origine`, `region_origine`, `cepage`, `appellation`,
  `designation_reglementee` et `pourcentage_alcool_par_volume`.
- Le bouton **Rechercher sur SAQ** peut donc remplir pays, region, cepages,
  style/appellation et alcool, en plus du lien produit direct.

## v1.3.22 — 2026-05-14

**Correction crash recherche SAQ manuelle.**

- La recherche SAQ directe s'execute maintenant sur `Dispatchers.IO`.
- Corrige le `NetworkOnMainThreadException` quand on lance
  **Rechercher sur SAQ** depuis la fiche produit.

## v1.3.21 — 2026-05-14

**Recherche SAQ strictement fiche produit.**

- Le bouton **Rechercher sur SAQ** ne passe plus par le flux IA general.
- La recherche SAQ appelle directement Adobe Live Search et stocke seulement
  une vraie fiche produit `/fr/<code>`.
- Si aucune fiche SAQ n'est resolue, l'app affiche **Aucun resultat** au lieu
  de stocker une URL `/catalogsearch/result`.

## v1.3.20 — 2026-05-14

**Resolution SAQ vers une vraie fiche produit.**

- La recherche SAQ manuelle utilise maintenant Adobe Live Search, l'endpoint
  GraphQL public utilise par SAQ.com, pour trouver le premier produit pertinent.
- Les URL SAQ `/catalogsearch/result` ne sont plus acceptees comme lien final.
- Une URL SAQ valide doit maintenant etre une vraie fiche produit, par exemple
  `https://www.saq.com/fr/15344631`.
- Cas valide: `Gaja Barbaresco 2022` resout vers `https://www.saq.com/fr/15344631`.

## v1.3.19 — 2026-05-14

**Z.AI retire du chemin recommande et resolution Untappd plus robuste.**

- Le fallback par defaut devient `groq,gemini`; Z.AI reste disponible seulement
  si `AI_PROVIDER=zai` est demande explicitement.
- Le README recommande maintenant `AI_PROVIDER=groq` pendant la stabilisation.
- Si un fournisseur IA retourne `found=false`, l'app tente quand meme la
  resolution directe Untappd/Vivino avant de conclure a un vrai aucun resultat.
- La resolution produit n'est lancee que pour la source principale du type
  courant, ce qui evite des appels reseau inutiles.

## v1.3.18 — 2026-05-14

**Resolution des recherches Untappd/Vivino vers une fiche produit.**

- Si le fournisseur IA ne donne pas une URL produit valide, l'app ouvre la
  recherche directe Untappd/Vivino et tente d'extraire le premier vrai lien
  produit pertinent.
- Les candidats sont encore valides localement contre le producteur, le nom et
  le millesime avant d'etre stockes.
- Si aucune fiche produit pertinente n'est trouvee, l'app garde le lien de
  recherche comme solution de secours.

## v1.3.17 — 2026-05-14

**Liens de recherche traites comme recherches proposees.**

- Une URL `/search` Vivino ou Untappd est maintenant affichee comme
  **Rechercher sur...** plutot que **Voir sur...**.
- Les pages de recherche n'affichent plus le checkmark de confirmation, afin
  d'eviter de marquer une recherche comme match parfait.

## v1.3.16 — 2026-05-14

**Garde-fou contre les URL hallucinees par les LLM.**

- Les URL Vivino/Untappd proposees par un fournisseur IA sont maintenant
  validees contre le slug: producteur, mots importants du nom et millesime
  doivent correspondre.
- Rejet des IDs placeholder probables, par exemple `123456`.
- Une fiche ne peut plus obtenir un match `PERFECT` si l'URL primaire n'a pas
  ete validee localement.
- Le prompt IA demande explicitement de retourner `null` plutot qu'une URL
  incertaine.

## v1.3.15 — 2026-05-14

**Erreur Z.AI surchargee distincte du quota.**

- Le `429` Z.AI `code=1305` est maintenant classe comme
  `PROVIDER_BUSY` plutot que `RATE_LIMIT`.
- La fiche affiche **Fournisseur IA surcharge** pour ce cas precis.
- Les recherches manuelles affichent aussi un toast dedie quand le fournisseur
  IA est temporairement surcharge.

## v1.3.14 — 2026-05-14

**Indexation IA multi-fournisseur sans retry automatique.**

- Ajout de `AI_PROVIDER` dans `local.properties` pour choisir `zai`, `groq`,
  `gemini`, ou une liste ordonnee comme `zai,groq,gemini`.
- Ajout des cles/modeles `ZAI_API_KEY`, `ZAI_MODEL`, `GROQ_API_KEY`,
  `GROQ_MODEL`, `GEMINI_MODEL`.
- `ProductIndexer` supporte maintenant Z.AI GLM, Groq et Gemini avec une
  sortie JSON stricte.
- WorkManager ne relance plus automatiquement une indexation rate-limitee ou
  en erreur reseau. La fiche affiche l'erreur et attend un retry manuel.
- Le modele Gemini n'est plus hardcode: valeur par defaut
  `gemini-2.5-flash-lite`.

## v1.3.13 — 2026-05-13

**Correction prise de photo.**

- Le chemin de la photo en cours est conserve pendant l'ouverture de l'app
  camera, meme si Android recree l'ecran.
- Au retour de l'app camera, la photo est acceptee si le fichier existe
  reellement, meme si le contrat camera retourne un succes ambigu.
- Ajout d'un court message si l'app camera revient sans fichier photo ecrit.

## v1.3.12 — 2026-05-13

**Message plus clair pour les blocages reseau masques.**

- Le message `NETWORK` indique maintenant **Blocage possible ou erreur reseau**
  au lieu de laisser croire que seule la connexion locale est en cause.
- Le hint suggere de reessayer plus tard ou de changer de reseau, utile quand
  Bing/Vivino bloque sans retourner de page de rate-limit explicite.

## v1.3.11 — 2026-05-13

**Distinguer rate-limit et vrai "aucun resultat".**

- L'indexation remonte maintenant une cause d'echec: `NO_RESULT`,
  `RATE_LIMIT` ou `NETWORK`.
- La fiche affiche **Recherche temporairement limitee** quand DDG/Bing semble
  bloquer les requetes, plutot que **Aucun resultat trouve**.
- Les recherches manuelles affichent aussi un toast different pour rate-limit
  ou erreur reseau.
- Ajout d'une migration Room v5 -> v6 pour stocker `syncFailureReason`.
- Les miss temporaires rate-limites ne sont plus caches comme de vrais
  "aucun resultat".

## v1.3.10 — 2026-05-13

**Fallback Vivino quand le millésime exact n'existe pas.**

- Les recherches Vivino avec millésime essaient maintenant aussi des variantes
  sans millésime plus tôt dans le fallback Bing.
- Ajout de requêtes Vivino entre guillemets pour les cas comme
  **Jean François Ganevat Vin Jaune 2012**.
- L'extraction lit aussi les URL Vivino/Untappd présentes dans le HTML brut des
  résultats, pas seulement les liens `<a href>`.
- Le choix final privilégie les vraies pages produit Vivino `/w/<id>` et
  Untappd `/b/<slug>/<id>`.

## v1.3.9 — 2026-05-13

**Protection contre les anciens liens de cache invalides.**

- Les recherches Vivino/Untappd ignorent maintenant un hit de cache dont l'URL
  ne pointe pas réellement vers le domaine attendu.
- Corrige les cas où une ancienne version avait pu cacher une URL de recherche
  Bing/DDG contenant `untappd.com` dans ses paramètres, sans être une vraie page
  Untappd.
- Cas visé : les recherches Untappd à nom très court comme
  **Hill Farmstead E. 2018**.

## v1.3.8 — 2026-05-13

**Recherche Untappd plus tolérante pour les bières datées.**

- Le fallback Bing essaie maintenant aussi les dates Untappd avec tirets et
  espaces, par exemple `08/11/2021`, `08-11-2021` et `08 11 2021`.
- L'extraction des résultats ne retient plus les liens Bing/DDG qui contiennent
  seulement `untappd.com` dans leurs paramètres de recherche; l'hôte réel doit
  être `untappd.com`.
- L'analyse des liens de recherche filtre d'abord les vrais candidats du bon
  domaine, puis limite la liste, ce qui évite de manquer un résultat placé plus
  bas dans la page.
- Les logs `ProductIndexer` affichent maintenant un petit échantillon des URL
  candidates pour aider à diagnostiquer les prochains cas limites.

## v1.3.7 — 2026-05-13

**Fallback de recherche quand DDG Lite renvoie une page vide/générique.**

- Ajout d'un fallback Bing pour Vivino et Untappd quand DDG Lite répond sans
  candidat exploitable.
- Les requêtes de fallback utilisent une recherche naturelle
  `producteur + nom + source`, plus fiable pour des cas comme
  **Jean Bourdy Château-Chalon**.
- Décodage des liens de recherche DDG et Bing, y compris certains liens Bing
  redirigés, avant de sélectionner le premier résultat du bon domaine.
- Les anciens "miss" cachés continuent d'être ignorés pendant les recherches
  automatiques et manuelles, afin qu'un échec temporaire ne bloque pas la fiche.

## v1.3.6 — 2026-05-13

**Feedback quand Untappd ne trouve rien.**

- Une indexation automatique sans résultat sur la source principale n'est plus
  considérée comme un succès silencieux.
- Pour une bière sans lien Untappd, la fiche affiche maintenant une bannière
  **Aucun résultat trouvé** avec action **Réessayer**.
- Les recherches automatiques réessaient les anciens "miss" DDG au lieu de
  rester bloquées par un échec temporaire mis en cache.
- Les lignes **Rechercher sur Vivino** et **Rechercher sur Untappd** sont
  affichées dès que le lien correspondant est absent, même si c'est la source
  principale du type de produit.

## v1.3.5 — 2026-04-27

**Repères visuels OCR.**

- Le dialogue OCR reste ouvert après avoir appliqué le texte reconnu à
  Producteur, Nom ou Millésime.
- Chaque champ déjà rempli affiche un petit checkmark bourgogne dans la rangée
  de boutons OCR, pour voir rapidement ce qu'il reste à surligner.

## v1.3.4 — 2026-04-27

**OCR local + indicateur de match parfait dans Mon cellier.**

- **Mon cellier** : ajout d'un petit checkmark vert à droite du nom dans les
  cartes horizontales quand la fiche a un match parfait sur sa source principale
  (Vivino pour un vin, Untappd pour une bière).
- **Nouvelle fiche** : ajout de **Lire l'étiquette** après la prise de photo.
  L'écran OCR affiche la photo, permet de surligner librement une zone de texte
  et lance la reconnaissance sur la bounding box du tracé.
- **OCR local ML Kit** : utilisation de `com.google.mlkit:text-recognition`
  avec le modèle Latin embarqué. Aucun serveur OCR externe n'est appelé.
- **Remplissage assisté** : le texte reconnu peut être appliqué à Producteur,
  Nom ou Millésime. Le champ Millésime extrait automatiquement une année
  `19xx` ou `20xx` si elle est présente.

## v1.3.3 — 2026-04-27

**Correctif v1.3.2 : compilation + recherches manuelles.**

- **Correction compilation** : `ProductIndexer.kt` contenait deux versions du
  scraper collées dans le même fichier, ce qui causait l'erreur Kotlin
  `Expecting a top level declaration`.
- **DDG Lite corrigé** : passage à `https://lite.duckduckgo.com/lite/` en GET,
  avec `Referer`, `DNT` et langue `fr-CA`.
- **Recherches manuelles réparées** : les lignes `Rechercher sur SAQ/Vivino/Untappd`
  sont maintenant cliquables sur toute la largeur, pas seulement sur la petite
  icône à droite.
- **Feedback utilisateur** : pendant la recherche, un spinner s'affiche; après
  la recherche, un message indique si un lien a été ajouté ou si aucun résultat
  n'a été trouvé.
- **Cache plus prudent** : les recherches manuelles ignorent les anciens "miss"
  DDG cachés afin qu'un essai raté/rate-limité ne bloque pas le bouton pendant
  24 heures.

## v1.3.2 — 2026-04-27

**Anti-rate-limit DDG : cache + DDG Lite + requêtes réduites + loupes manuelles.**

### Stratégie anti-rate-limit
- **Cache de requêtes** (Room v4→v5, table `search_cache`) : chaque URL trouvée
  via DDG est cachée 7 jours. Les miss (0 résultat) sont cachés 24h. Après
  la première session d'indexation, les re-indexations ne contactent plus DDG
  du tout pour les produits déjà vus.
- **DDG Lite** (`duckduckgo.com/lite/`) au lieu de `html.duckduckgo.com/html/`.
  Version minimaliste tolérée plus permissive. Headers `Referer` + `DNT: 1`
  ajoutés pour imiter un vrai browser.
- **1 requête DDG max** par fiche (source primaire seulement) au lieu de 6 :
  VIN → Vivino uniquement, BIÈRE → Untappd uniquement. SAQ est maintenant
  recherche manuelle optionnelle depuis la fiche.
- **Jitter backoff** : délai aléatoire 2-6s avant fallback sans-year.

### Recherches manuelles optionnelles (🔍 dans la fiche)
Chaque fiche affiche maintenant des loupes pour les sources non-indexées :
- VIN : loupe 🔍 SAQ + loupe 🔍 Untappd (Vivino est automatique)
- BIÈRE : loupe 🔍 SAQ + loupe 🔍 Vivino (Untappd est automatique)
Pendant la recherche : spinner ⟳ à la place de la loupe. Quand trouvé : le
lien apparaît avec le badge match qualité.

### Bouton ↻ Refresh
Nouveau bouton Refresh dans la TopBar de la fiche (à gauche du ✏️ édition).
Relance l'indexation de la source primaire seulement (Vivino ou Untappd).
Inutile de supprimer et recréer une fiche pour relancer le scraping.

## v1.3.1 — 2026-04-26

**Polissage UI + export CSV.**

- **Cards horizontales** plus compactes : 96dp → 84dp. Padding équilibré
  haut/bas via `Arrangement.Center` au lieu de spacing fixe — les textes
  sont centrés verticalement peu importe qu'il y ait 3 ou 4 lignes (avec
  ou sans style/AOC).
- **Export CSV** : nouveau bouton "CSV" en haut à droite de la TopBar de
  Mon Cellier (encadré gris pâle, surface variant). Génère un fichier CSV
  trié par nom de produit (insensible à la casse), avec les colonnes :
  Nom, Producteur, Type, Millésime, Couleur, Quantité, Pays, Région,
  Cépages, Style/AOC, Alcool, IBU, Lien SAQ, Lien Vivino, Lien Untappd,
  Date d'ajout. Format RFC 4180 (champs avec virgules entourés de `"`,
  guillemets internes doublés). BOM UTF-8 pour qu'Excel gère les accents.
  Partage via FileProvider + chooser Android.
- **Logging amélioré** : `ProductIndexer.index()` log maintenant le démarrage
  d'une indexation avec ID, type, producteur, nom et millésime — utile pour
  diagnostiquer une indexation qui ne se déclenche pas du tout.

## v1.3.0 — 2026-04-26

**Refonte UI : thème dynamique + cards horizontales + couleur de vin.**

- **Thème dynamique** : nouveau paramètre `beverageType` sur `CellierManagerTheme`
  qui swap les couleurs primary entre bourgogne (vins) et ambre/jaune (bières).
  AjouterScreen et FicheProduitScreen sont wrappés pour réagir au type sélectionné.
  Le listing reste neutre (bourgogne par défaut). Au top des écrans concernés :
  toggle Vin/Bière dans Ajouter → bascule du thème en temps réel ; ouvrir une
  fiche bière → tout le détail passe en jaune.
- **Cards horizontales** dans le listing : `LazyVerticalGrid` (cards carrées 2x2)
  remplacé par `LazyColumn` avec cards 96dp de haut. Photo carrée à gauche,
  textes à droite (produit, producteur, style, année + pastille couleur).
  ~5-6 fiches visibles en simultané sur S23 Ultra.
- **Couleur de vin** : nouveau enum `WineColor` (ROUGE/BLANC/ORANGE/ROSE) +
  champ `wineColor: WineColor?` sur `CellarItem`. Saisie manuelle via segmented
  button dans AjouterScreen et le mode édition de la fiche. Affichée comme
  pastille ronde 10dp/12dp à côté du millésime, dans le listing et la fiche.
  Migration Room v3→v4 non-destructive (`ALTER TABLE ADD COLUMN wineColor`).
- **Search dans les filtres** (FilterPickerSheet) : nouveau champ search en haut,
  filtrage `startsWith` insensible aux accents/casse. L'option "Effacer" est
  retirée (tap-outside fait pareil).
- Couleurs ajoutées dans la palette : `WineColorRouge` (#722F37), `WineColorBlanc`
  (#F4E4BC), `WineColorOrange` (#D97706), `WineColorRose` (#F4A6B8).

## v1.2.2 — 2026-04-25

**Fix SAQ : recherche via DDG + sélecteurs HTML mis à jour.**

- `searchSaq` ne passe plus par le moteur interne `catalogsearch/result/` de SAQ
  (qui ne trouve pas les produits de niche comme les vins du Québec). Passe
  désormais par DDG ciblé sur `site:saq.com/fr` avec filtre URL produit
  `/fr/<6-9 digits>`. Bénéficie de la logique `preferYearInPath` (v1.2.1).
- `parseSaqProduct` : sélecteurs `[data-th="..."]` génériques au lieu de
  `span[data-th="..."]`. SAQ utilise maintenant `<strong>` au lieu de `<span>`,
  ce qui faisait échouer silencieusement l'extraction de Pays/Région/Cépages
  même quand le produit était trouvé. Le sélecteur sans tag matche les deux.
- Nettoyage des cépages : `"Cayuga&74&%, Frontenac blanc&10&%"` → `["Cayuga",
  "Frontenac blanc"]` (entities mal échappées + suppression des pourcentages).
- Logging détaillé : nombre de candidats DDG retournés, URL retenue,
  champs SAQ extraits.

## v1.2.1 — 2026-04-25

**Fix Untappd : ciblage du millésime + préfixe brewery dans le nom.**

- `ProductIndexer.searchViaDuckDuckGo` : pour Untappd (et bonus pour Vivino),
  prend désormais 5 résultats DDG au lieu de 3 et **privilégie les URLs dont le
  path contient le millésime utilisateur** (e.g. `/peche-mortel-bourbon-2020`
  vs `/peche-mortel-bourbon-2025`). Évite que DDG renvoie la mauvaise année
  par défaut.
- `UntappdParser` : nouveau `stripBreweryPrefix` enlève le préfixe brewery du
  nom quand le ld+json contient `"Brasserie Dieu du Ciel! Péché Mortel Bourbon"`
  → ne garde que `"Péché Mortel Bourbon"`. Matching robuste aux accents et casse.
- L'extraction du producteur se fait maintenant AVANT celle du nom, pour pouvoir
  utiliser le brewery comme préfixe à stripper.

## v1.2.0 — 2026-04-25

**Scraping Untappd avec match quality (parallèle à Vivino).**

- Nouveau `UntappdParser.kt` (parser isolé pour les bières). Stratégie hybride :
  - **ld+json** (fiable) : nom canonique avec préfixe brewery + brand canonique
  - **HTML structured** : `<p class="abv">9.5% ABV</p>`, `<p class="ibu">42 IBU</p>`,
    `<p class="style">...</p>`, `<div class="name"><h1>...</h1></div>`
  - **meta keywords** : pays + région (anglais) en bonus
- `UntappdData` : producteur canonique, nom canonique, année (extraite de `(YYYY)`),
  style, ABV, IBU, pays, région, qualité du match.
- `CellarItem` : ajout de `ibu: Int?` et `untappdMatchQuality: MatchQuality?`.
- **Migration Room v2 → v3 NON destructive** : `ALTER TABLE` ajoute les nouvelles
  colonnes. Tes fiches existantes sont préservées.
- `IndexingWorker.mergeIndexResult` : règles unifiées
  (Vivino canonique pour vins, Untappd canonique pour bières).
- `CellarRepository.confirmUntappdMatch` : symétrique à `confirmVivinoMatch`,
  applique les données canoniques d'Untappd.
- Composant UI `MatchableLinkRow` : remplace `VivinoLinkRow`. Sert maintenant
  aux deux sources avec badge couleur (vert/jaune) + ✓ confirmation.
- Section bière de la fiche : ajout de IBU dans l'affichage.
- Formulaire d'édition : ajout des champs manquants
  (style+alcool pour vins, IBU pour bières).

## v1.1.4 — 2026-04-25

**Fix UI : style/AOC + ABV pour les vins.**

- `DetailsSection` (FicheProduitScreen) : style et alcoolVolume étaient affichés
  uniquement pour les bières (héritage v1.0). Ajoutés à la section vin.
- Ordre logique d'affichage pour les vins : Pays → Région → Style/AOC → Cépages → Alcool.
- `DetailRow` refactoré pour gérer les valeurs longues (ex. liste de cépages) :
  alignement top + valeur prend l'espace restant et wrap si besoin.
- `DetailRowMultiline` n'est plus utilisé pour les cépages (mise en page incohérente
  avec les autres rows).

## v1.1.3 — 2026-04-25

**Extraction des cépages depuis Vivino.**

- `VivinoParser.extractGrapes()` : nouveau, deux stratégies en cascade :
  1. JSON `"grapes":[{"id":N,"name":"X"},...]` du blob `__PRELOADED_STATE__`
     (cépages typiques du style/AOC, e.g. Bordeaux Saint-Émilion → Cabernet Franc + Merlot)
  2. Fallback texte : `"... is a Red wine. Made from X, Y, Z."` (description marketing)
- `VivinoData.grapes: List<String>` ajouté au modèle.
- `IndexingWorker.mergeIndexResult` : priorité grapes existant > SAQ > Vivino.
  Le fallback Vivino évite les vins sans cépages quand la SAQ ne les fournit pas.
- `CellarRepository.confirmVivinoMatch` : remplit aussi les cépages si vides.

## v1.1.2 — 2026-04-25

**Fix scraping Vivino — strategy pivot.**

- `VivinoParser` : abandon complet de l'approche chunk + unescape (trop fragile : le
  marker `\"alcohol\"` escaped n'est pas dans toutes les versions du HTML servi par
  Vivino selon User-Agent / CDN).
- Nouvelle stratégie : regex directes sur le HTML brut, anchored sur la structure
  JSON de `window.__PRELOADED_STATE__` qui contient les données du vin en form
  non-escaped.
- Patterns clés validés sur HTML réel de 2.4 MB :
  - `"wine_facts":{"alcohol":14}` → ABV vintage-spécifique
  - `"region":{"id":...,"name":"..."}` → région
  - `"style":{"id":...,...,"name":"..."}` → AOC / wine style
  - `"vintage":{"id":...,"seo_name":"...-2022","name":"..."}` → année + nom complet
- `[^{]*?` (non-greedy, exclut les `{`) au lieu de `[^}]*?` pour gérer les objets
  imbriqués (e.g. `region.country` est un objet, pas une string).
- Logging détaillé par champ pour faciliter futur debug.

## v1.1.1 — 2026-04-25

- `VivinoParser` ajoute automatiquement `?year=YYYY` à l'URL si manquant. DDG
  retourne souvent l'URL canonique sans le year, ce qui faisait servir une page
  "wine overview" sans données vintage.
- `IndexingWorker.mergeIndexResult` : si match APPROX, Vivino remplit maintenant
  les champs vides (region/style/alcohol) au lieu de les jeter. Priorité par champ :
  PERFECT Vivino > existant > SAQ > APPROX Vivino.

## v1.1 — 2026-04-25

**Première itération du scraping Vivino + UI match quality.**

- Nouveau `VivinoParser.kt` (parser isolé pour ne pas casser le scraping SAQ).
- `MatchQuality` enum (PERFECT/APPROX) ajouté à `CellarItem`.
- Room DB v1 → v2 (destructive migration en dev).
- UI fiche : badge coloré "Match parfait" (vert) / "Match approximatif" (jaune)
  + icône ✓ cliquable pour confirmer manuellement un match APPROX.
- Action `confirmVivinoMatch` dans le repo : refetch et applique les données
  canoniques de Vivino (producteur, nom, région, style, ABV).
- Autocomplete producteur désactivé dans Ajouter (à revoir plus tard).

## v1.0 — 2026-04

Version initiale (cf README).
# 2.1.0-dev — refonte compagnon Windows + Chrome

- Remplace l’indexation directe et les contournements de filtres par une capture volontaire de la page active dans Chrome.
- Ajoute le compagnon Windows local, son protocole persistant, le jumelage à usage unique et le transport TLS vers Android.
- Ajoute la file de recherches, la comparaison champ par champ, la provenance et les protections contre les réponses tardives.
- Migre Room vers le schéma 7 sans migration destructive et conserve l’ancien inventaire.
- Ajoute les fiches épuisées et les sauvegardes complètes `.cellierbackup` avec photos et validation d’intégrité.
- Retire les clés d’indexation du BuildConfig. La clé facultative de Bromelier est maintenant saisie dans les réglages et chiffrée par Android Keystore.
