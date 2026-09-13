# 🪙 JupJup

A single APK that bundles **MacJupJup** (macOS app catalog collector) and **PlanJupJup** (carrier plan / wireless pricing collector), turning a Samsung Galaxy phone into a local data-collection server.

- **Package**: `com.borasarang.jupjup`
- **Services**: MacJupJup (port 3000) + PlanJupJup (port 3001)
- **Portal**: `http://<phone-ip>:3000/` / `http://<phone-ip>:3001/`

The start screen **Insight** shows both services side by side (status, address, stats, crawl/server actions). Use the top drawer (Series/Mac/Plan) and bottom tabs (Insight/Sources/Notifications/Settings) to navigate.

## Architecture

```
:app               # Shell — MainActivity, JupJupApplication, shared theme/icon
:services:mac      # Library — port 3000, isolated DB/DataStore/resources (mac_ prefix)
:services:plan     # Library — port 3001, isolated DB/DataStore/resources (plan_ prefix)
```

Both services run in the same process but are fully isolated (ports, databases, DataStore files, notification channels, resources). `MacJupJupRuntime` / `PlanJupJupRuntime` (Kotlin objects) replace the per-service `Application` classes due to Android's single-Application-per-process constraint.

## Build

```bash
./build_and_run.sh build     # assembleDebug + install on connected device
./build_and_run.sh test      # unit tests for both service modules
./build_and_run.sh lint
./build_and_run.sh clean
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## CI

- **ci.yml** — unit tests + lint on push/PR to `main`
- **release.yml** — builds a release APK and creates a GitHub Release when a `v*` tag is pushed

## Documentation

See the [`docs/`](docs/) directory for the development plan, design notes, API spec, permissions, and changelog.