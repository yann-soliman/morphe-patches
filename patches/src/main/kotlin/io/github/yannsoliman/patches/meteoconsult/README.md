# METEO CONSULT 1.1.4 (37)

Package : `com.meteoconsult.androidapp`. Entrée testée : XAPK APKPure avec APK de base
et splits arm64, anglais et xxhdpi. Utiliser le XAPK complet dans Morphe.

## Comportement

Le patch **Meteo Consult: hide forecast end subscription prompt** désactive uniquement
l'appel qui ouvre l'abonnement en réponse à `BulletinEndReached`.
Il ne change ni la limite de l'API, ni les heures reçues, ni le statut du compte.
Après la dernière heure disponible, les données peuvent donc rester vides.
Le comparateur et les autres accès à l'abonnement conservent leur comportement.

## Analyse de l'APK

SHA-256 de l'APK de base :
`49d7dd9824b572acfb52ba423d1be7576dcd4f6e231fde1f7d17a70e87492fa6`.

- `yn/e` émet `BulletinEndReached` lorsque le jour sélectionné dépasse le dernier
  jour des prévisions horaires disponibles. `yn/b` émet aussi cet événement en fin de liste.
- `xl/q` fournit le gestionnaire `c0/i2`, cas 9, qui vérifie l'événement et la restriction.
- Le callback reçu de `jn/b` est `eq/j`, cas 1 : il choisit `forecastBulletin` et met
  l'état d'affichage de l'abonnement à vrai.
- `ll/a` utilise cet état pour remplacer le bulletin par l'écran d'abonnement.

Le seul changement est le remplacement par `nop` de l'appel `Function0.invoke`
à l'offset 0x2c6 de `Lc0/i2;->invoke(Ljava/lang/Object;)Ljava/lang/Object;`
dans `classes.dex`. Son résultat est ignoré et l'instruction suivante est un `goto`.
L'appel du comparateur à 0x2f6 n'est pas modifié.

Ces noms et offsets documentent l'analyse ; le patch sélectionne les classes
par les chaînes `BulletinEndReached` et `ComparatorEndReached`, puis identifie
l'appel par ses références et la structure de la branche. Chaque cible doit
être unique et la séquence d'instructions doit correspondre avant modification.

## Validation

La compilation CI ne remplace pas un essai sur téléphone. Pour le test fonctionnel :

1. Dans l'application d'origine, ouvrir un lieu et dépasser la dernière heure disponible
   dans les prévisions horaires ; constater l'ouverture de l'abonnement.
2. Appliquer ce patch au XAPK 1.1.4 avec Morphe et refaire le même parcours.
3. Vérifier que le bulletin reste affiché, même si aucune donnée supplémentaire n'apparaît.
4. Revenir aux premières heures, changer de lieu puis relancer l'application.
5. Vérifier que l'accès volontaire à l'abonnement depuis le compte fonctionne toujours.

Publier sur `dev` et tester la pré-release avant promotion vers `main`.
Le patch Marine existant est conservé.
