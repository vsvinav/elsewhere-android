<h1 align="center">Elsewhere for Android</h1>
<p align="center"><strong>Your Google Maps Timeline, printed as a world-tour poster — natively on Android.</strong><br>
Kotlin · Jetpack Compose · the same eight tabs, the same poster, the same privacy rules as <a href="https://github.com/vsvinav/elsewhere">the web app</a>.</p>

> **Status:** under construction, usable today. All eight tabs are built — Tour, Numbers, Facts, Stops, Dates, Map, Flights and Replay; share cards and on-device recording come next. Install the latest APK from [Releases](https://github.com/vsvinav/elsewhere-android/releases).

<p align="center">
<img src="docs/screenshots/tour.png" width="180" alt="Tour: ADA'S ELSEWHERE headline, nights per city, dotted route map">
<img src="docs/screenshots/numbers.png" width="180" alt="Numbers: distance by month as a stacked bar chart, best month, weekly rhythm">
<img src="docs/screenshots/facts.png" width="180" alt="Facts: a fun-facts card deck with shuffle">
<img src="docs/screenshots/dates.png" width="180" alt="Dates: nights away, trip legs, a route breakdown">
<img src="docs/screenshots/map.png" width="180" alt="Map: category chips, search, a selected stop card and Fit pins">
<img src="docs/screenshots/flights.png" width="180" alt="Flights: an orthographic globe with great-circle routes">
<img src="docs/screenshots/replay.png" width="180" alt="Replay: the playhead moving through time with coloured trails per mode">
</p>

<details><summary>More screens</summary>
<p align="center">
<img src="docs/screenshots/tour-setlist.png" width="180" alt="Tour: setlist of places and residency">
<img src="docs/screenshots/facts-list.png" width="180" alt="Facts: the full list with figures">
<img src="docs/screenshots/dates-nights.png" width="180" alt="Dates: nights by city">
<img src="docs/screenshots/calendar.png" width="180" alt="Calendar sheet for picking a range">
<img src="docs/screenshots/rangebar.png" width="180" alt="Range bar with presets">
<img src="docs/screenshots/flights-ledger.png" width="180" alt="Flights: the route ledger">
<img src="docs/screenshots/key-refused.png" width="180" alt="Onboarding: a refused Google key, with Google's message, a hint and the limited-maps acknowledgement">
<img src="docs/screenshots/home-naming.png" width="180" alt="Onboarding: home candidates being named, with the naming stamp">
</p>
<p align="center"><sub>Rendered by the app's own screenshot tests from a synthetic record, so no real trips are shown.</sub></p>
</details>

## Progress

| Area | State |
| --- | --- |
| Timeline parser, analytics, local database | ✅ |
| Shell: ticket strip, range bar, calendar, onboarding | ✅ |
| Tour · Numbers · Facts · Stops · Dates | ✅ |
| Map (Google map tiles, OpenStreetMap fallback, clusters, search) | ✅ |
| Flights globe | ✅ |
| Replay | ✅ |
| Key verification · place names and photos | ✅ |
| Share cards | ⏳ |
| Recording new location on-device | ⏳ |

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

1. [console.cloud.google.com → Maps APIs](https://console.cloud.google.com/google/maps-apis), enable **Places API (New)** and **Map Tiles API**.
2. Create a key. Under *Application restrictions* choose **None** — a key restricted to websites (HTTP referrers) or to Android apps is refused, because the app calls these APIs directly over HTTPS, not through the Maps SDK. If you want to lock it down, use *API restrictions* and allow just those two APIs.
3. Paste it in the app at the front door, or later from the ▾ menu → Maps key.

The app checks the key the moment you save it, with two free requests, and shows Google's exact answer if either is refused, along with what to change. A key that fails (or no key) means *limited maps*: OpenStreetMap tiles, no place names or photos. You are asked to acknowledge that before going on, and can re-check the key from the menu any time.

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
├── map/        MapLibre host (Google tiles or free style), clustering, pins, replay camera
├── ui/world/   poster theme, fonts, Pass/Bill/Sticker/Stamp, figures
├── ui/shell/   masthead, ticket strip, range bar, calendar sheet
├── ui/tabs/    one package per tab
└── share/      card → PNG → share sheet
```

Fonts: Big Shoulders Display and Archivo, SIL Open Font License.
