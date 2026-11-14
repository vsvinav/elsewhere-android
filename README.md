# elsewhere · Android

A Capacitor shell around the [elsewhere](../timeline) web app. Same layout, same feel; the web build is copied in, nothing is rewritten.

## Build

Needs Node, Android Studio (SDK 34+) and the web checkout beside this one at `../timeline`.

```sh
npm install
npm run sync     # builds ../timeline/web, stages www/, syncs the Android project
npm run open     # opens android/ in Android Studio
npm run apk      # or: debug APK at android/app/build/outputs/apk/debug/
```

Set `WEB=/path/to/web` if the web checkout lives elsewhere.

## What the app does differently

- Opens on the import screen: no record is bundled. Tap the card to pick `Timeline.json`.
- Share cards go to the system share sheet instead of a download.
- The ticket nav and header respect the display cutout and gesture bar.

Google Maps / Places keys are entered in the app, same as on the web, and stay on the device.
