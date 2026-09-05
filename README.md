# Financio

Jouw persoonlijke financiële adviseur die altijd binnen handbereik is.

Android-app om ING-transacties in te laden (CSV/MT940), in te delen in categorieën, en per
categorie budgetlimieten te bewaken — met een rode markering zodra een categorie over budget
gaat. Fase 1 is bewust lokaal-only: geen backend, geen bankvergunning, geen netwerkpermissie.

## Status: fase 1 compleet (ongebouwd)

Alle fase-1 functionaliteit uit de routekaart is geïmplementeerd: transacties, budgetten,
grafieken, instellingen, importeren, biometrische vergrendeling en handmatige categorisatie.
Nog niet gecompileerd in een echte Android-omgeving — zie "Bouwen" hieronder.

## Modules

- **`core`** — pure Kotlin, geen Android-afhankelijkheid. De import-pipeline (CSV/MT940-parsers,
  format-detectie, dedup), de categorisatie-regelmatcher (inclusief geleerde regels uit
  handmatige categorisatie) en de budgetdrempels. Draait en test zonder Android SDK.
- **`app`** — de Android-app: Room (versleuteld met SQLCipher, sleutel in de Android Keystore),
  Hilt voor DI, Jetpack Compose voor de UI. Implementeert de `core`-repository-interfaces.

## Wat er werkt

- **Transacties** — lijst, met tik-om-te-categoriseren voor niet-gematchte transacties.
- **Budgetten** — limieten per categorie met groen/amber/rood, tik op een categorie voor de grafiek.
- **Grafieken** — maand-op-maand en jaar-op-jaar per categorie, met de budgetlimiet als lijn in de grafiek.
- **Importeren** — CSV/MT940 inlezen, dedupliceren, automatisch categoriseren; wat overblijft
  kun je vóór het bevestigen handmatig toewijzen (en dat wordt onthouden als nieuwe regel).
- **Instellingen** — budgetlimieten instellen, categorisatieregels inzien, biometrische
  vergrendeling aan/uit.
- **App-vergrendeling** — `BiometricPrompt` (vingerafdruk/gezicht/schermbeveiliging) vóór de
  content, aan te zetten in Instellingen; staat standaard aan.

## Bouwen

```
./gradlew :core:test    # de import-/categorisatie-/budgetlogica, geen Android SDK nodig
./gradlew :app:build    # de volledige app — vereist een lokale Android SDK-installatie
```

`:core:test` is in deze repo geverifieerd (35 tests, groen). `:app` kon in de omgeving waarin
dit skeleton is opgezet niet gebouwd worden — die had geen toegang tot Google's Maven-repository
(nodig voor AndroidX/AGP) — dus open het project in Android Studio voor de eerste echte build
van `:app`. Controleer daarbij in ieder geval:
- de exacte SQLCipher-integratieklasse in `app/di/DatabaseModule.kt` tegen de geïnstalleerde
  `net.zetetic:sqlcipher-android`-versie;
- of `compileSdk`/`targetSdk` 35 en de overige versies in `gradle/libs.versions.toml` nog de
  gewenste keuze zijn tegen die tijd;
- of `BiometricPrompt.PromptInfo` met `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` op een testtoestel
  het verwachte systeemscherm toont (`app/MainActivity.kt`).

## Bekende beperkingen

- De app-vergrendeling gebruikt lokale Compose-state (niet bewaard over schermrotatie of
  procesherstart) — een rotatie vraagt dus opnieuw om ontgrendelen. Functioneel geen probleem,
  wel een punt om later te verfijnen als het als hinderlijk ervaren wordt.
- Handmatige categorisatie in het importscherm gebruikt een eenvoudig dropdown-menu, geen
  zoekbalk — prima bij een handvol categorieën, minder prettig bij tientallen.

## Ontwerpdocumenten

De routekaart, technische architectuur en het schermontwerp waarop dit is gebaseerd zijn per
artifact met de projecteigenaar gedeeld tijdens het ontwerptraject.
