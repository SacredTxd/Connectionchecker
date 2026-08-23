# Connection Checker

An Android app that monitors network connectivity: what the platform reports, whether a
real endpoint is actually reachable, and how that has held up over time.

## What it does

- **Live status** — transport in use (WiFi / cellular / ethernet / VPN / bluetooth),
  whether the system has validated the network, and whether it is metered.
- **Reachability probes** — an HTTP probe against `generate_204` measuring round-trip
  latency, because "connected" and "the internet works" are not the same claim.
- **History** — a bounded, newest-first log of samples persisted to disk, surviving
  process death.
- **Summary** — uptime fraction, average and worst latency, and an outage count that
  treats a run of consecutive offline samples as one outage.
- **Background monitoring** — an opt-in foreground service that samples on an interval
  and keeps the latest result on an ongoing notification.

## Layout

```
app/src/main/java/com/sacredtxd/connectionchecker/
├── data/                     Models, history store, repository
│   ├── Models.kt             NetworkStatus, ReachabilityResult, ConnectionEvent, ConnectionSummary
│   ├── ConnectionEventStore.kt   Bounded JSON-backed history log
│   └── ConnectionRepository.kt   Single source of truth for the UI and service
├── monitor/
│   ├── ConnectivitySource.kt     Interfaces the repository depends on
│   ├── NetworkStatusMonitor.kt   ConnectivityManager callbacks as a cold Flow
│   └── ReachabilityChecker.kt    HTTP probe with latency measurement
├── service/
│   └── ConnectionMonitorService.kt   Foreground service, periodic sampling
├── ui/                       Compose dashboard, theme, ViewModel
├── AppContainer.kt           Hand-rolled DI
├── ConnectionCheckerApp.kt
└── MainActivity.kt
```

## Getting the app

Every push builds a debug APK in CI and republishes it as the rolling `latest`
prerelease, so this link always serves the newest build:

**https://github.com/SacredTxd/Connectionchecker/releases/latest**

The APK is debug-signed, so Android asks you to allow installation from an unknown
source. Android 7.0 (API 24) or newer.

## Building

Requires the Android SDK (compileSdk 35) and JDK 17+.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Testing

`ConnectionRepository` depends on the `ConnectivitySource` and `ReachabilityProbe`
interfaces rather than their Android-coupled implementations, so the repository, the
history store, the summary rollup and the probe's result mapping are all covered by
plain JVM unit tests with no emulator or Robolectric runtime needed.

```
./gradlew testDebugUnitTest
```

`minSdk` is 24; `POST_NOTIFICATIONS` is requested at runtime on Android 13+, and the
monitoring service still runs if it is denied — only the notification is suppressed.

## Updating the app

The app updates itself. **Check for updates** on the dashboard reads a `version.json`
published alongside the newest release, compares its version code with the installed
one, downloads the APK into app-private cache and hands it to the system package
installer — which shows its own confirmation before anything is installed.

Android 8.0 and newer gate installing by source app. The first time, the app sends
you to the settings screen that grants it; approve there and tap Install again.

### Release signing

In-app updates only work when every build is signed with the same key: Android
refuses to install an update whose signature differs from the installed app.

CI signs the release when these repository secrets are present. Without them the
build still succeeds, falling back to debug signing.

| Secret | What it holds |
|---|---|
| `KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | the key alias |
| `KEY_PASSWORD` | the key password |

The version code is the CI run number, so every published build outranks the last.
