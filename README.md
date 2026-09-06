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
  *elke* transactie, ook een al gecategoriseerde, om de categorie te wijzigen — daarna wordt
  gevraagd of dezelfde categorie ook toegepast moet worden op de andere transacties van diezelfde
  tegenpartij die al in de lijst staan (scheelt tijd bij bijvoorbeeld 40 Albert Heijn-regels).
  Ook ING's eigen "Tag"-label (indien aanwezig) wordt getoond en is doorzoekbaar. Een transactie
  kan ook **gesplitst** worden over meerdere categorieën (bijv. één Albert Heijn-bon half
  boodschappen, half drogisterij) — "Veilig te besteden deze maand" bovenaan het scherm laat zien
  hoeveel er, gegeven het huidige saldo en verwachte abonnementsafschrijvingen, nog uitgegeven kan
  worden. Zodra er meer dan één rekening bestaat verschijnt hier ook een rekeningfilter
  ("Alle rekeningen" of één specifieke).
- **Budgetten** — limieten per categorie met groen/amber/rood, tik op een categorie voor de grafiek.
  Optionele rollover per categorie (in te stellen in Instellingen): onbenut budget van vorige
  maand telt als bonusruimte bij deze maand op.
- **Grafieken** — maand-op-maand en jaar-op-jaar per categorie, met de budgetlimiet als lijn in de
  grafiek en een ‹ ›-navigator om het weergegeven venster naar eerdere maanden/jaren te schuiven
  (voorheen alleen een vast venster eindigend op vandaag, met geen manier om terug te bladeren).
  Een derde modus, Saldoverloop, toont het banksaldo zelf als lijngrafiek in de tijd (per
  rekening, met een rekeningkiezer zodra er meer dan één bestaat).
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
- **Categorieën & regels importeren/exporteren** — vanuit Instellingen: alles in één bestand, of
  losse categorieën/regels apart — allemaal als leesbare JSON. Categorieën worden op naam
  gematcht, regels op categorienaam + type + patroon; bestaat iets al lokaal, dan blijft dat
  ongewijzigd staan (nooit een bestaande kleur of regel-prioriteit overschrijven). Eén gedeeld
  importbestand-formaat volstaat voor zowel "alles" als "losse onderdelen": het bestand beschrijft
  zelf wat erin zit, dus is er maar één "Bestand importeren"-knop nodig. Zie `BackupSerializer`,
  `CategoryImport` en `RuleImport` in `:core`.
- **Instellingen** — budgetlimieten (met optionele rollover) instellen, links naar rekeningen-,
  categorie-/regel- en spaardoelenbeheer en Abonnementen, import/export, biometrische
  vergrendeling aan/uit, meldingen aan/uit.
- **Meldingen** — volledig lokaal, geen server of pushtoken: een melding zodra een budget net over
  de 80%- of 100%-grens gaat (direct na categoriseren of importeren, niet pas bij de eerstvolgende
  keer dat de app open is), en een wekelijkse samenvatting (totaal besteed deze week, aantal
  budgetten over de limiet). Staat standaard uit; het aanzetten vraagt op Android 13+ meteen om de
  systeemtoestemming.
- **Abonnementen** — herkent terugkerende afschrijvingen (Netflix, Spotify, verzekeringen, etc.)
  puur uit je eigen transactiehistorie: geregeld qua timing (ongeveer maandelijks) én qua bedrag
  (max. 15% afwijking). Geen bankkoppeling, geen merchant-database — zie `SubscriptionDetector`
  in `:core`. Toont de geschatte totale maandlast en, per abonnement, wanneer de volgende
  afschrijving verwacht wordt.
- **Spaardoelen** — een doelbedrag koppelen aan een categorie; de voortgang is simpelweg hoeveel
  er ooit netto naar die categorie is overgeboekt (dezelfde `debits - credits`-conventie als
  Budgetten, alleen niet per maand) — een latere opname (een bijschrijving in die categorie)
  verlaagt de voortgang dus vanzelf weer, zonder een aparte boekhouding daarvoor nodig te hebben.
- **Meerdere rekeningen** — nog steeds volledig lokaal: elke rekening krijgt zijn eigen,
  losse CSV-/MT940-import (geen bankkoppeling, geen aggregator — dat blijft de bewust uitgestelde
  architectuurstap). Categorieën, budgetten en spaardoelen gelden over alle rekeningen heen.
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
- **Budgetten toonde dezelfde categorie twee keer.** Echte oorzaak: `BudgetEntity` had geen
  unique constraint op (categoryId, yearMonth), en `setLimit()` gaf bij elke aanroep `id=0` mee
  aan een `@Insert(onConflict=REPLACE)` — zonder een bestaande rij met dat exacte id om te
  vervangen, betekende dat gewoon "voeg een nieuwe rij toe", elke keer weer. Geen schemamigratie
  gebruikt om dit te repareren (dat zou een `Migration(1,2)` vereisen die ik zonder een build om
  tegen te testen niet veilig genoeg vertrouwde — een subtiele fout daarin kan bestaande data
  stukmaken). In plaats daarvan: `setLimit()` zoekt nu eerst de bestaande rij op en hergebruikt
  diens id (dus REPLACE vervangt 'm daadwerkelijk), plus een onschadelijke, bij elke opstart
  uitgevoerde opruimquery (`BudgetDao.deleteDuplicates()`) die jouw al-bestaande dubbele rijen
  voor dezelfde categorie+maand samenvoegt tot de meest recente.
- **Sommige grafieken (bijv. Inkomsten) toonden niets.** `ChartsViewModel` gebruikte
  `observeSpent()` — dezelfde query als Budgetten, die bewust alléén negatieve bedragen (uitgaven)
  optelt. Voor een categorie die uitsluitend bijschrijvingen bevat (Inkomsten) is dat altijd nul,
  terwijl Transacties' filter op diezelfde categorie gewoon alle bijpassende rijen toont. Nieuwe,
  aparte query `observeCategoryTotal()` (som van absolute bedragen, ongeacht teken) toegevoegd
  specifiek voor Grafieken; `observeSpent()` blijft ongewijzigd voor Budgetten, waar "uitgaven
  tegen een limiet" wél de juiste betekenis is.
- **Categorieën & regels importeren/exporteren.** Nieuwe `kotlinx-serialization-json`
  afhankelijkheid (1.11.0, geverifieerd als nieuwste stabiele release op Maven Central) en de
  bijbehorende `org.jetbrains.kotlin.plugin.serialization`-plugin (versie gelijk aan de
  Kotlin-compiler, zoals `kotlin.compose` dat ook al deed) toegevoegd aan `:core`. Alle
  export/import-logica zelf is volledig unit-getest (64 tests groen, was 49); de Android-laag
  (SAF-bestandskiezers, de daadwerkelijke DB-writes) is zoals altijd niet in deze sandbox te
  bouwen.
- **Nieuw app-icoon.** Een afgeronde vierkant in het bestaande accentgroen met een simpel wit
  oplopend staafdiagram — "je financiën, stijgende lijn". Bewust opgebouwd uit rechte lijnen en
  één standaard rounded-rect-boogformule in plaats van freehand curves, en vooraf gerenderd via
  een SVG-equivalent in een headless Chromium in deze sandbox om het uiterlijk te controleren
  (zowel als afgerond vierkant als cirkel-uitgesneden, ter simulatie van hoe verschillende
  launchers adaptieve iconen bijsnijden) — het enige stuk van deze hele lijst dat wél visueel
  geverifieerd kon worden vóórdat het jouw toestel bereikt.
- **Eerste echte schemamigratie (v1 → v2): ING-Tag, saldoverloop, gesplitste transacties,
  spaardoelen.** Vier van een grotere batch functionaliteiten (zie hieronder), maar allemaal
  gebouwd op dezelfde, bewust *puur additieve* migratie: twee nieuwe nullable kolommen op
  `transactions` (`balanceCents`, `tag`, beide `DEFAULT NULL`) en twee gloednieuwe, lege tabellen
  (`transaction_splits`, `savings_goals`) — geen enkele bestaande kolom of rij wordt aangeraakt.
  Dat is bewust: een migratie die bestaande data moet *transformeren* (zoals de eerdere
  Budgetten-dubbeling) vertrouwde ik zonder build niet genoeg om blind te proberen; een puur
  additieve migratie wel. Dit keer bovendien niet alleen met de ogen gereviewd, maar ook echt
  *uitgevoerd*: met Python's ingebouwde `sqlite3`-module is het exacte v1-schema (op basis van
  Room's eigen DDL-conventies) nagebouwd, gevuld met voorbeelddata, en is de *letterlijke*
  migratie-SQL uit `Migrations.kt` erop losgelaten — 15 assertions, allemaal geslaagd: kolommen
  bestaan met het juiste type/nullability, bestaande rijen blijven intact, FK's en cascade-deletes
  werken op de nieuwe tabellen, en de nieuwe UNION-gebaseerde `observeSpent`/`observeCategoryTotal`
  (die een gesplitste transactie's toewijzingen meetellen zonder dubbel te tellen) rekenen correct.
  Dit is voor het eerst in dit traject een Room-schemawijziging die daadwerkelijk gedraaid is in
  plaats van alleen gelezen — sterker dan alle eerdere `:app`-reviews, al blijft de Room/Hilt/
  Compose-integratie eromheen (zoals altijd) niet in deze sandbox te bouwen. `:core` (het model,
  `SplitValidation`, `SubscriptionDetector`, `SafeToSpendCalculator`, `BudgetEvaluator.
  effectiveLimit()`) is wél volledig unit-getest: **88 tests groen, was 64**.
- **ING-Tag zichtbaar en doorzoekbaar.** `CsvIngParser` leest nu ook ING's eigen "Tag"-kolom uit
  (een label dat je zelf in Mijn ING aan een transactie kunt hangen, bijv. "Vakantie 2024") —
  optioneel, geen harde eis zoals de verplichte kolommen. Getoond als klein label naast de
  tegenpartijnaam in Transacties, en meegenomen in de zoekbalk (naast naam/omschrijving).
- **Budgetrollover.** `BudgetEntity.rollover` bestond al sinds het allereerste schema maar werd
  nergens gelezen — nu daadwerkelijk toegepast: onbenut budget van vorige maand (limiet min
  besteed, nooit negatief) telt als bonusruimte bij deze maand op, aan/uit te zetten per categorie
  in Instellingen. Een overschrijding vorige maand wordt nooit als straf doorgerekend — rollover
  helpt alleen, het verkleint nooit.
- **Saldoverloop-grafiek.** Nieuwe modus in Grafieken (naast maand-op-maand/jaar-op-jaar) die het
  banksaldo na elke dag met transactie-activiteit als lijn toont, met een gestippelde nullijn zodra
  het bereik door nul heen gaat. ING's CSV bevat geen tijdstip, alleen een datum — bij meerdere
  transacties op dezelfde dag is er dus geen waterdicht signaal voor de volgorde binnen die dag;
  gekozen voor een gedocumenteerde, deterministische benadering (de laatst-ingevoerde transactie
  van die dag) in plaats van te gokken.
- **Abonnementendetectie, spaardoelen, gesplitste transacties, veilig-te-besteden, meerdere
  rekeningen.** De rest van dezelfde functionaliteitenbatch als de schemamigratie hierboven — vier
  nieuwe, zichtbare features op dezelfde v1→v2-fundering. `SplitValidation` en
  `SubscriptionDetector`/`SafeToSpendCalculator` waren al `:core`-zijdig klaar en getest; dit
  koppelt ze aan de UI, plus twee nieuwe `:core`-methodes (`observeSplitTransactionIds`,
  ongewijzigd verder qua logica — puur interface-uitbreiding, geen nieuwe tests nodig) om een
  gesplitste transactie in Transacties als "Gesplitst" te tonen in plaats van als "niet
  gecategoriseerd" (haar eigen `categoryId` is null, per ontwerp — zie de vorige migratie-entry).
  Meerdere rekeningen relaxt de eerder overal hardgecodeerde `DefaultAccount.ID`: een
  rekeningkiezer in Importeren (alleen zichtbaar zodra een tweede rekening bestaat), een nieuw
  "Rekeningen"-scherm om ze te beheren, en rekeningfilters in Transacties en Grafieken (Grafieken
  telt bewust nooit de saldi van twee rekeningen bij elkaar op — dat is geen zinvol getal).
  `applyCategoryToCounterparty`'s "toepassen op de rest"-actie is nu ook expliciet per rekening
  gescopet in plaats van altijd op `DefaultAccount.ID`. Alles hier is `:app`-laag (UI/ViewModels)
  en dus zoals gebruikelijk niet in deze sandbox te bouwen; `:core` blijft ongewijzigd qua
  gedrag en dus nog steeds 88 tests groen.
- **Lokale meldingen (budgetdrempel + wekelijkse samenvatting).** Laatste feature van deze batch,
  en de enige die een nieuwe afhankelijkheid nodig had (`androidx.work:work-runtime-ktx`, voor de
  wekelijkse samenvatting) en een manifest-wijziging (`POST_NOTIFICATIONS`, vereist vanaf
  Android 13 — puur voor meldingen die de app zelf en alleen lokaal genereert, dus geen inbreuk op
  de bewuste "geen netwerkpermissie"-keuze bovenin het manifest; het bestand is bijgewerkt om dat
  expliciet te maken).
  - **Budgetdrempel**: `BudgetEvaluator.crossedIntoWorseStatus()` (nieuw, `:core`, 8
    parametrisatie-tests) bepaalt of een categorie net erger is geworden (OK→WARNING,
    OK→OVER, WARNING→OVER) — niet bij een verbetering, en niet opnieuw bij een al-OVER categorie
    die nog verder over gaat. `BudgetThresholdNotifier` (`:app`) neemt vóór elke categorisatie-
    schrijfactie (`TransactionsViewModel.categorize`/`applyCategoryToCounterparty`,
    `ImportViewModel.confirm`) een momentopname van het besteedde bedrag, en vergelijkt die na de
    schrijfactie.
  - **Wekelijkse samenvatting**: `WeeklyDigestWorker`, een `CoroutineWorker` met bewust alléén de
    standaard `(Context, WorkerParameters)`-constructor — WorkManager's eigen standaard-factory
    kan die zonder verdere hulp bouwen, dus is er geen `androidx.hilt:hilt-work`,
    `HiltWorkerFactory` of `Configuration.Provider`-gedoe nodig; Hilt-repositories worden in plaats
    daarvan via een `@EntryPoint` (`EntryPointAccessors.fromApplication`) opgehaald, een
    standaard-Hilt-feature die geen extra afhankelijkheid vereist. Elke 7 dagen, geregistreerd
    (idempotent) bij elke app-start; controleert zelf `AppPreferences.notificationsEnabled` bij
    elke run in plaats van bij het aan/uit zetten van de instelling geannuleerd/opnieuw ingepland
    te worden.
  - **Instellingen** kreeg een "Meldingen"-schakelaar (uit by default); aanzetten vraagt op
    Android 13+ meteen de systeemtoestemming aan via `ActivityResultContracts.RequestPermission()`
    — een weigering laat de schakelaar gewoon aan staan, `NotificationHelper` controleert de
    daadwerkelijke toestemming zelf vlak vóór elke melding en doet dan stilzwijgend niets in plaats
    van te crashen.
  - **Niet onafhankelijk geverifieerd, in tegenstelling tot elke andere versie in dit bestand**:
    `androidx.work:work-runtime-ktx`'s versienummer (`2.10.0`) staat op AndroidX's eigen
    Maven-repository (`dl.google.com`), die — net als voor Room, Compose en Navigation eerder in
    dit traject — vanuit deze sandbox niet bereikbaar is; in tegenstelling tot de eerdere
    Hilt/Room/Kotlin-versiebumps kon dit dus dit keer niet eens achteraf met een losse `curl`
    tegen Maven Central bevestigd worden (Dagger/Hilt staat daar wél op, AndroidX-artefacten
    principieel niet). Controleer dit nummer in Android Studio (of laat het gewoon de nieuwste
    stabiele versie voorstellen) vóór je hierop vertrouwt.
  - De meldingsiconen hergebruiken `ic_launcher` (net als het launcher-icoon zelf) in plaats van
    een apart, voor de statusbalk geoptimaliseerd monochroom icoon — functioneel correct (Android
    negeert kleur toch en toont alleen het alfakanaal als silhouet vanaf API 21), maar niet
    visueel geverifieerd, om dezelfde reden als de rest van deze `:app`-laag.
  - `:core`: 96 tests groen (was 88). De rest (`NotificationHelper`, `BudgetThresholdNotifier`,
    `WeeklyDigestWorker`, de Instellingen-toggle, de manifest-/Gradle-wijzigingen) is `:app`-laag
    en dus zoals altijd niet in deze sandbox te bouwen — hier komt bovenop dat zelfs de
    afhankelijkheidsversie niet extern te bevestigen was, dus dit stuk verdient bij het eerste
    echte bouwen in Android Studio extra aandacht.
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
- Importeren van categorieën/regels overschrijft nooit iets bestaands (zelfde ontwerpkeuze als
  Categorieën & regels beheren) — een categorie waarvan je lokaal de kleur hebt veranderd, of een
  regel waarvan je de prioriteit hebt aangepast, blijft dus ongewijzigd staan bij een hernieuwde
  import van hetzelfde bestand. Bewuste keuze (nooit stilzwijgend een bestaande instelling
  overschrijven), geen bug, maar wel iets om te weten als je verwacht dat importeren ook bestaande
  items bijwerkt.
- "Ook toepassen op de rest?" na het categoriseren van één transactie kijkt alleen naar transacties
  die al in de lijst staan (dus al geïmporteerd zijn) — het is geen vervanging voor de
  regel-gebaseerde automatische categorisatie bij een volgende import, die blijft apart bestaan.
- De budgetdrempel-melding kijkt alleen naar categorisatie-momenten (handmatig categoriseren,
  "toepassen op de rest", importeren) — niet naar het wijzigen van een budgetlimiet zelf. Een
  limiet verlagen tot ver onder wat al besteed is, geeft dus pas bij de eerstvolgende
  categorisatie een melding, niet meteen.
- De wekelijkse samenvatting-melding is een vast interval vanaf het moment van de eerste
  app-start (via `WorkManager`s `PeriodicWorkRequest`), niet gekoppeld aan een vaste dag/tijd
  (bijv. altijd zondagavond) — dat zou een `Constraints`/tijdvenster-berekening vereisen die de
  moeite niet waard leek voor fase 1.

## Ontwerpdocumenten

De routekaart, technische architectuur en het schermontwerp waarop dit is gebaseerd zijn per
artifact met de projecteigenaar gedeeld tijdens het ontwerptraject.
