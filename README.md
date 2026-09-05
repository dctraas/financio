# Financio

Jouw persoonlijke financiële adviseur die altijd binnen handbereik is.

Android-app om ING-transacties in te laden (CSV/MT940), in te delen in categorieën, en per
categorie budgetlimieten te bewaken — met een rode markering zodra een categorie over budget
gaat. Fase 1 is bewust lokaal-only: geen backend, geen bankvergunning, geen netwerkpermissie.

## Status: fase 1 compleet, bouwt en start

Alle fase-1 functionaliteit uit de routekaart is geïmplementeerd: transacties, budgetten,
grafieken, instellingen, importeren, biometrische vergrendeling en handmatige categorisatie.
`:app` compileert inmiddels succesvol in een echte Android Studio-omgeving; de eerste
opstart-crash (zie "Nog te controleren") is gefixt — zie dat overzicht voor de rest van het
traject.

## Modules

- **`core`** — pure Kotlin, geen Android-afhankelijkheid. De import-pipeline (CSV/MT940-parsers,
  format-detectie, dedup), de categorisatie-regelmatcher (inclusief geleerde regels uit
  handmatige categorisatie) en de budgetdrempels. Draait en test zonder Android SDK.
- **`app`** — de Android-app: Room (versleuteld met SQLCipher, sleutel in de Android Keystore),
  Hilt voor DI, Jetpack Compose voor de UI. Implementeert de `core`-repository-interfaces.

## Wat er werkt

- **Transacties** — lijst met datum per regel, zoeken op naam/omschrijving, filteren op categorie
  (inclusief een "Niet gecategoriseerd"-filter) en sorteren (datum/bedrag, op- of aflopend) — de
  controles die bunq/YNAB/Buddy ook boven hun transactielijst zetten. Een niet-gecategoriseerde
  transactie is nu duidelijk te onderscheiden (omlijnde amberkleurige stip + "Tik om te
  categoriseren" in plaats van de neutrale kleur van een echte categorie als "Overig"). Tik op
  *elke* transactie, ook een al gecategoriseerde, om de categorie te wijzigen.
- **Budgetten** — limieten per categorie met groen/amber/rood, tik op een categorie voor de grafiek.
- **Grafieken** — maand-op-maand en jaar-op-jaar per categorie, met de budgetlimiet als lijn in de
  grafiek en een ‹ ›-navigator om het weergegeven venster naar eerdere maanden/jaren te schuiven
  (voorheen alleen een vast venster eindigend op vandaag, met geen manier om terug te bladeren).
- **Importeren** — CSV/MT940 inlezen, dedupliceren, automatisch categoriseren; wat overblijft
  wordt **per tegenpartij gegroepeerd** (één keuze voor alle 40 Albert Heijn-transacties samen,
  in plaats van 40 losse keuzes), gesorteerd op grootste totaalbedrag eerst, met aantal/bedrag/
  periode per groep. Elke keuze wordt onthouden als nieuwe regel voor toekomstige imports.
- **Standaardcategorieën en -regels** — een nieuwe, lege database wordt eenmalig gevuld met 13
  categorieën (Boodschappen, Vervoer, Uit eten, Abonnementen, Kleding & verzorging, Wonen &
  vaste lasten, Gezondheid & verzekering, Vrije tijd & hobby's, Vakantie & reizen, Cadeaus &
  giften, Sparen & beleggen, Inkomsten, Overig) en een curated set trefwoordregels voor
  bekende, ondubbelzinnige merken (supermarkten, energieleveranciers, streamingdiensten, etc.) —
  zie `DefaultCategorization` in `:core`. Bewust géén regel voor generieke webshops (Bol.com,
  Amazon): die verkopen van alles, dus daar gokken we niet — één keer handmatig kiezen volstaat,
  daarna onthouden als regel.
- **Categorieën & regels beheren** — eigen scherm (via Instellingen → "Beheren"): categorieën
  toevoegen/verwijderen, en regels handmatig toevoegen/verwijderen (trefwoord of exacte
  tegenrekening/IBAN). Een handmatig toegevoegde regel (`ManualRule`, prioriteit 10) wint altijd
  van zowel een standaardregel (20) als een geleerde regel uit import (50).
- **Instellingen** — budgetlimieten instellen, link naar categorie-/regelbeheer, biometrische
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
- **`org.jetbrains.kotlin.android`-plugin verwijderd uit `app/build.gradle.kts`.** AGP 9.0
  introduceerde ingebouwde Kotlin-ondersteuning, standaard aan — de losse Kotlin Android-plugin
  toepassen geeft sindsdien een harde configuratiefout ("no longer required for Kotlin support
  since AGP 9.0"), niet alleen een waarschuwing. Dit had eigenlijk de échte oorzaak van de
  vorige "Cannot add extension with name 'kotlin'"-fout kunnen zijn (Kotlin 2.3.21 geeft nu
  gewoon een duidelijke diagnose in plaats van een generieke Gradle-extensiebotsing). Geverifieerd
  via Android's eigen releasenotes (de sandbox heeft geen toegang tot Google's Maven om dit zelf
  te bouwen). `:core` gebruikt een aparte, niet-Android Kotlin-plugin en is hier niet door
  geraakt — nog steeds 35 tests, groen.
- **Root-`build.gradle.kts` miste dezelfde opschoning.** De vorige fix verwijderde de
  `kotlin-android`-plugin uit `app/build.gradle.kts` en de catalogus-entry, maar over het hoofd
  gezien dat het root-`build.gradle.kts` diezelfde alias óók nog met `apply false` aanriep (om de
  versie eenmalig te declareren voor alle modules) — daardoor faalde de configuratie alsnog met
  "Unresolved reference 'android'" zodra de catalogus-entry weg was. Dat was geen nieuwe
  ecosysteemverrassing maar een onvolledige eigen fix; nu ook uit het root-bestand verwijderd.
- **Hilt 2.52 → 2.60.1.** De Hilt Gradle-plugin faalde met "Android BaseExtension not found"
  tegen AGP 9.1.0 — dit keer wél een genuine, bevestigde ecosysteemincompatibiliteit, niet iets
  wat de sandbox had kunnen voorkomen: Hilt's eigen Gradle-plugin kreeg pas AGP 9-ondersteuning
  vanaf versie 2.59 (bevestigd via [google/dagger#4944](https://github.com/google/dagger/issues/4944)
  en [#5083](https://github.com/google/dagger/issues/5083); 2.52 dateert van ruim daarvoor). 2.59
  zelf had echter een kapotte `ComponentTreeDeps`-runtime tegen AGP 9
  ([#5099](https://github.com/google/dagger/issues/5099)), pas gefixt in latere patches. Daarom
  niet naar 2.59 maar naar **2.60.1** getild — de nieuwste release op Maven Central op het moment
  van deze fix, met de vervolgfixes verwerkt. `hilt-android` en `hilt-android-compiler` zijn
  beide bevestigd te bestaan op Maven Central in versie 2.60.1 (rechtstreeks met `curl`
  gecontroleerd, geen giswerk). `:core` gebruikt geen Hilt en is hier niet door geraakt.
- **Room 2.6.1 → 2.8.4.** `:app:kspDebugKotlin` faalde met `IllegalStateException: unexpected
  jvm signature V` diep in Room's eigen annotation processor (bij het verwerken van een
  `suspend`-DAO-methode met een `Unit`-returntype). Dit is een bevestigde, bekende KSP2-bug
  ([google/ksp#2177](https://github.com/google/ksp/issues/2177),
  [#2957](https://github.com/google/ksp/issues/2957)) tegen onze Kotlin 2.3.21/KSP 2.3.11 —
  Room 2.6.1 dateert van vóór KSP2 en loopt hier tegenaan. Gefixt upstream in Room; getild naar
  **2.8.4**, de nieuwste stabiele release (geverifieerd via developer.android.com/jetpack/androidx/releases/room).
  Room 2.8.0 verhoogde zelf de minSdk-ondergrens naar 23 — ruim onder onze minSdk 26, dus geen
  verdere aanpassing nodig. Zijdelings: sinds Room 2.7 is `room-ktx` een leeg artifact (alle
  functionaliteit zit nu in `room-runtime`) — blijft onschadelijk in de dependency-lijst staan,
  hoeft niet weg. `:core` gebruikt geen Room en is hier niet door geraakt.
- **`:app` compileert nu — eerste opstart-crash: `FinancioApplication` ontbrak in het manifest.**
  Na alle bovenstaande build-fixes compileerde `:app` voor het eerst succesvol, maar de app
  crashte direct bij opstart met "Hilt Activity must be attached to an @HiltAndroidApp
  Application. Did you forget to specify your Application's class name in your manifest's
  `<application />`'s `android:name` attribute?" — een eigen fout uit de oorspronkelijke
  skeleton-opzet: `FinancioApplication` (met `@HiltAndroidApp`) bestond al, maar
  `AndroidManifest.xml`'s `<application>`-tag verwees er nooit naar. Toegevoegd:
  `android:name=".FinancioApplication"`.
- **`UnsatisfiedLinkError` op `SQLiteConnection.nativeOpen`.** Na de manifest-fix startte de app,
  maar crashte bij de eerste databasetoegang. `net.zetetic:sqlcipher-android`'s
  `SupportOpenHelperFactory` laadt de native SQLCipher-library niet automatisch — dat moet de
  toepassing zelf doen vóór elke databasehandeling, per de library's eigen documentatie
  ([sqlcipher/sqlcipher-android](https://github.com/sqlcipher/sqlcipher-android)'s README, exact
  dit patroon). Toegevoegd: `System.loadLibrary("sqlcipher")` vóór `Room.databaseBuilder(...)` in
  `DatabaseModule.provideDatabase()`. Een eigen omissie uit de oorspronkelijke opzet, niet
  gerelateerd aan de eerdere versie-bumps.
- **CSV-import faalde met "Kolom 'Datum' ontbreekt" — twee keer, met een verkeerde eerste
  diagnose.** De eerste keer leek de export tab-gescheiden; dat bleek achteraf een
  kopieerartefact (Excel → terminal-plak) te zijn, geen echt tab-teken — mijn fout, ik had toen
  geen letterlijke bytes van het bestand, alleen hoe het er in de terugkoppeling uitzag. De
  échte, herbevestigde oorzaak: ING's CSV-velden staan tussen dubbele aanhalingstekens
  (`"Datum";"Naam / Omschrijving";...`, RFC 4180-stijl) en de parser haalde die aanhalingstekens
  nooit weg — het token was dus letterlijk `"Datum"`, wat nooit gelijk is aan `Datum`. Dit keer
  bevestigd tegen een letterlijke, geciteerde export die de projecteigenaar rechtstreeks plakte.
  `CsvIngParser` heeft nu een RFC 4180-bewuste regel-splitser (`splitCsvLine`) die aanhalingstekens
  correct verwijdert (en `""`-escapes ontsnapt) vóórdat kolomnamen/waarden vergeleken worden; de
  eerdere tab/puntkomma-detectie blijft staan (onschadelijk, kost niets) maar is nu gedocumenteerd
  als defensief, niet als bevestigd echt formaat. Getest tegen de exacte, geanonimiseerde
  originele export (`CsvIngParserTest`) — in de sandbox echt gebouwd en getest, want dit raakt
  alleen `:core`: **37 tests, groen** (was 36, plus de nieuwe test).
- **`FOREIGN KEY constraint failed` bij importeren.** De allereerste import na de vorige fixes
  crashte alsnog: `TransactionEntity.accountId` heeft een foreign key naar `accounts.id`, maar
  er werd nergens ooit een rij in `accounts` aangemaakt — `AccountDao.insert()` bestond al sinds
  het skeleton maar werd nooit aangeroepen. `DatabaseSeeder` seedt nu ook het enige fase-1-account
  (`DefaultAccount`, expliciet met id 1 zodat het overeenkomt met wat de rest van de app
  aanneemt), naast de categorieën/regels van de vorige fix. Beide seeds zijn onafhankelijk
  idempotent (los gecontroleerd op bestaan) zodat dit ook op een bestaande installatie die de
  categorieën al wél kreeg (maar het account nooit) alsnog vanzelf herstelt, zonder de app-data
  te hoeven wissen.
- **Categorieën & regels beheren.** Nieuw scherm (`CategoryManagementScreen`, bereikbaar via
  Instellingen) om categorieën en regels handmatig toe te voegen/verwijderen — zie "Wat er werkt"
  hierboven. `:core` kreeg `ManualRule` (prioriteit 10, wint van standaard- en geleerde regels) en
  `CategoryRepository` drie nieuwe methodes (`addCategory`/`deleteCategory`/`deleteRule`); beide
  volledig unit-getest (49 tests groen, was 47). De Android-laag (DAO's, Room-repository-impl,
  Compose-UI, navigatie) is zoals altijd niet in deze sandbox te bouwen.
- **Transacties: filteren/sorteren, datum tonen, altijd kunnen hercategoriseren; Grafieken:
  periode-navigator.** Drie losse verzoeken in één keer verwerkt, alle drie zuivere UI-/
  ViewModel-wijzigingen in `:app` — geen `:core`-code geraakt, dus niets hier opnieuw te
  bouwen/testen. `TransactionsViewModel` filtert/sorteert nu client-side (zoekterm, categorie,
  4 sorteringen) over de al geladen lijst — bewust geen SQL-query per filtercombinatie, want een
  persoonlijke rekening is klein genoeg om dat overbodig te maken. `ChartsViewModel` kreeg een
  verschuifbaar ankerpunt (`referenceMonth`) in plaats van een vast venster dat altijd op vandaag
  eindigde. Bewust `‹`/`›`-tekens gebruikt in plaats van `Icons.Filled.ChevronLeft`/`ChevronRight`:
  kon niet verifiëren of die iconen in de meegeleverde `material-icons-core` zitten (versus de
  niet-meegeleverde `material-icons-extended`) zonder een build, dus liever hetzelfde
  platte-tekst-linkpatroon dat de rest van de app al gebruikt (bijv. "Beheren →").
- of de overige versies in `gradle/libs.versions.toml` nog de gewenste keuze zijn tegen die tijd;
- of `BiometricPrompt.PromptInfo` met `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` op een testtoestel
  het verwachte systeemscherm toont (`app/MainActivity.kt`);
- of `DatabaseSeeder.seedIfEmpty()` daadwerkelijk draait vóór het eerste import-scherm de
  categorielijst opvraagt (`FinancioApplication.onCreate()` start het als fire-and-forget
  coroutine op `Dispatchers.IO`) — de logica zelf (`DefaultCategorization` in `:core`) is
  volledig getest, maar de Hilt-injectie in `Application` en de Room-transactie eromheen zijn
  Android-specifiek en dus niet in deze sandbox te verifiëren.

## Bekende beperkingen

- De app-vergrendeling gebruikt lokale Compose-state (niet bewaard over schermrotatie of
  procesherstart) — een rotatie vraagt dus opnieuw om ontgrendelen. Functioneel geen probleem,
  wel een punt om later te verfijnen als het als hinderlijk ervaren wordt.
- Handmatige categorisatie in het importscherm gebruikt een eenvoudig dropdown-menu, geen
  zoekbalk — prima bij een handvol categorieën, minder prettig bij tientallen.
- Standaardregels (prioriteit 20) winnen van een `LearnedRule` uit import (prioriteit 50) voor
  dezelfde tegenpartij — in de praktijk onschadelijk, want een al automatisch gecategoriseerde
  transactie verschijnt sowieso niet in "Te controleren". Wil je een standaardregel structureel
  overrulen: voeg in Categorieën & regels een handmatige regel toe (prioriteit 10, wint altijd)
  in plaats van te wachten op een nieuwe import.
- Regels kunnen alleen worden toegevoegd/verwijderd, niet bewerkt — voor "regel X moet naar
  categorie Y in plaats van Z" verwijder je 'm en maak je 'm opnieuw aan.
- Een categorie verwijderen kan niet ongedaan worden gemaakt; er komt geen bevestiging-met-
  voorbeeld ("dit raakt N transacties en M regels"), alleen de generieke waarschuwingstekst.
- De grafieken-periodenavigator kan onbeperkt terug — er is geen "geen data meer, klaar"-grens;
  je ziet dan gewoon lege (€0,00) staven voor periodes vóór je eerste import.
- Filteren/sorteren in Transacties werkt alleen op wat al geladen is in het geheugen (client-side);
  bij een zeer lange transactiehistorie (jaren) zou dit ooit naar een database-query verplaatst
  moeten worden, maar voor fase 1 is dat verre toekomstmuziek.

## Ontwerpdocumenten

De routekaart, technische architectuur en het schermontwerp waarop dit is gebaseerd zijn per
artifact met de projecteigenaar gedeeld tijdens het ontwerptraject.
