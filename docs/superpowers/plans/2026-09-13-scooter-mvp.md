# Scooter MVP0 Implementation Plan

> **For agentic workers:** Exécuter les tâches et critères ci-dessous dans l’ordre ; la mission autorise leur exécution dans cette session. Ne pas substituer une simulation à une validation Android.

**Goal:** Obtenir un APK Android de preuve technique capable de conserver et comparer départ/retour par face.

**Architecture:** Module Android Kotlin/Compose, CameraX, SQLite privé, extraction MediaMetadataRetriever, OpenCV local. Les captures et les imports convergent vers le même pipeline. Les alertes restent expérimentales et la clôture exige une revue.

**Tech Stack:** AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, compile/target SDK 35, min SDK 27, Compose BOM 2025.04.01, CameraX 1.4.2, OpenCV 4.13.0.

**Spec:** `docs/BLUEPRINT.md`

## Global Constraints

Android uniquement ; aucune API payante ; aucun entraînement ; aucune conclusion automatique de dommage. Originaux conservés ; import explicitement identifié ; aucune synchronisation prétendue. Dépôt initial vide, sans commit : travail dans le dossier fourni, branche de travail si possible, pas de duplication par worktree sans bénéfice.

### Tâche 1 : build minimal

Créer settings.gradle.kts, build.gradle.kts, app/build.gradle.kts, manifeste, MainActivity.kt et wrapper officiel. Interfaces : activité exportée `com.scootcheck.app.MainActivity`.

- [x] Épingler dépendances et configurer SDK local hors Git.
- [x] Exécuter `./gradlew assembleDebug` ; attendre APK valide avant pipeline.

### Tâche 2 : inspections durables

Créer InspectionStore.kt. Tables scooters, rentals, media, reviews. Une location par scooter ouverte, quatre faces, origine camera/import. Interface `createScooter(label): Long`, `openRental(scooterId): Long`, `saveMedia(rentalId, kind, face, file, frame, source)`, `closeRental(id)`.

- [x] Test : deux appels openRental renvoient le même ID tant que non clôturée ; nouveau après clôture ; double identifiant refusé.
- [x] Tester que closeRental échoue sans départ/retour et revue complets.
- [x] Transaction médias après finalisation fichiers ; reprendre faces déjà présentes après recréation store.

### Tâche 3 : acquisition et sélection

Créer CaptureScreen.kt et VideoFrames.kt. `extract(video: File, output: File): File` choisit la plus nette de cinq frames, garde ratio et original. CameraX Preview + Recorder ; import via GetContent ; thread IO, feedback erreur.

- [x] Test vidéo fixture 2 s : image décodable produite, SHA original identique.
- [x] Test vidéo invalide : erreur compréhensible, aucune face marquée complète.
- [x] Enregistrement réel CameraX sur source émulée, retour écran et reprise après recréation vérifiés.
- [ ] Refus de permission et interruption système pendant enregistrement : vérification manuelle sur téléphone restant à faire.

### Tâche 4 : comparaison

Créer VisualComparator.kt. `compare(before: Bitmap, after: Bitmap): Comparison(status, boxes, reason)` ; ORB + RANSAC ; seuils conservateurs et recouvrement ; OpenCV CPU.

- [x] Identique texturé → aucune région ; uniforme → non comparable ; patch modifié → suggestion ; scène différente → non comparable.
- [x] Ne jamais renvoyer une probabilité de dommage. Limite visible dans écran/rapport.

### Tâche 5 : parcours et export

MainActivity : liste, fiche location, capture et revue par face. EvidenceExport : `export(rentalId): File` ZIP privé partageable FileProvider, manifeste JSON et images/vidéos.

- [x] Parcours UI : créer scooter, huit médias injectés via le pipeline instrumenté ; sélecteur d’import testé séparément, revue, clôture, historique.
- [x] Export : ZIP contient huit originaux et hashes recomputables, statut des revues et origine.
- [x] Blocage clôture sans revue ; décisions survivent à relancement.

### Tâche 6 : vérification et livraison

Créer scripts de build et smoke AVD, README et docs/VALIDATION.md.

- [x] `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
- [x] `./gradlew connectedDebugAndroidTest` sur AVD dédié ; screenshots et logs dans artifacts.
- [x] Vérifier signature APK avec apksigner, copier sous artifacts, calculer SHA-256.
- [x] Documenter limites, tests réels non faits et procédure appareil physique.

## Acceptation

Build effectif et états persistants vérifiés. Le succès logiciel ne valide ni précision sur scooters ni gain économique. Aucun corpus réel fourni : conserver cette lacune comme critère du pilote, pas comme motif d’inventer une performance.

## Ajustements constatés

OpenCV 4.13.0 remplace 4.12.0. Le rééchantillonnage projectif est assuré par PerspectiveWarp.kt, testé séparément : warpPerspective natif provoquait un SIGILL sur cet émulateur ARM64 avec les deux versions. ORB et homographie restent dans OpenCV. Les tests finaux sont lancés directement avec am instrument pour conserver les captures ; connectedDebugAndroidTest a aussi été exécuté auparavant. Les résultats définitifs et réserves figurent dans docs/VALIDATION.md.
