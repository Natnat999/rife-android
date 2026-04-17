# Rife-Android

Application Android pour convertir des vidéos de 30 fps à 60 fps en utilisant l'algorithme RIFE, NCNN et l'accélération GPU Vulkan.

## Fonctionnalités
- **Interpolation RIFE v4.6** : Double la fluidité des vidéos.
- **Accélération GPU** : Utilise Vulkan via NCNN.
- **Pipeline FFmpeg** : Décodage et encodage haute performance.
- **Material You** : Interface moderne avec couleurs dynamiques.
- **Modèles à la demande** : Les modèles sont téléchargés au premier lancement pour garder l'APK léger.

## Installation
L'APK est disponible dans la section [Releases](https://github.com/Natnat999/rife-android/releases).

## Compilation
Le projet peut être compilé avec Android Studio ou via la ligne de commande :
```bash
./gradlew assembleDebug
```
