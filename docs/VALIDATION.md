# Validation du MVP0

Le livrable est une preuve technique Android locale. Aucune précision de détection sur scooters réels n’est établie.

## Environnement et preuves

Android 15 / API 35, émulateur ARM64 dédié `emulator-5554`, écran 320 × 640. JDK 21, Gradle 8.13, AGP 8.13.2. Les versions sont épinglées dans le dépôt. Les journaux et captures sont conservés dans `artifacts/`, hors Git.

- `final-build.log` : compilation debug, tests JVM, lint et APK des tests. Lint : 0 erreur et 21 avertissements, notamment de versions/cibles et conventions Android ; la compilation réussie ne signifie pas absence d’avertissements.
- Tests JVM : 4 cas réussis, SHA-256 et projection bilinéaire (identité, translation, matrice singulière).
- Tests Android : 10 cas réussis (`OK (10 tests)`, 32,685 s), dont acquisition CameraX, import par le sélecteur système, revue/clôture/historique, comparaison et stockage/export.
- `font-scale-tests.log` : deux parcours réussis sur l’APK final (`OK (2 tests)`, 23,804 s) avec police 130 % et mode nuit système. L’application conserve sa palette claire.
- `capture.png`, `review.png`, `closed.png`, `picker.png`, `export.png` : captures de l’application réellement exécutée. Les variantes `*-large.png` montrent le texte agrandi.

La caméra émulée reçoit une mire vidéo synthétique. Il s’agit bien d’un enregistrement CameraX, mais pas d’une caméra physique ni d’un scooter. Le parcours complet de revue utilise huit vidéos ajoutées par le pipeline instrumenté ; l’acquisition et le sélecteur sont exercés dans des tests distincts. Aucun envoi de rapport à un tiers n’est effectué.

## Ce que les tests contrôlent

Les scènes synthétiques identiques et légèrement translatées restent sans suggestion ; une modification locale est proposée ; les images uniformes et les scènes différentes déclenchent l’abstention. Ces résultats contrôlent des cas déterministes, sans mesurer le rappel des rayures ou le taux de faux positifs terrain.

La base conserve les faces et décisions après réouverture. Une location incomplète ne peut pas être clôturée. Les identifiants dupliqués et les faces déjà conservées sont refusés. Une vidéo corrompue ne produit pas de capture validée. L’export contient les huit originaux et les revues ; les hashes sont recomputés et une altération d’original bloque l’export.

## Corrections vérifiées pendant la construction

- Correction d’index de lecture SQLite.
- Remplacement du rééchantillonnage projectif natif : `warpPerspective` provoquait un SIGILL sur cet émulateur ARM64, avec OpenCV 4.12 et 4.13. `PerspectiveWarp.kt` effectue cette étape en Kotlin ; ORB et l’estimation d’homographie restent dans OpenCV.
- Attente de la fin effective de l’analyse dans les tests UI.
- Aperçu caméra limité à sa zone via TextureView ; remise en haut à chaque face.
- Comparaison avant/après côte à côte, export principal après clôture, titre long tronqué.
- Icônes des barres système adaptées à la palette claire même lorsque le système est en mode sombre.

## Limites et validation terrain restante

Aucun téléphone physique ni corpus réel fourni. Restent à vérifier sur l’appareil cible : autorisation refusée, appels et interruptions système pendant capture, autonomie, chauffe, vitesse, reflets, faible lumière, flou, petits défauts, différences d’angle et masquages. Le protocole pilote et ses métriques sont détaillés dans `BLUEPRINT.md`.

L’APK debug universel inclut plusieurs architectures natives et pèse environ 203 Mo. Il est destiné à l’essai local, sans configuration de publication Play ni clé commerciale. La base et les médias sont privés au téléphone ; leur désinstallation les efface. Le ZIP constitue un export manuel, pas une sauvegarde distante automatique, une preuve horodatée certifiée ou une expertise de responsabilité.

## Livraison

APK : `artifacts/verif-scoot-debug.apk`. Signature APK v2 vérifiée par apksigner ; empreinte validée via `shasum -a 256 -c artifacts/verif-scoot-debug.apk.sha256`. Le sélecteur de partage système a été ouvert et le nom du ZIP vérifié, puis refermé sans transmission. Le build, la suite Android et les extractions de captures ont terminé avec succès via les scripts du dépôt.

Revue UI indépendante : comparaison côte à côte, export principal, titres longs et contraste système réexaminés sur les captures finales ; aucun point ouvert dans ce périmètre. Ce contrôle ne remplace pas un essai terrain.
