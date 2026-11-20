# Clim Haier — télécommande infrarouge Android

Application Android qui pilote une climatisation Haier (télécommande YL-W04, étiquette V9014557)
depuis un téléphone équipé d'un émetteur infrarouge (Xiaomi, Redmi, Poco…).

## Rétro-ingénierie : ce qui est connu du protocole

| Élément | Valeur |
|---|---|
| Porteuse | 38 kHz |
| Pré-entête | 3000 µs ON / 3000 µs OFF |
| Entête | 3000 µs ON / 4300 µs OFF |
| Bit | 520 µs ON, puis 1650 µs OFF = « 1 » / 650 µs OFF = « 0 » (MSB en premier) |
| Trame | **état complet** à chaque appui (et pas une simple touche) |

Trame V9014557 / YR-W02 (14 octets) :

| Octet | Contenu |
|---|---|
| 0 | Modèle : `0xA6` (variante A) ou `0x59` (variante B) |
| 1 | bits 7-4 = température − 16 ; bits 3-0 = volet (0 = fixe, 0xC = balayage) |
| 3 | bit 1 = Health |
| 4 | bit 6 = marche (1) / arrêt (0) |
| 5 | bits 7-5 = ventilation (1 forte, 2 moyenne, 3 faible, 5 auto) |
| 7 | bits 7-5 = mode (0 auto, 1 froid, 2 déshum., 4 chaud, 6 ventil.) |
| 8 | bit 7 = sommeil |
| 12 | touche appuyée (5 marche, 6 mode, 4 ventil., 0 temp+, 1 temp−, 2 volet, 11 sommeil) |
| 13 | somme des octets 0 à 12 (modulo 256) |

L'application contient aussi deux variantes, sélectionnables avec le bouton « Protocole » :
la trame 176 bits (22 octets, extension `0xB7`) et l'ancien protocole HSU07 (9 octets, préfixe `0xA5`).

Sources : bibliothèque IRremoteESP8266 (`src/ir_Haier.h`, `src/ir_Haier.cpp`). L'encodeur
(`HaierIr.java`) est vérifié contre des trames réelles capturées : `test/HaierIrTest.java`.

## Construire l'APK

- **Avec GitHub (sans PC)** : pousse ce dossier dans un dépôt GitHub. Le workflow
  `.github/workflows/build.yml` construit l'APK. Il se télécharge ensuite dans l'onglet
  *Actions*, rubrique *Artifacts* → `ClimHaier-apk`.
- **Avec un PC** : ouvre le dossier dans Android Studio, puis *Build → Build APK(s)*.

Ensuite, installe `app-debug.apk` sur le téléphone en autorisant les « sources inconnues ».

## Utilisation

1. Vise la clim avec le haut du téléphone et appuie sur **MARCHE**.
2. Si rien ne se passe, appuie sur **Protocole** pour passer à la variante suivante, puis réessaie.
3. Une fois la bonne variante trouvée, elle reste mémorisée.

Limite : une clim ne renvoie aucune information. L'application affiche donc l'état qu'elle a
envoyé en dernier. Si quelqu'un utilise la télécommande d'origine, l'affichage de l'appli
n'est plus à jour : appuie simplement sur MARCHE ou ARRÊT pour le resynchroniser.
