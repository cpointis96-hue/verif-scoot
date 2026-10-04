# Reconstruction pour le portfolio

Contrôles du 4 octobre 2026, sur une copie isolée des sources. L’historique Git local d’origine est conservé. Les journaux, caches, SDK, données de l’émulateur, keystores et builds restent hors des sources publiables.

Environnement : macOS Apple Silicon, JDK 21, Gradle Wrapper 8.13, SDK Android 35 et émulateur ARM64 dédié. Le SDK et le cache Gradle déjà présents sur la machine ont été utilisés ; ils ne sont pas inclus dans le dépôt.

| Vérification | Résultat |
|---|---|
| `scripts/build.sh` | Réussi : tests JVM, lint, APK debug et APK des tests |
| Tests JVM | 4 cas, 0 erreur, 0 échec |
| Lint | 0 erreur, 21 avertissements |
| `scripts/test-emulator.sh` | Réussi : `OK (10 tests)`, 27,816 secondes pour l’instrumentation |
| Capture CameraX | Test réussi avec une mire vidéo fournie à la caméra émulée |
| Import système | Test réussi par le sélecteur Android |
| Inspection, revue, clôture et réouverture | Parcours instrumenté réussi |
| Pipeline et export | Tests de comparaison synthétique, stockage, intégrité et export réussis |
| Signature de l’APK | `apksigner verify --verbose` réussit ; signature v2 valide |
| Taille de l’APK reconstruit | 159 127 405 octets, environ 151,8 MiB |

SHA-256 de cet APK :

```text
436bac684b50cac5cbb77e8fca6dc3998ac0fff506d56d75bc22633fcfcca015
```

Les captures `docs/screenshots/capture.png`, `review.png` et `closed.png` proviennent de ces tests. Les médias sont synthétiques. La capture automatique prise immédiatement après le dernier lancement montrait encore l’écran d’accueil de l’émulateur pendant la transition : elle n’est pas utilisée dans la notice. L’émulateur n’était plus connecté après la fin du processus de test.

Le document `VALIDATION.md` conserve les résultats de la construction antérieure. Cette reconstruction ne reproduit pas son essai de police agrandie et ne prouve pas un fonctionnement sur téléphone physique. Aucun corpus réel, qualité de détection terrain, chauffe, autonomie, refus de permissions ou distribution Play n’a été validé dans cette préparation.

L’APK est un build debug pour essais, pas une release commerciale. La préparation ne transmet aucun rapport ni média à un tiers.
