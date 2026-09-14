# Provenance et dépendances

Les composants AndroidX/Compose/CameraX proviennent de Google Maven ; Kotlin et OpenCV de Maven Central. Le wrapper Gradle est généré par la distribution Gradle officielle. Aucun modèle ni dataset de dommages téléchargé.

OpenCV 4.13.0 : bibliothèque de vision, [licence Apache 2.0 et détails tiers](https://github.com/opencv/opencv/blob/4.13.0/LICENSE). Les bibliothèques natives et leurs notices transitives restent celles de la distribution officielle. Avant publication commerciale, conserver l’inventaire et toutes les notices correspondantes dans le paquet de distribution.

Fixtures AndroidTest : vidéo créée localement avec `ffmpeg -f lavfi -i testsrc2=size=640x480:rate=10:duration=2 -c:v libx264 -pix_fmt yuv420p fixture.mp4`. C’est une mire synthétique, pas un scooter et pas une donnée client. Textures de tests créées par Canvas Android avec graine fixe. Aucune fixture ne mesure la précision sur dommages physiques.

CarDD, SAM2, LightGlue, DINOv2 et modèles 3D sont étudiés dans le blueprint, mais ne sont ni téléchargés ni utilisés dans le code. Aucun appel payant ni envoi externe de médias n’a été effectué.
