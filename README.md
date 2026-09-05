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
(nodig voor AndroidX/AGP) — dus is dit voor het eerst echt gecompileerd en gestart in Android
Studio door de projecteigenaar, wat een paar dingen aan het licht bracht die de sandbox niet kon
vinden. Nog te controleren:
- **16 KB-pagina-uitlijning van AndroidX-native libraries.** `libsqlcipher.so` is opgelost door
  `net.zetetic:sqlcipher-android` naar 4.18.0 te tillen (geverifieerd: de klasse bestaat nog en
  de LOAD-segmenten zijn daadwerkelijk 16 KB uitgelijnd). `libandroidx.graphics.path.so` — een
  transitieve afhankelijkheid, vermoedelijk via `navigation-compose`'s predictive-back-animatie —
  kon niet vanuit deze sandbox worden opgelost (die library staat alleen op Google's
  Maven-repository, hier niet bereikbaar); de projecteigenaar heeft `navigationCompose`,
  `activityCompose` en `composeBom` via Android Studio bijgewerkt naar respectievelijk `2.10.0`,
  `1.13.0` en `2026.08.00`.
- **AGP 8 → 9, compileSdk 35 → 37.** Die bijgewerkte libraries bleken zelf AGP 9.1.0 en
  compileSdk 37 te vereisen (`checkDebugAarMetadata` faalde daar expliciet op — geen giswerk,
  de foutmelding noemt deze versies letterlijk). Beide zijn dienovereenkomstig bijgewerkt.
- **Gradle 8.14.3 → 9.3.1.** AGP 9.1.0 vereist dat zelf. De wrapper (`gradle/wrapper/*`,
  `gradlew`, `gradlew.bat`) is bijgewerkt en dit keer wél in deze sandbox getest: Gradle 9.3.1
  gedownload en `:core` er letterlijk mee gebouwd, met de échte `gradle/libs.versions.toml` en
  `core/build.gradle.kts` uit deze repo (niet een losse testharness zoals bij eerdere fixes) —
  **35 tests, groen**. Dat testen ving meteen een echt Gradle 9-probleem op: sinds Gradle 9 komt
  de JUnit Platform Launcher niet meer automatisch op het testklassenpad terecht, dus `gradle
  test` faalde met "Failed to load JUnit Platform" tot `junit-platform-launcher:1.11.0` expliciet
  als `testRuntimeOnly`-afhankelijkheid was toegevoegd — inmiddels gedaan.
- **Kotlin 2.0.21 → 2.3.21, KSP 2.0.21-1.0.28 → 2.3.11.** AGP 9.1.0 kon niet samen met
  Kotlin 2.0.21 — `org.jetbrains.kotlin.android` faalde met "Cannot add extension with name
  'kotlin'", een genuine versie-incompatibiliteit (Kotlin 2.0.21 dateert van ruim vóór AGP 9).
  Dit maakte de eerder bewust uitgestelde Kotlin/KSP-update alsnog noodzakelijk. Beide zijn in
  deze sandbox echt geverifieerd, inclusief het samenspel: een minimaal Kotlin+KSP-scratchproject
  gebouwd om de versiecombinatie te bevestigen, én `:core` opnieuw gedraaid met de bijgewerkte
  `gradle/libs.versions.toml` — **35 tests, groen**, en de eerdere "incompatible with Gradle
  10"-waarschuwing (uit de Kotlin-plugin zelf) is als bijvangst ook verdwenen. Let op: KSP's
  eigen versienummering is losgekoppeld van Kotlin's versienummer sinds Kotlin 2.3 — `2.3.11` is
  geen typfout voor `2.3.21-iets`, KSP volgt vanaf hier zijn eigen releaseschema.
- of de overige versies in `gradle/libs.versions.toml` nog de gewenste keuze zijn tegen die tijd;
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
