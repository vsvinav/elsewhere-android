<h1 align="center">Elsewhere for Android</h1>
<p align="center"><strong>Your Google Maps Timeline, printed as a world-tour poster — natively on Android.</strong><br>
Kotlin · Jetpack Compose · the same eight tabs, the same poster, the same privacy rules as <a href="https://github.com/vsvinav/elsewhere">the web app</a>.</p>

> **Status:** under construction. Data model, parser and analytics are ported; the UI is being built tab by tab. Debug APKs are attached to every CI run under *Actions → Android*.

## What you get

| Tab | What's on it |
| --- | --- |
| **Tour** | Your years as one poster: cities ranked by nights slept, places, countries, a dotted route map. |
| **Numbers** | Distance by month and mode, best month, long-run averages. |
| **Facts** | What your record says about you, each with a figure, plus a swipeable deck of shorter, ruder ones. |
| **Stops** | The places you keep going back to, with photos and names. |
| **Dates** | A calendar of where you were, night by night. |
| **Flights** | A globe turned to show every route you've flown; tap an airport to see where it took you. |
| **Map** | Every stop pinned and clustered, filtered by category, searchable. |
| **Replay** | Press play and move through time; planes, cars, trains and walks each get their own colour. |

Pick any range — a year, 90 days, two dates — and every tab updates. Every card shares as a PNG.

## Quick start

### Install

1. Open the latest run under **Actions → Android** and download `elsewhere-debug.apk` (or grab a tagged build from **Releases**).
2. On the phone, allow installs from your browser/files app and open the APK.
3. Export your Timeline (below), open Elsewhere, pick the file.

### Export your Timeline

- **Android** — Settings → Location → Location Services → Timeline → *Export Timeline data*.
- **iPhone** — Google Maps → your photo → *Your timeline* → ⋯ → *Export*.

You get one `Timeline.json`. Google moved Timeline onto the phone in 2024; Takeout no longer includes it.

### Google Maps key (optional)

Without a key the app draws OpenStreetMap tiles and skips place names/photos. With one:

1. [console.cloud.google.com → Maps APIs](https://console.cloud.google.com/google/maps-apis), enable **Maps SDK for Android** and **Places API (New)**.
2. Create a key; restrict it to Android apps with package `com.elsewhere.app` and your signing SHA-1 (`./gradlew signingReport`).
3. Paste it in the app at the front door, or later from the ▾ menu.

The ▾ menu shows a running count of paid calls and a rough dollar figure. Names and photos are fetched once for your 100 busiest stops and cached forever.

## Build it yourself

```bash
brew install openjdk@17                                   # or any JDK 17
brew install --cask android-commandlinetools
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-35" "build-tools;35.0.0" "platform-tools"
git clone https://github.com/vsvinav/elsewhere-android && cd elsewhere-android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # JUnit twins of the web app's vitest suite
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Your data

Your export never leaves the phone. It is parsed on-device and kept in a local database. Network calls: map tiles, a few free OpenStreetMap address lookups for your home picks, and — only with your key — Google Places names and photos for your hundred busiest stops.

## Layout

```
app/src/main/java/com/elsewhere/app/
├── ingest/     Timeline parser, derive (roles, places, days, trips), enrichment, cost meter
├── analytics/  range, tour, stats, facts, flights — pure Kotlin, ported 1:1 from the web app
├── data/       Room + DataStore, RecordRepository
├── map/        MapHost (Google Maps / MapLibre), clustering, pins, replay camera
├── ui/world/   poster theme, fonts, Pass/Bill/Sticker/Stamp, figures
├── ui/shell/   masthead, ticket strip, range bar, calendar sheet
├── ui/tabs/    one package per tab
└── share/      card → PNG → share sheet
```

Design spec and plan: [`elsewhere/docs/superpowers`](https://github.com/vsvinav/elsewhere/tree/main/docs/superpowers).

Fonts: Big Shoulders Display and Archivo, SIL Open Font License.
