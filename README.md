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
│   ├── NetworkStatusMonitor.kt   ConnectivityManager callbacks as a cold Flow
│   └── ReachabilityChecker.kt    HTTP probe with latency measurement
├── service/
│   └── ConnectionMonitorService.kt   Foreground service, periodic sampling
├── ui/                       Compose dashboard, theme, ViewModel
├── AppContainer.kt           Hand-rolled DI
├── ConnectionCheckerApp.kt
└── MainActivity.kt
```

## Building

Requires the Android SDK (compileSdk 35) and JDK 17+.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

`minSdk` is 24; `POST_NOTIFICATIONS` is requested at runtime on Android 13+, and the
monitoring service still runs if it is denied — only the notification is suppressed.
