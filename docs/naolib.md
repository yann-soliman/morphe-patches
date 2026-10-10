# Patchs Naolib vélo

Ces patchs ciblent **Naolib vélo 3.6.1** (`com.jcdecaux.vls.nantes`).

## Carte avec microG

**Naolib vélo : carte avec microG** affiche la carte avec le moteur MapLibre
fourni par Morphe microG. Les commerces et autres points d’intérêt du fond de
carte sont masqués ; les stations de vélos et les parkings restent affichés.

Un appui sur un parking affiche une fenêtre avec son nom. Le bouton
**Voir le parking** ouvre sa page de détails ; **Fermer** revient à la carte.
La fenêtre affiche l’état du parking, les places libres et la capacité lorsque
Naolib fournit ces informations, avec l’heure de réception des dernières données.
Les stations de vélos conservent leur comportement habituel.

Ce patch nécessite **Morphe microG avec son moteur de carte**
(`app.revanced.android.gms`, version 7.2.1 compatible).
Téléchargement : <https://morphe.software/microg>.
Les services Google Play doivent également rester installés.
Si microG manque ou si son moteur de carte est absent, Naolib propose un bouton
pour ouvrir la page de téléchargement. Après installation, relancez Naolib.

Le chargement d’une nouvelle zone dépend du réseau et du moteur de carte.
Ce patch n’accélère pas le téléchargement du fond de carte.

## Parkings favoris

Le patch de carte ajoute un bouton étoile sur la carte. Il ouvre la liste des
parkings favoris, classés par nom. Pour enregistrer un parking, appuyez dessus
puis choisissez **Ajouter aux favoris**. La même fenêtre permet de le retirer.
Un appui dans la liste recentre la carte sur le parking et ouvre sa fenêtre ;
**Voir le parking** ouvre ses détails habituels.

Les favoris sont enregistrés uniquement sur ce téléphone dans les données de
Naolib, sans synchronisation avec le compte. Une désinstallation ou un effacement
des données supprime cette liste. Les noms et positions enregistrés correspondent
aux données disponibles au moment de l’ajout. Les données reçues depuis par
Naolib sont utilisées en priorité. Si elles ne sont pas encore chargées, la
disponibilité est indiquée comme non actualisée. Les détails utilisent le
parcours habituel de Naolib.

## Position de la carte

La position, le zoom, l’inclinaison et l’orientation sont enregistrés sur le
téléphone à la fin de chaque déplacement de la carte. Ils sont restaurés au
prochain chargement. Les boutons de localisation restent disponibles pour
recentrer la carte sur votre position.

## Ouvrir les parkings à distance

**Naolib vélo : ouvrir les parkings à distance** retire le contrôle local de
proximité avant l’ouverture d’un véloparc. Le seuil configuré dans l’application
est de 500 m pour le service utilisé ici.

La localisation, le compte et un accès valide au parking restent nécessaires.

## Déverrouiller les vélos à distance

**Naolib vélo : déverrouiller les vélos à distance** retire les deux contrôles
locaux de proximité : lors de la sélection de la station la plus proche et avant
le déverrouillage. Le seuil utilisé ici est de 100 m.

L’application conserve la sélection de la station la plus proche et la demande
habituelle de location. La localisation, le compte et un accès valide au service
restent nécessaires. Les éventuelles restrictions du serveur restent applicables.

## Conserver les parkings

**Naolib vélo : conserver les parkings** conserve la liste en mémoire lorsque
l’actualisation des parkings renvoie une liste vide. Il ne crée pas de parkings
et ne conserve aucune liste si elle n’a pas déjà été chargée.

## Installation

Les quatre patchs sont présélectionnés dans Morphe ; vous pouvez décocher ceux
que vous ne souhaitez pas utiliser.

Importez le fichier `.mpp` dans Morphe et appliquez les patchs souhaités à
l’archive originale de Naolib vélo 3.6.1. Le patch de carte peut être combiné
avec les patchs de proximité et de conservation des parkings.

Les patchs incluent le correctif de démarrage nécessaire à l’application modifiée.
L’installation peut nécessiter une reconnexion au compte.
