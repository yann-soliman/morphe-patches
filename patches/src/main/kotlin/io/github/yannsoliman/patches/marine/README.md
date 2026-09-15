# Météo Marine 7.1.3

Patch : **Marine Weather: remove forecast subscription screen**.

Package : `com.lachainemeteo.marine.androidapp`, version 7.1.3 (196).

Le bulletin tronque localement les prévisions horaires selon `LegendContent.display.hours`.
Son callback de navigation ouvre l'abonnement lorsque `BulletinRestrictions.isBulletinRestricted`
est vrai. Le patch retire cette troncature et neutralise ce seul indicateur de restriction.
Le comparateur, les cartes, le routage, la facturation et le statut du compte restent inchangés.
Seules les prévisions effectivement retournées par le serveur peuvent être affichées.

## Implémentation

- Le fingerprint du constructeur `(ZZ)V` utilise la classe identifiée par la chaîne
  `BulletinRestrictions(isBulletinRestricted=`. Son premier argument devient faux.
- Le mapper statique des prévisions horaires est identifié par sa signature complète,
  son type de retour et ses appels `Display.getHours` puis `getForecasts`.
  Son troisième argument devient vrai pour conserver les heures reçues.
- Chaque cible doit être unique et toutes sont résolues avant modification.
- Les instructions `const/16` permettent notamment d'adresser le registre v31 du mapper.

## Vérifications et limites

Analyse du DEX original : constructeur `Lej0;-><init>(ZZ)V` (3 registres),
mapper `Lio3;->S(...)Ljava/util/LinkedHashMap;` (33 registres).
Le callback du bulletin lit le premier champ et déclenche le paywall quand il est vrai.
Les noms obfusqués ne sont pas utilisés pour sélectionner ces cibles.

SHA-256 de l'APK analysé :
`d3b4f255b0a84cc36527a44913120b484cf52b05e3b1fc875dbc317cafcceede`.

Tests de régression Python du dépôt : réussis.
Compilation locale : bloquée au téléchargement de Gradle par le réseau de l'environnement.
Application du bundle à l'APK et test sur téléphone : à effectuer.

## Test sur téléphone

1. Appliquer uniquement ce patch à l'APK 7.1.3 avec Morphe.
2. Ouvrir un lieu et vérifier les premières 24 heures.
3. Faire défiler au-delà de 24 heures puis sélectionner plusieurs jours : vérifier
   l'absence d'écran d'abonnement et la présence de valeurs météo avec les bonnes dates.
4. Vérifier un deuxième lieu, un retour arrière et une réouverture de l'application.
5. Vérifier que le comparateur conserve son comportement initial.

Ne pas conclure à un déblocage des données serveur si les heures suivantes sont vides.
