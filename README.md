# Numbers (Digits)

Daily arithmetic puzzle for Android — inspired by the discontinued NY Times Digits game.
Combine six numbers with `+ − × ÷` to hit each day’s five targets.

Web sibling: [LKovariHome Numbers](https://lkovari.github.io/LKovariHome/#/digits/digits-game)

Privacy policy (EN/HU, Play Console URL): [numbers-privacy-policy.html](https://lkovari.github.io/KLHome/assets/bigfiles/numbers-privacy-policy.html)

Local copy: [`docs/play-console/privacy-policy.html`](docs/play-console/privacy-policy.html)

`applicationId` / namespace: `com.lkovari.mobile.apps.digits`  
Launcher name: **Numbers**. Portrait only. Splash (~500 ms) then the game.

## Stack

| Piece | Detail |
|-------|--------|
| UI | Kotlin + Jetpack Compose (Material 3), Compose BOM `2025.12.00` |
| SDK | minSdk 24 / compileSdk 36 / targetSdk 36 / version `1.0.0` (`versionCode` 1) |
| JVM | Java 11 / Kotlin `2.2.10` / Gradle `9.4.1` / AGP `9.2.1` |
| Backend | Firebase Firestore project `numbers-55698`, collection `puzzledata` |
| Local | DataStore Preferences `numbers_progress` (same calendar day; load-first restore) |
| Signing | Shared EKL release keystore (same pattern as sensors-s) |
| Locales | English (`values`) and Hungarian (`values-hu`) |
| Permission | `INTERNET` |

`google-services.json` is **required** to build: the Google Services Gradle plugin is already applied. Place it at `app/google-services.json` (gitignored).

## Gameplay (as implemented)

Play is sequential: the five stages must be solved in order. Tapping a completed/other stage chip does not jump stages.

1. Select a number, then an operator (`+ − × ÷`), then a second number.
2. The left operand is disabled; the result replaces the right operand.
3. You do **not** have to use all six numbers. A stage completes only when a result **equals the target**.
4. Undo restores the previous board. It does **not** append a step to the solution history used for share text.
5. Invalid ops (negative subtraction, inexact division, divide by zero) toast “Invalid operation”, clear the selection, and leave the board and history unchanged.

Rules in `Arithmetic`:

- Addition and multiplication always succeed.
- Subtraction is allowed when `A >= B` (including zero).
- Division is allowed only when `B != 0` and `A` is divisible by `B` (integers only; no fractions).

The in-app Help text still describes NYT-style **stars** for near-misses (1–10 and 11–25 away). The Android engine does not score near-misses; only an exact hit completes the stage.

## Game UI

- Six circular number buttons (`90.dp`).
- Operator row: undo, `+`, `−`, `×`, `÷`. Diameter is **three-quarters** of a number button (`67.5.dp`). All five stay on **one line**.
- That row uses the full screen width; other content keeps `16.dp` side padding. Do not use negative Compose `padding` (it crashes: padding must be non-negative).
- Palette: wash `#EBF9FD` + `#1E88E5` (not NYT Digits green).

## Layout

```
app/src/main/java/com/lkovari/mobile/apps/digits/
  domain/     Arithmetic, GameEngine, PuzzleGenerator, models, Operator
  data/       DailySessionLoader, PuzzleDataCodec, NetworkStatusChecker, firestore/, prefs/
  ui/         game/ (screen + ViewModel), splash/, theme/
app/src/test/java/.../digits/
  domain/DomainTest.kt
  data/DataEdgeCasesTest.kt
  data/DailySessionLoaderTest.kt
```

Startup is **load-first**. `DigitsViewModel` shows a spinner (`loading = true`) and asks `DailySessionLoader` for today’s session. It does **not** generate a board or write DataStore until that check finishes. That avoids a race that used to overwrite a completed stage on cold start.

## Daily puzzle and progress

**Generator** (`PuzzleGenerator`): five difficulty bands (target ranges roughly 30–100 through 400–550). Each stage has six unique operands. Stages are sorted by target; `stageIndex` is reindexed to `0..4` after sort. Sampling and target-band loops have hard caps; a fallback board `{1,2,3,4,5,10}` is used if generation fails.

**Firestore** (one-shot `get()` of `puzzledata`, not a live snapshot):

- Document fields: `locale`, `data` (JSON string).
- Locale match: normalized tag, or primary language (`en-US` ↔ `en`).
- A stored puzzle is used only if `day` is the **same calendar day** as now; otherwise the app generates and upserts.

`data` JSON shape:

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

**Cold start** (`DailySessionLoader.load`):

1. Read DataStore. If same-calendar-day progress exists → **restore** it (completed stages, current stage, remaining numbers). Do not generate, fetch, or upsert.
2. Else if online and Firestore has a non-empty puzzle for **today** → use that board.
3. Else **generate** five stages locally. If online, upsert that puzzle to Firestore (new document, or overwrite the locale’s existing doc when its `day` is stale). If offline or upsert fails, still play the generated board and show a sync banner.

A fresh session is then written to DataStore so leaving the app (or Android killing the process) does not lose the board. Progress snapshots are taken before the save coroutine runs, and the write uses `NonCancellable` so clearing the ViewModel is less likely to drop the last completed stage.

**DataStore**: Preferences file `numbers_progress`, key `daily_progress`. Load returns `null` (day treated as fresh) if the saved `day` is not today. In-progress operand `disabled` flags are persisted so a mid-stage board can resume. Survives process death and swipe-away. **Does not** survive Settings → Clear storage / uninstall (unless Android backup restores it). Firestore stores the **daily puzzle**, not whether the user finished a stage.

**Offline / Firestore errors**: if there is no internet or Firestore throws, the app still starts a **local** daily puzzle, shows a red banner, and offers **Retry** and **Dismiss**. Classify:

| Situation | `SyncIssue` |
|-----------|-------------|
| No error | `NONE` |
| Offline, or message looks like host/timeout/connection | `NO_INTERNET` |
| Other exceptions while “online” (e.g. permission-denied) | `DATABASE_UNAVAILABLE` |

Same-day progress stays on-device either way. Retry re-checks network and sync **without replacing a board that already has stages** (including restored progress). The header **Numbers** label and today’s date are display-only; they have no tap handler.

## Tests

JUnit 4 **unit tests only** (`app/src/test`), plus `kotlinx-coroutines-test` for `DailySessionLoaderTest`. There is no `androidTest` source set: no Espresso / Compose UI tests, no emulator tests, no Firestore or DataStore integration tests.

### How to run

From the repo root (needs Android SDK in `local.properties`):

```bash
# All unit tests (debug + release variants)
./gradlew :app:test

# Debug variant only (usual local run)
./gradlew :app:testDebugUnitTest

# One class
./gradlew :app:testDebugUnitTest --tests com.lkovari.mobile.apps.digits.domain.ArithmeticEdgeCasesTest
./gradlew :app:testDebugUnitTest --tests com.lkovari.mobile.apps.digits.data.DailySessionLoaderTest

# One method
./gradlew :app:testDebugUnitTest --tests com.lkovari.mobile.apps.digits.domain.ArithmeticEdgeCasesTest.divisionRequiresExactNonZeroDivisor
```

HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

In Android Studio: right-click a test class/method or the `test` source set → Run. Use a JDK 11+ toolchain.

### `domain/DomainTest.kt`

**`ArithmeticEdgeCasesTest`** — integer ops in `Arithmetic.evaluate`

| Test | What it covers |
|------|----------------|
| `addMultiplyAlwaysWork` | `4+5=9`, `4×5=20` |
| `subtractionAllowsEqualAndRejectsNegative` | `5−5=0`, `5−3=2`, `3−5` is invalid |
| `divisionRequiresExactNonZeroDivisor` | `6÷2=3`; `5÷2` and `5÷0` invalid |
| `undoOperatorIsInvalidInEvaluate` | `UNDO` is not a numeric operator |

**`GameEngineEdgeCasesTest`** — selection, apply, undo, history

| Test | What it covers |
|------|----------------|
| `requiresOperatorBetweenOperands` | Second number is ignored until an operator is chosen |
| `ignoresOperatorWhenNoOperandSelected` | Operator with no left operand is a no-op |
| `ignoresClicksOnDisabledOperands` | Used (disabled) numbers cannot be tapped |
| `invalidDivisionClearsSelectionAndDoesNotChangeBoard` | Inexact divide toasts invalid path; board and solution steps unchanged |
| `invalidSubtractionDoesNotPolluteHistory` | `2−9` does not leave a fake undo/history entry |
| `successfulOpDisablesLeftAndWritesResultOnRight` | `10−5` disables 10 and writes `5` on the right |
| `undoRestoresBoardWithoutAddingSolutionStep` | Undo pops the board; solution step count stays the same |
| `clearAllHistoryWipesBoardAndSolutionStacks` | After stage complete, undo has nothing to pop |
| `deselectingSelectedOperandClearsIt` | Tapping the selected number again deselects it |
| `unknownOperandIdIsNoOp` | Unknown id leaves the board unchanged |
| `equationFormattingUsesOperatorSymbols` | `10 - 5 = 5` for share / stage-complete lines |

**`PuzzleGeneratorEdgeCasesTest`** — constructive generation caps and shape

| Test | What it covers |
|------|----------------|
| `alwaysReturnsFiveStagesWithSixOperandsSortedByTarget` | 8 seeds: 5 stages, indices `0..4`, 6 unique operands, positive targets, sorted by target |
| `differentSeedsUsuallyDiffer` | Seeds 11 vs 99 do not produce the same target/operand list |

### `data/DataEdgeCasesTest.kt`

**`SyncIssueMessagesTest`** — banner classification (no Android network APIs)

| Test | What it covers |
|------|----------------|
| `noErrorMeansNone` | `error == null` → `NONE`, no message |
| `offlineFlagMapsToNoInternet` | `isOnline=false` + `IOException` → `NO_INTERNET` |
| `networkishExceptionsMapToNoInternetWhenOnlineFlagTrue` | `UnknownHostException` / “connection timeout” still map to `NO_INTERNET` |
| `otherExceptionsMapToDatabaseUnavailable` | e.g. `permission-denied` → `DATABASE_UNAVAILABLE` |
| `messagesAreUserFacing` | Copy mentions “internet” / “database” |

**`PuzzleDataCodecTest`** — Firestore `data` JSON and DataStore progress JSON

| Test | What it covers |
|------|----------------|
| `puzzleDayRoundTrip` | Serialize/parse keeps day, locale, target, operand values |
| `progressRoundTripPreservesDisabledFlags` | Mid-stage `disabled`, summaries, `stageIndex` survive round-trip |
| `invalidJsonReturnsNull` | Truncated `{` and `"not-json"` parse as `null` (no throw) |

**`PuzzleFirestoreHelpersTest`** — locale and calendar helpers (no live Firestore)

| Test | What it covers |
|------|----------------|
| `localeNormalizationAndPrimaryLanguage` | `" En-US "` → `en-us`; `en-US` → `en`; `hu_HU` → `hu` |
| `sameCalendarDayIgnoresTimeOfDay` | 08:00 and 22:30 same day; next calendar day is not |
| `endOfTodayIsLateEvening` | `endOfTodayMillis()` is 23:59 local time |

### `data/DailySessionLoaderTest.kt`

**`DailySessionLoaderTest`** — cold-start restore vs generate/fetch (`kotlinx-coroutines-test` / `runTest`, fakes only)

| Test | What it covers |
|------|----------------|
| `sameDayProgressIsRestoredWithoutGeneratingOrFetching` | Saved stage 1 complete → `Restored`; no generate, lookup, or upsert |
| `restoredProgressKeepsCompletedStageWhenOffline` | Same restore offline → `NO_INTERNET`, still no generate/upsert |
| `missingLocalProgressUsesTodaysRemotePuzzle` | Empty DataStore + today’s Firestore puzzle → `Fresh` from remote |
| `missingLocalProgressGeneratesOfflineWithoutUpsert` | Empty DataStore + offline → generated board, `NO_INTERNET` |
| `missingRemotePuzzleGeneratesAndUpserts` | Empty DataStore + no today’s remote → generate and upsert |

### Not covered (gaps)

ViewModel `retrySync` wiring, real Firestore, real DataStore I/O, Compose layout (operator row), splash, and ShareSheet. Bootstrap **decision** order is covered by `DailySessionLoaderTest`. Those other surfaces need instrumented or fake-repository tests if you add them later.

## Build

From the repo root. Needs Android SDK in `local.properties` (`sdk.dir=...`, gitignored), plus `app/google-services.json`.

```bash
cp keystore.properties.example keystore.properties
# fill storePassword / keyPassword (EKL)

./gradlew :app:test
./gradlew :app:installDebug
```

Release: R8 minify + resource shrink, `debugSymbolLevel = SYMBOL_TABLE`, ProGuard keeps Firebase/GMS. **Unsigned** if `keystore.properties` is missing.

### Debug APK (unsigned)

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Signed release APK

Uses the `release` signing config from `keystore.properties` (see [Signing (EKL)](#signing-ekl)).

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Confirm the APK is signed with the EKL upload key:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

`apksigner` ships with the Android SDK (`build-tools/<version>/apksigner`). The SHA-1 should match the Release fingerprint in [Firebase](#firebase).

### Signed release AAB (Play Console)

Play Store uploads use an Android App Bundle, not an APK. Same signing config as the release APK:

```bash
./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

Upload that `.aab` in Play Console → Production / Testing → Create release. After Play App Signing is on, Google re-signs what users install; the local AAB is signed with the **upload** key (EKL).

Both artifacts in one go:

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

## Signing (EKL)

Use the production EKL keystore (not TreeCalc’s `upload-keystore.jks`):

```
storeFile=/Users/kovarilaszlo/android-keystores/keystores/release/ekl-release-key_v36500.keystore
keyAlias=ekldroidapps
storePassword=...
keyPassword=...
```

`keystore.properties` is gitignored. Example: `keystore.properties.example`.

## Firebase

Project **numbers-55698**, collection `puzzledata`, Android app id `com.lkovari.mobile.apps.digits`.

1. Download `app/google-services.json` and keep it out of git.
2. Add **both** SHA-1 fingerprints under Project settings → Your apps → Android app → Add fingerprint, then re-download `google-services.json` (so `oauth_client` is not empty):

| Build | SHA-1 |
|-------|-------|
| Debug | `71:1C:92:BE:26:C5:AE:3E:51:CC:DE:D5:05:35:0D:FE:D5:AB:35:8B` |
| Release (EKL upload key) | `78:96:DE:F3:EC:86:32:E6:E7:BC:9C:99:9D:A7:BB:31:96:C2:84:0C` |

After Play App Signing is enabled, also add the **App signing key certificate** SHA-1 from Play Console → App integrity.

Logcat `GoogleApiManager … Unknown calling package name 'com.google.android.gms'` on emulators is usually a Play Services / emulator image issue, not an app defect and not a Play Store rejection. Use a Google Play system image or a physical device to verify.

## Branding assets (Play-ready)

| Asset | Spec | Location |
|-------|------|----------|
| **Adaptive foreground** | 432×432 RGBA, transparent outside circle | `app/src/main/res/drawable/ic_launcher_foreground.png` |
| **Legacy launcher** | mdpi→xxxhdpi RGB | `app/src/main/res/mipmap-*` |
| **In-app logo** | 512×512 RGBA transparent | `app/src/main/res/drawable/ic_numbers_logo.png` |
| **Play high-res icon** | 512×512 RGB (no alpha) | [`store/hi-res-icon-512.png`](store/hi-res-icon-512.png) |
| **Play feature graphic** | 1024×500 RGB | [`store/feature-graphic.png`](store/feature-graphic.png) |
| **Transparent master** | 512×512 RGBA | [`store/launcher-icon-512-transparent.png`](store/launcher-icon-512-transparent.png) |

## Angular reference issues

Known web bugs and the Android algorithm corrections are listed in [`lkovarihome-errors.md`](lkovarihome-errors.md). Angular sources are not changed in this repo. Share on Android uses the system Share sheet (`Intent.ACTION_SEND`), not clipboard.
