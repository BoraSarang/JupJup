# 🪙 JupJup

A single APK that bundles **MacJupJup** (macOS app catalog collector) and **PlanJupJup** (carrier plan collector), turning a Samsung Galaxy phone into a local data-collection server.

- **Package**: `com.borasarang.jupjup`
- **Services**: MacJupJup (3010) · PlanJupJup (3020)
- **Portal**: `http://<phone-ip>:3010/` · `:3020/`

The start screen **Dashboard** shows the services (status, address, stats, crawl/server actions, active-service highlight). Switch services with the top segmented control and navigate with the bottom tabs (Dashboard/Home/Settings). App info lives in the toolbar `⋮` menu.

> PromptJournal (3030) and CommunityJupJup (3040) were removed after promoting reusable pieces into `:services:common`.

## Architecture

Gradle project root is `android/` in this repository (`android/settings.gradle.kts`).

```
:app                    # Shell — MainActivity, JupJupApplication, shared theme/icon
:services:common        # Shared library (crawl, Throttler, NetMeter, AI, search, …)
:services:mac           # Library — port 3010, isolated DB/DataStore/resources (mac_ prefix)
:services:plan          # Library — port 3020, isolated DB/DataStore/resources (plan_ prefix)
```

All services run in the same process but are fully isolated (ports, databases, DataStore files, notification channels, resources). `{Prefix}JupJupRuntime` (Kotlin objects) replace the per-service `Application` classes due to Android's single-Application-per-process constraint.

## Build

```bash
./build_and_run.sh build     # assembleDebug + install on connected device
./build_and_run.sh test      # unit tests for service modules
./build_and_run.sh lint
./build_and_run.sh clean
```

Output APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## CI

- **ci.yml** — unit tests + lint on push/PR to `main`
- **release.yml** — builds a release APK and creates a GitHub Release when a `v*` tag is pushed

## Documentation

See the [`docs/`](docs/) directory for the development plan, design notes, API spec, permissions, and changelog.
