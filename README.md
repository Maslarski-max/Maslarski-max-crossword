# Crossword

A native Android crossword game built with Kotlin, Jetpack Compose and Material 3, set up for publishing on Google Play.
The start screen offers two modes: **Classic Crossword** (solo) and **Crossword Arena** (turn-based, against an AI).

- **Classic Crossword**: zoomable/pannable Canvas grid for boards up to 25x25 (pinch, pan, double-tap to zoom in on a spot or back out; taps hit the right cell at any zoom), Across/Down word and active-cell
  highlighting, a live clue bar, a full clue list screen, an on-screen keyboard plus hardware-keyboard support,
  animated letter entry and a confetti/score/stars completion screen.
- **Content**: 7 levels (Easy → Hard, ending with a 20x20 board); the first is free and each next one is unlocked in order for 50 coins, plus a rotating Daily Puzzle with a streak counter.
  Puzzles are plain JSON in `app/src/main/assets/puzzles/`; drop in a file to add a level.
- **Crossword Arena**: an arrow-word board (clues and arrows sit in the grid) with `+2`/`+3` bonus cells, a
  "You N vs N Opponent" score banner and a 5-tile letter rack. Drag or tap tiles onto one word, then Submit (correct
  tiles score their cell value, completing a word adds its length, the rack refills) or Pass. The AI opponent
  (Rookie / Challenger / Champion) then takes its turn. The match ends when the board is full or after four scoreless turns
  in a row. Hints cost 10 coins; a win pays 20 coins (a draw 10) and unlocks stronger opponents. Arena boards are built from the same
  puzzle JSON, so new levels are playable in both modes.
- **Coins**: one Room-backed wallet (starts at 100) shown in the Hub, level list and both game headers. Reveal Letter
  and Reveal Word cost 10 coins (Check Errors 3); solving puzzles and winning Arena matches earn coins. Every balance
  change pops a +/− notification, and anything you can't afford opens a "Not enough coins!" dialog.
- **Persistence**: the board is saved to Room on every keystroke; progress, unlocks, high scores, daily state and
  the wallet live in Room, settings in DataStore. Arena matches (in progress and finished) are kept in their own
  `arena_matches` table, so Arena stats and unlocks are tracked separately from Classic progress.
- **UI**: phones, tablets and foldables (adaptive layouts), light/dark theme, Material You dynamic color on Android 12+,
  edge-to-edge, predictive back.
- **Play readiness**: 100% ad-free (no banners or interstitials); the AdMob SDK and UMP consent (GDPR / US states) stay
  wired up for optional rewarded ads later,
  Firebase Analytics + Crashlytics with Consent Mode, a Play Integrity hook, backup/data-extraction rules,
  HTTPS-only network config, R8, a signed-AAB pipeline (Gradle, fastlane and GitHub Actions), store listing text,
  a privacy policy and Data safety answers.

| | |
| --- | --- |
| Min / target / compile SDK | 26 / 36 / 37.2 |
| Kotlin / AGP / Gradle | 2.4.20 / 9.4.1 / 9.7.1 |
| Dependencies | `gradle/libs.versions.toml` |

## Requirements

- JDK 21 (`JAVA_HOME` pointing at it)
- Android SDK with platform **android-37.2** (compile) and build-tools 36+; Android Studio installs these on sync, or
  `sdkmanager "platforms;android-37.2" "build-tools;36.0.0"`. `local.properties` needs `sdk.dir=...` if `ANDROID_HOME` isn't set.
- Ruby 3.x + Bundler (only for fastlane)

## Build and run

```bash
./gradlew :app:assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug             # install on a connected device/emulator
./gradlew :app:testDebugUnitTest        # engine, parser, scoring and bundled-puzzle tests
./gradlew :app:lintDebug                # Android lint (fails on errors)
```

Or open the folder in Android Studio and run the `app` configuration.

The app shows no ads. The AdMob SDK is initialised with Google's **test** app ID unless you override it. Firebase is
disabled until you add `app/google-services.json` (the app runs fine without it).

## Architecture

```
app/src/main/java/com/maslarski/crossword/
├── domain/            Pure Kotlin – no Android dependencies, fully unit-tested
│   ├── model/         Puzzle, Word, BoardState, progress models
│   ├── engine/        CrosswordEngine (selection, typing, hints, validation), GameRules (score/stars/coins), Streaks
│   ├── parser/        PuzzleParser – JSON → Puzzle with numbering and validation
│   ├── arena/         Arena mode: ArenaLayout (arrow-word board), ArenaRules (rack, scoring, turns), ArenaAi, stats
│   └── repository/    Repository interfaces
├── data/
│   ├── local/         Room entities, DAOs, database, mappers
│   ├── repository/    Asset puzzles, Room progress/wallet, DataStore settings
│   ├── ads/           ConsentManager (UMP), AdsManager (SDK init only; no ad formats yet)
│   ├── telemetry/     Telemetry interface + Firebase implementation
│   └── integrity/     PlayIntegrityChecker
├── di/                Hilt modules
└── ui/                Compose – theme, components (grid, keyboard, clue bar…), screens + ViewModels, navigation
```

Each screen has a Hilt `ViewModel` exposing a `StateFlow<UiState>`; screens collect it with
`collectAsStateWithLifecycle` and send user intents back as function calls. The ViewModels call the stateless
`CrosswordEngine` and persist the resulting `BoardState` through `ProgressRepository` (serialised with a `Mutex`,
so every keystroke is written in order). Navigation uses type-safe `@Serializable` routes: `HubRoute` (mode picker)
and `SettingsRoute` at the top level, with nested `ClassicGraph` (home, levels, game, clues) and `ArenaGraph`
(lobby, match) graphs.

## Adding puzzles

A puzzle is a JSON file in `app/src/main/assets/puzzles/levels/` (or `.../daily/`):

```json
{
  "id": "easy-03",
  "title": "Kitchen",
  "author": "You",
  "difficulty": "EASY",
  "order": 7,
  "grid": ["CAT", "A#O", "BOW"],
  "clues": {
    "across": { "1": "Feline", "3": "Archer's weapon" },
    "down":   { "1": "Taxi",   "2": "Pull a car" }
  }
}
```

- `grid`: one string per row; `A`–`Z` are the solution letters and `#` is a black square.
- Clue numbers follow standard crossword numbering (left-to-right, top-to-bottom); every slot of 2+ letters needs
  exactly one clue. `PuzzleParser` rejects anything inconsistent, and `BundledPuzzlesTest` fails the build if a
  bundled file is invalid.
- `order` sets the position in the level list (levels unlock in that order). Daily puzzles rotate by date.

Easier: write a word list and let the generator lay out the grid and numbering for you:

```text
# tools/puzzle-sources/easy-03.txt
id: easy-03
title: Kitchen
difficulty: EASY
order: 7
type: level
size: 9
seed: 3
---
KETTLE | It whistles when the water boils
SPOON | Soup utensil
```

```bash
python3 tools/puzzle_builder.py tools/puzzle-sources/easy-03.txt
```

Words that can't be interlocked are reported and left out; tweak `size`/`seed` or the word list and re-run.

## Configuration

All values below are read from (in order) a Gradle property (`-Pname=...` or `~/.gradle/gradle.properties`),
then an environment variable, then the defaults in `gradle.properties`. **Never commit production values.**

| Gradle property | Environment variable | Default |
| --- | --- | --- |
| `admobAppId` | `ADMOB_APP_ID` | Google test app ID |
| `playIntegrityCloudProjectNumber` | `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` | `0` (disabled) |
| `versionCode` / `versionName` | `VERSION_CODE` / `VERSION_NAME` | `1` / `1.0.0` |
| `umpTestDeviceId` (debug only) | `UMP_TEST_DEVICE_ID` | empty |

### AdMob and consent (UMP)

1. Create the app in AdMob and set `admobAppId` for release builds. No ad units are needed while the app is ad-free.
2. In AdMob → **Privacy & messaging**, create a **European regulations** (GDPR) message and a **US state regulations**
   message. `ConsentManager` requests consent info on every launch, shows the form when required, and the Settings
   screen shows **Ad privacy choices** when the user must be able to change their choice.
3. The Mobile Ads SDK is only initialised after `canRequestAds()` is true. Firebase Consent Mode ad signals follow the
   same result; analytics defaults to denied ad storage until then (see the manifest meta-data).
4. No ad formats are shown. To add rewarded ads later, create a rewarded ad unit and load it from `AdsManager` once
   `adsReady` is true.
5. To test the EEA consent flow on a debug build, pass your device's hashed ID (UMP prints it in logcat) as
   `-PumpTestDeviceId=...`; `ConsentManager` then forces EEA geography for that device.

### Firebase Analytics and Crashlytics

1. Create a Firebase project and add an Android app with package `com.maslarski.crossword` (and
   `com.maslarski.crossword.debug` if you want debug data).
2. Download `google-services.json` into `app/` (git-ignored). The Google Services and Crashlytics Gradle plugins apply
   automatically when the file exists.
3. Crashlytics collection is disabled in debug builds.

### Play Integrity

`PlayIntegrityChecker` wraps the Standard Integrity API: `prepare()` warms up a token provider and
`requestToken(requestHash)` returns an integrity token (both no-ops returning a failure while unconfigured). Set `playIntegrityCloudProjectNumber` to your Google Cloud project number to enable it,
link the project in Play Console → **App integrity**, and send tokens to **your server** for decoding and
verification — never trust a verdict decoded on the device. The game itself is offline, so nothing calls it by default.

### Security notes

- `network_security_config.xml` blocks cleartext traffic; the app has no exported components besides the launcher.
- Backups include only the Room database and the settings DataStore (`backup_rules.xml`, `data_extraction_rules.xml`);
  ad, consent and Firebase identifiers are excluded.
- Release builds are minified and resource-shrunk with R8; verbose/debug/info `Log` calls are stripped.
- Signing keys, `google-services.json` and Play service-account keys are git-ignored.

## Release: signed App Bundle (.aab)

### 1. Create an upload key (once)

```bash
scripts/create-upload-keystore.sh           # writes upload-keystore.jks + keystore.properties (both git-ignored)
```

Or create `keystore.properties` by hand:

```properties
storeFile=/absolute/or/relative/path/upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Instead of the file you can set `CROSSWORD_KEYSTORE_FILE`, `CROSSWORD_KEYSTORE_PASSWORD`, `CROSSWORD_KEY_ALIAS` and
`CROSSWORD_KEY_PASSWORD`. Enrol in **Play App Signing** (default for new apps): Google holds the app-signing key and
this key is only your upload key. Back it up.

### 2. Build

```bash
./gradlew :app:bundleRelease -PversionCode=2 -PversionName=1.0.1 \
  -PadmobAppId=ca-app-pub-XXXX~YYYY
# → app/build/outputs/bundle/release/app-release.aab
```

Without signing credentials the bundle is built unsigned (useful for CI checks) and Play will reject it.

### 3. fastlane (optional)

```bash
bundle install
bundle exec fastlane test                                 # unit tests + lint
bundle exec fastlane build_aab version_code:2 version_name:1.0.1
bundle exec fastlane internal version_code:2 version_name:1.0.1   # upload to Internal testing as a draft
bundle exec fastlane promote to:production rollout:0.1     # staged rollout of the latest internal build
bundle exec fastlane metadata                              # push fastlane/metadata/android text
```

Uploads need a Play Console service account with release permissions; save its JSON key as
`play-service-account.json` (git-ignored) or point `PLAY_SERVICE_ACCOUNT_JSON` at it. The very first bundle must be
uploaded manually in Play Console.

### 4. GitHub Actions

`.github/workflows/android.yml` runs tests, lint and a debug build on every push/PR. Pushing a tag `v1.2.3` also builds
a signed AAB (version name `1.2.3`, version code = run number) and attaches it as an artifact. Repository secrets:
`UPLOAD_KEYSTORE_BASE64` (`base64 -w0 upload-keystore.jks`), `CROSSWORD_KEYSTORE_PASSWORD`, `CROSSWORD_KEY_ALIAS`,
`CROSSWORD_KEY_PASSWORD`, and optionally `GOOGLE_SERVICES_JSON`, `ADMOB_APP_ID`,
`PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER`.

## Google Play checklist

- **Target API**: `targetSdk = 36` (Android 16), meeting Play's current target API requirement for new apps and updates.
- **Privacy policy**: `docs/privacy-policy.html` — publish it (e.g. GitHub Pages from `/docs`), fill in the developer
  name/contact, and keep `privacy_policy_url` in `strings.xml` in sync.
- **Data safety / App content**: suggested answers in `docs/play-store-data-safety.md`.
- **Advertising ID**: declared in the manifest (`com.google.android.gms.permission.AD_ID`); answer "Yes" in App content.
- **Store listing**: `fastlane/metadata/android/en-US/` (title, short/full description, changelogs). Add screenshots and
  a 512×512 icon / 1024×500 feature graphic in Play Console or under `fastlane/metadata/android/en-US/images/`.
- **16 KB page sizes**: the app ships no native code of its own; keep AGP and SDKs current so bundled SDK libraries stay aligned.
- **Testing**: new personal developer accounts must run a closed test (12+ testers for 14 days) before production access.
