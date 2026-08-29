# Numbers (Digits)

**Nyelv:** [English](README-en.md) · Magyar

Napi számrejtvény Androidra — a megszűnt NY Times Digits játék szellemében.
Hat számot kell `+ − × ÷` műveletekkel kombinálni, hogy eltaláld a napi öt célt.

Eredeti Angular implementáció (2023): [github.com/lkovari/LKovariHome — `src/app/digits`](https://github.com/lkovari/LKovariHome/commits/master/src/app/digits)

A weben: [lkovari.github.io/LKovariHome/#/digits/digits-game](https://lkovari.github.io/LKovariHome/#/digits/digits-game)

Adatvédelmi tájékoztató (EN/HU, Play Console URL): [numbers-privacy-policy.html](https://lkovari.github.io/KLHome/assets/bigfiles/numbers-privacy-policy.html)

Helyi másolat: [`docs/play-console/privacy-policy.html`](docs/play-console/privacy-policy.html)

`applicationId` / namespace: `com.lkovari.mobile.apps.digits`  
Indítóikon neve: **Numbers**. Csak álló mód. Splash (~500 ms), utána a játék.

## Stack

| Elem | Részlet |
|------|---------|
| UI | Kotlin + Jetpack Compose (Material 3), Compose BOM `2025.12.00` |
| SDK | minSdk 24 / compileSdk 36 / targetSdk 36 / verzió `1.0.0` (`versionCode` 1) |
| JVM | Java 11 / Kotlin `2.2.10` / Gradle `9.4.1` / AGP `9.2.1` |
| Backend | Firebase Firestore projekt `numbers-55698`, kollekció `puzzledata` |
| Helyi | DataStore Preferences `numbers_progress` (ugyanaz a naptári nap; load-first visszaállítás) |
| Aláírás | Közös EKL release keystore (ugyanaz a minta, mint a sensors-s-nél) |
| Nyelvek | Angol (`values`) és magyar (`values-hu`) |
| Engedély | `INTERNET` |

A `google-services.json` **kötelező** a buildhez: a Google Services Gradle plugin már be van kötve. Tedd ide: `app/google-services.json` (gitignore-ozva).

## Játékmenet (ahogy implementálva van)

A játék sorrendhez kötött: az öt szintet egymás után kell megoldani. Egy kész / másik szint chipjére koppintva nem lehet ugrani.

1. Válassz egy számot, majd egy műveletet (`+ − × ÷`), aztán egy második számot.
2. A bal operandus letiltódik; az eredmény a jobb operandus helyére kerül.
3. **Nem** kell mind a hat számot felhasználni. Egy szint csak akkor kész, ha az eredmény **egyenlő a céllal**.
4. A visszavonás az előző táblát állítja vissza. **Nem** ír új lépést a megosztáshoz használt megoldástörténetbe.
5. Érvénytelen művelet (negatív kivonás, nem osztható osztás, nullával osztás) „Érvénytelen művelet” toastot mutat, törli a kijelölést, a tábla és a történet változatlan marad.

Szabályok az `Arithmetic`-ben:

- Az összeadás és a szorzás mindig sikerül.
- A kivonás akkor engedélyezett, ha `A >= B` (a nulla is beleértendő).
- Az osztás csak akkor engedélyezett, ha `B != 0` és `A` osztható `B`-vel (csak egész szám; tört nincs).

A beépített Súgó szövege még mindig NYT-stílusú **csillagokat** ír a közelítésre (1–10 és 11–25 eltérés). Az Android motor nem pontozza a közelítéseket; csak a pontos találat zárja a szintet.

## Játék UI

- Hat kör alakú számgomb (`90.dp`).
- Műveletsor: visszavonás, `+`, `−`, `×`, `÷`. Az átmérő a számgomb **háromnegyede** (`67.5.dp`). Mind az öt **egy sorban** marad.
- Ez a sor a teljes képernyőszélességet használja; a többi tartalom `16.dp` oldalsó paddingot kap. Ne használj negatív Compose `padding`et (összeomlik: a padding nem lehet negatív).
- Paletta: wash `#EBF9FD` + `#1E88E5` (nem a NYT Digits zöldje).

## Elrendezés

```
app/src/main/java/com/lkovari/mobile/apps/digits/
  domain/     Arithmetic, GameEngine, PuzzleGenerator, models, Operator
  data/       DailySessionLoader, PuzzleDataCodec, NetworkStatusChecker, firestore/, prefs/
  ui/         game/ (képernyő + ViewModel), splash/, theme/
app/src/test/java/.../digits/
  domain/DomainTest.kt
  data/DataEdgeCasesTest.kt
  data/DailySessionLoaderTest.kt
```

Az indítás **load-first**. A `DigitsViewModel` spinnert mutat (`loading = true`), és a `DailySessionLoader`-től kéri a mai sessiont. **Nem** generál táblát és **nem** ír a DataStore-ba, amíg ez az ellenőrzés be nem fejeződik. Ez elkerüli azt a versenyhelyzetet, amely hidegindításkor felülírhatott egy már kész szintet.

## Napi rejtvény és haladás

**Generátor** (`PuzzleGenerator`): öt nehézségi sáv (célértékek nagyjából 30–100-tól 400–550-ig). Minden szintnek hat egyedi operandusa van. A szintek célérték szerint rendezettek; a `stageIndex` rendezés után `0..4`-re indexelődik újra. A mintavételezésnek és a célsáv-ciklusoknak kemény korlátjuk van; ha a generálás elbukik, a `{1,2,3,4,5,10}` tartalék tábla kerül használatra.

**Firestore** (egyszeri `get()` a `puzzledata` kollekción, nem élő snapshot):

- Dokumentummezők: `locale`, `data` (JSON string).
- Nyelvegyeztetés: normalizált címke, vagy elsődleges nyelv (`en-US` ↔ `en`).
- A tárolt rejtvény csak akkor használatos, ha a `day` **ugyanaz a naptári nap**, mint most; különben az app generál és upsertel.

A `data` JSON alakja:

```json
{
  "day": 1700000000000,
  "locale": "en-US",
  "stages": [
    {
      "stageIndex": 0,
      "expectedValue": 39,
      "operands": [1, 2, 3, 4, 5, 10]
    }
  ]
}
```

**Hidegindítás** (`DailySessionLoader.load`):

1. DataStore olvasása. Ha van aznapi haladás → **visszaállítás** (kész szintek, aktuális szint, megmaradt számok). Ne generálj, ne tölts le, ne upsertelj.
2. Különben, ha van hálózat, és a Firestore-ban van nem üres **mai** rejtvény → azt a táblát használd.
3. Különben **generálj** öt szintet helyben. Ha van hálózat, upserteld a rejtvényt Firestore-ba (új dokumentum, vagy a locale meglévő doksijának felülírása, ha a `day` elavult). Ha offline vagy az upsert elbukik, akkor is a generált táblával lehet játszani, és szinkron banner jelenik meg.

Ezután a friss session beíródik a DataStore-ba, hogy az app elhagyása (vagy ha az Android kilövi a folyamatot) ne veszítse el a táblát. A haladás pillanatképe a mentő coroutine előtt készül, az írás `NonCancellable`, így a ViewModel törlése kevésbé hagyja el az utolsó kész szintet.

**DataStore**: Preferences fájl `numbers_progress`, kulcs `daily_progress`. A betöltés `null`-t ad (a nap frissnek számít), ha a mentett `day` nem a mai. A folyamatban lévő operandus `disabled` flagjei perzisztálódnak, hogy egy félkész tábla folytatható legyen. Túléli a folyamat halálát és a recés eltávolítást. **Nem** éli túl a Beállítások → Tárhely törlése / eltávolítás műveletet (kivéve, ha az Android biztonsági mentés visszaállítja). A Firestore a **napi rejtvényt** tárolja, nem azt, hogy a felhasználó befejezett-e egy szintet.

**Offline / Firestore hibák**: ha nincs internet, vagy a Firestore kivételt dob, az app akkor is **helyi** napi rejtvénnyel indul, piros bannert mutat, és **Újra** plusz **Elrejtés** gombot kínál. Osztályozás:

| Helyzet | `SyncIssue` |
|---------|-------------|
| Nincs hiba | `NONE` |
| Offline, vagy az üzenet host/timeout/connection jellegű | `NO_INTERNET` |
| Egyéb kivételek „online” állapotban (pl. permission-denied) | `DATABASE_UNAVAILABLE` |

Az aznapi haladás mindkét esetben a készüléken marad. Az Újra újraellenőrzi a hálózatot és a szinkront, **anélkül hogy lecserélne egy már szintekkel rendelkező táblát** (beleértve a visszaállított haladást). A fejléc **Numbers** felirata és a mai dátum csak megjelenítés; nincs koppintáskezelőjük.

## Tesztek

Csak JUnit 4 **egységtesztek** (`app/src/test`), plusz `kotlinx-coroutines-test` a `DailySessionLoaderTest`-hez. Nincs `androidTest` source set: nincs Espresso / Compose UI teszt, nincs emulátor teszt, nincs Firestore vagy DataStore integrációs teszt.

### Futtatás

A repo gyökeréből (kell Android SDK a `local.properties`-ben):

```bash
# Minden egységteszt (debug + release variáns)
./gradlew :app:test

# Csak debug variáns (szokásos helyi futtatás)
./gradlew :app:testDebugUnitTest

# Egy osztály
./gradlew :app:testDebugUnitTest --tests com.lkovari.mobile.apps.digits.domain.ArithmeticEdgeCasesTest
./gradlew :app:testDebugUnitTest --tests com.lkovari.mobile.apps.digits.data.DailySessionLoaderTest

# Egy metódus
./gradlew :app:testDebugUnitTest --tests com.lkovari.mobile.apps.digits.domain.ArithmeticEdgeCasesTest.divisionRequiresExactNonZeroDivisor
```

HTML jelentés: `app/build/reports/tests/testDebugUnitTest/index.html`.

Android Studio-ban: jobb klikk egy tesztosztályra/metódusra vagy a `test` source setre → Run. JDK 11+ toolchain kell.

### `domain/DomainTest.kt`

**`ArithmeticEdgeCasesTest`** — egész számú műveletek az `Arithmetic.evaluate`-ben

| Teszt | Mit fed le |
|-------|------------|
| `addMultiplyAlwaysWork` | `4+5=9`, `4×5=20` |
| `subtractionAllowsEqualAndRejectsNegative` | `5−5=0`, `5−3=2`, `3−5` érvénytelen |
| `divisionRequiresExactNonZeroDivisor` | `6÷2=3`; `5÷2` és `5÷0` érvénytelen |
| `undoOperatorIsInvalidInEvaluate` | Az `UNDO` nem numerikus művelet |

**`GameEngineEdgeCasesTest`** — kijelölés, alkalmazás, visszavonás, történet

| Teszt | Mit fed le |
|-------|------------|
| `requiresOperatorBetweenOperands` | A második számot figyelmen kívül hagyja, amíg nincs művelet |
| `ignoresOperatorWhenNoOperandSelected` | Művelet bal operandus nélkül no-op |
| `ignoresClicksOnDisabledOperands` | A felhasznált (letiltott) számokra nem lehet koppintani |
| `invalidDivisionClearsSelectionAndDoesNotChangeBoard` | A nem osztható osztás érvénytelen útra visz; tábla és megoldáslépések változatlanok |
| `invalidSubtractionDoesNotPolluteHistory` | A `2−9` nem hagy hamis undo/történet bejegyzést |
| `successfulOpDisablesLeftAndWritesResultOnRight` | A `10−5` letiltja a 10-et, és `5`-öt ír jobbra |
| `undoRestoresBoardWithoutAddingSolutionStep` | Az undo kiveszi a táblát; a megoldáslépések száma marad |
| `clearAllHistoryWipesBoardAndSolutionStacks` | Szintkész után az undónak nincs mit kivennie |
| `deselectingSelectedOperandClearsIt` | A kijelölt számra újra koppintva a kijelölés törlődik |
| `unknownOperandIdIsNoOp` | Ismeretlen id változatlanul hagyja a táblát |
| `equationFormattingUsesOperatorSymbols` | `10 - 5 = 5` a megosztás / szintkész sorokhoz |

**`PuzzleGeneratorEdgeCasesTest`** — konstruktív generálás korlátai és alakja

| Teszt | Mit fed le |
|-------|------------|
| `alwaysReturnsFiveStagesWithSixOperandsSortedByTarget` | 8 seed: 5 szint, indexek `0..4`, 6 egyedi operandus, pozitív célok, cél szerint rendezve |
| `differentSeedsUsuallyDiffer` | A 11-es és 99-es seed nem ugyanazt a cél/operandus listát adja |

### `data/DataEdgeCasesTest.kt`

**`SyncIssueMessagesTest`** — banner osztályozás (nincs Android hálózati API)

| Teszt | Mit fed le |
|-------|------------|
| `noErrorMeansNone` | `error == null` → `NONE`, nincs üzenet |
| `offlineFlagMapsToNoInternet` | `isOnline=false` + `IOException` → `NO_INTERNET` |
| `networkishExceptionsMapToNoInternetWhenOnlineFlagTrue` | `UnknownHostException` / „connection timeout” akkor is `NO_INTERNET` |
| `otherExceptionsMapToDatabaseUnavailable` | pl. `permission-denied` → `DATABASE_UNAVAILABLE` |
| `messagesAreUserFacing` | A szöveg „internet” / „database” jellegű |

**`PuzzleDataCodecTest`** — Firestore `data` JSON és DataStore haladás JSON

| Teszt | Mit fed le |
|-------|------------|
| `puzzleDayRoundTrip` | A szerializálás/parse megőrzi a napot, locale-t, célt, operandusértékeket |
| `progressRoundTripPreservesDisabledFlags` | Félkész `disabled`, összefoglalók, `stageIndex` túlélik a round-tripet |
| `invalidJsonReturnsNull` | Csonka `{` és `"not-json"` `null`-t ad (nem dob) |

**`PuzzleFirestoreHelpersTest`** — locale és naptár segédek (nincs élő Firestore)

| Teszt | Mit fed le |
|-------|------------|
| `localeNormalizationAndPrimaryLanguage` | `" En-US "` → `en-us`; `en-US` → `en`; `hu_HU` → `hu` |
| `sameCalendarDayIgnoresTimeOfDay` | 08:00 és 22:30 ugyanaz a nap; a következő naptári nap nem |
| `endOfTodayIsLateEvening` | Az `endOfTodayMillis()` helyi idő szerint 23:59 |

### `data/DailySessionLoaderTest.kt`

**`DailySessionLoaderTest`** — hidegindítás visszaállítás vs. generálás/lekérés (`kotlinx-coroutines-test` / `runTest`, csak fake-ek)

| Teszt | Mit fed le |
|-------|------------|
| `sameDayProgressIsRestoredWithoutGeneratingOrFetching` | Mentett 1. szint kész → `Restored`; nincs generálás, lookup vagy upsert |
| `restoredProgressKeepsCompletedStageWhenOffline` | Ugyanaz offline → `NO_INTERNET`, továbbra sincs generálás/upsert |
| `missingLocalProgressUsesTodaysRemotePuzzle` | Üres DataStore + mai Firestore rejtvény → `Fresh` a távoliból |
| `missingLocalProgressGeneratesOfflineWithoutUpsert` | Üres DataStore + offline → generált tábla, `NO_INTERNET` |
| `missingRemotePuzzleGeneratesAndUpserts` | Üres DataStore + nincs mai távoli → generálás és upsert |

### Nem lefedett (hiányok)

ViewModel `retrySync` bekötés, valódi Firestore, valódi DataStore I/O, Compose elrendezés (műveletsor), splash és ShareSheet. A bootstrap **döntési** sorrendet a `DailySessionLoaderTest` lefedi. A többi felülethez instrumentált vagy fake-repository tesztek kellenek, ha később hozzáadod őket.

## Build

A repo gyökeréből. Kell Android SDK a `local.properties`-ben (`sdk.dir=...`, gitignore-ozva), plusz `app/google-services.json`.

```bash
cp keystore.properties.example keystore.properties
# töltsd ki a storePassword / keyPassword mezőket (EKL)

./gradlew :app:test
./gradlew :app:installDebug
```

Release: R8 minify + resource shrink, `debugSymbolLevel = SYMBOL_TABLE`, ProGuard megtartja a Firebase/GMS-t. **Aláíratlan**, ha hiányzik a `keystore.properties`.

### Debug APK (aláíratlan)

```bash
./gradlew :app:assembleDebug
```

Kimenet: `app/build/outputs/apk/debug/app-debug.apk`

### Aláírt release APK

A `keystore.properties` `release` signing configját használja (lásd [Aláírás (EKL)](#aláírás-ekl)).

```bash
./gradlew :app:assembleRelease
```

Kimenet: `app/build/outputs/apk/release/app-release.apk`

Ellenőrizd, hogy az APK az EKL feltöltőkulccsal van aláírva:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Az `apksigner` az Android SDK-val jön (`build-tools/<version>/apksigner`). A SHA-1-nek egyeznie kell a [Firebase](#firebase) Release ujjlenyomatával.

### Aláírt release AAB (Play Console)

A Play Store feltöltés Android App Bundle, nem APK. Ugyanaz az aláírás, mint a release APK-nál:

```bash
./gradlew :app:bundleRelease
```

Kimenet: `app/build/outputs/bundle/release/app-release.aab`

Ezt a `.aab` fájlt töltsd fel a Play Console → Production / Testing → Create release helyen. Ha a Play App Signing be van kapcsolva, a Google újraaláírja, amit a felhasználók telepítenek; a helyi AAB az **upload** kulccsal (EKL) van aláírva.

Mindkét artifact egyszerre:

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

## Aláírás (EKL)

A production EKL keystore-t használd (ne a TreeCalc `upload-keystore.jks`-ét):

```
storeFile=/Users/kovarilaszlo/android-keystores/keystores/release/ekl-release-key_v36500.keystore
keyAlias=ekldroidapps
storePassword=...
keyPassword=...
```

A `keystore.properties` gitignore-ozva van. Példa: `keystore.properties.example`.

## Firebase

Projekt **numbers-55698**, kollekció `puzzledata`, Android app id `com.lkovari.mobile.apps.digits`.

1. Töltsd le az `app/google-services.json` fájlt, és tartsd git-en kívül.
2. Add hozzá **mindkét** SHA-1 ujjlenyomatot: Project settings → Your apps → Android app → Add fingerprint, majd töltsd le újra a `google-services.json`-t (hogy az `oauth_client` ne legyen üres):

| Build | SHA-1 |
|-------|-------|
| Debug | `71:1C:92:BE:26:C5:AE:3E:51:CC:DE:D5:05:35:0D:FE:D5:AB:35:8B` |
| Release (EKL upload kulcs) | `78:96:DE:F3:EC:86:32:E6:E7:BC:9C:99:9D:A7:BB:31:96:C2:84:0C` |

A Play App Signing bekapcsolása után add hozzá a **App signing key certificate** SHA-1-et is: Play Console → App integrity.

A Logcat `GoogleApiManager … Unknown calling package name 'com.google.android.gms'` emulátoron általában Play Services / emulátorkép probléma, nem app-hiba, és nem Play Store elutasítás. Google Play rendszerképet vagy fizikai eszközt használj az ellenőrzéshez.

## Play Store listaszövegek és grafikák

Másolható szövegek, űrlapválaszok és grafikák a Google Play Console-hoz:

| Fájl | Tartalom |
|------|----------|
| [`store/listing-en.txt`](store/listing-en.txt) | EN név, rövid és teljes leírás, kapcsolat, kategória |
| [`store/listing-hu.txt`](store/listing-hu.txt) | HU név, rövid és teljes leírás |
| [`store/whatsnew-en.txt`](store/whatsnew-en.txt) | EN kiadási megjegyzések (1.0.0) |
| [`store/whatsnew-hu.txt`](store/whatsnew-hu.txt) | HU kiadási megjegyzések (1.0.0) |
| [`store/play-console-form-answers.md`](store/play-console-form-answers.md) | Data safety, hirdetés, besorolás, célközönség és kapcsolódó válaszok |

## Márkagrafikák (Play-kész)

| Asset | Spec | Helyszín |
|-------|------|----------|
| **Adaptive foreground** | 432×432 RGBA, a körön kívül átlátszó | `app/src/main/res/drawable/ic_launcher_foreground.png` |
| **Legacy launcher** | mdpi→xxxhdpi RGB | `app/src/main/res/mipmap-*` |
| **In-app logo** | 512×512 RGBA átlátszó | `app/src/main/res/drawable/ic_numbers_logo.png` |
| **Play high-res icon** | 512×512 RGB (nincs alfa) | [`store/hi-res-icon-512.png`](store/hi-res-icon-512.png) |
| **Play feature graphic (EN)** | 1024×500 RGB | [`store/feature-graphic.png`](store/feature-graphic.png) |
| **Play feature graphic (HU)** | 1024×500 RGB | [`store/feature-graphic-hu.png`](store/feature-graphic-hu.png) |
| **Telefonscreenshotok** | 1080×1920 RGB, 24 bites PNG | [`store/screenshots/`](store/screenshots/) |
| **Átlátszó mester** | 512×512 RGBA | [`store/launcher-icon-512-transparent.png`](store/launcher-icon-512-transparent.png) |

## Angular referenciahibák

Az ismert webes hibák és az Android algoritmusjavítások a [`lkovarihome-errors.md`](lkovarihome-errors.md) fájlban vannak. Az Angular forrásokat ez a repo nem módosítja. Androidon a megosztás a rendszer Share sheetjét használja (`Intent.ACTION_SEND`), nem a vágólapot.
