# Financio

Jouw persoonlijke financiële adviseur die altijd binnen handbereik is.

Android-app om ING-transacties in te laden (CSV/MT940), in te delen in categorieën, en per
categorie budgetlimieten te bewaken — met een rode markering zodra een categorie over budget
gaat. Fase 1 is bewust lokaal-only: geen backend, geen bankvergunning, geen netwerkpermissie.

## Status: fase-1 skeleton

Dit is het projectskeleton op basis van de vastgestelde architectuur. Nog geen pixel-perfecte
UI, wel een werkende laagstructuur van bestand tot budgetstatus.

## Modules

- **`core`** — pure Kotlin, geen Android-afhankelijkheid. De import-pipeline (CSV/MT940-parsers,
  format-detectie, dedup), de categorisatie-regelmatcher en de budgetdrempels. Draait en test
  zonder Android SDK.
- **`app`** — de Android-app: Room (versleuteld met SQLCipher, sleutel in de Android Keystore),
  Hilt voor DI, Jetpack Compose voor de UI. Implementeert de `core`-repository-interfaces.

## Bouwen

```
./gradlew :core:test    # de import-/categorisatie-/budgetlogica, geen Android SDK nodig
./gradlew :app:build    # de volledige app — vereist een lokale Android SDK-installatie
```

`:core:test` is in deze repo geverifieerd (33 tests, groen). `:app` kon in de omgeving waarin
dit skeleton is opgezet niet gebouwd worden — die had geen toegang tot Google's Maven-repository
(nodig voor AndroidX/AGP) — dus open het project in Android Studio voor de eerste echte build
van `:app`, met name om te controleren:
- de exacte SQLCipher-integratieklasse in `app/di/DatabaseModule.kt` tegen de geïnstalleerde
  `net.zetetic:sqlcipher-android`-versie;
- of `compileSdk`/`targetSdk` 35 nog de gewenste keuze is tegen die tijd.

## Wat nog moet gebeuren (fase 1)

- Grafieken-scherm (maand-/jaarvergelijking per categorie) — databasekant staat al klaar.
- Instellingen: budgetlimieten instellen, categorisatieregels beheren.
- BiometricPrompt-vergrendeling vóór de app-content.
- Handmatige categorisatie voor transacties die geen regel matcht.

## Ontwerpdocumenten

De routekaart, technische architectuur en het schermontwerp waarop dit skeleton is gebaseerd
zijn per artifact met de projecteigenaar gedeeld tijdens het ontwerptraject.
