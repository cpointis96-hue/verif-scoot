# Contrat de revue du MVP0 Android

Autorité : la mission demande des composants standards, un outil terrain extrêmement simple, peu de boutons et une construction autonome. Le choix de stack et de parcours est explicitement délégué à l’agent.

THESIS : retrouver les médias d’une même face avant et après la location.
OWN-WORLD : application Android Material 3 claire, un accent bleu, médias dominants, aucun décor marketing.
STORY : choisir scooter, capturer quatre faces, retrouver les deux états, revue humaine et export.
FIRST VIEWPORT : action et face courante visibles ; à la comparaison, les deux images visibles ensemble et agrandissables.
FORM : structure native déterministe issue du parcours : TopAppBar + contenu défilable, paire d’images en deux colonnes, boutons Material. Aucune exploration aléatoire ni seed rétrospective. La demande de simplicité et de composants standards prime sur le processus générique de génération de mondes visuels du skill ; aucun tirage ni approbation de comp n’est revendiqué.

La référence de qualité est la convention Android et le brief terrain, pas une carte d’inspiration inventée. Il s’agit d’une preuve technique, pas d’une validation esthétique ou d’usage terrain. L’interface native a une palette claire fixe ; le mode nuit système et le texte agrandi doivent être testés pour vérifier sa stabilité.

Matrice minimale : téléphone 320×640, texte 1× et 1,3× ; capture avec source vidéo virtuelle, revue avant/après, location clôturée. Pas de tablette ciblée dans ce MVP0.
