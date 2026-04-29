# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

This is a consumable (filament) management app for Bambu Lab A1 mini printers (no AMS). It has three layers:

- **`app/`** — Android client (Kotlin, Jetpack Compose, Room DB). Manages filament rolls, confirms print jobs, and pulls drafts from the desktop.
- **`desktop-app/`** — Legacy Windows desktop GUI (Java 17, Swing, built-in `com.sun.net.httpserver`). Watches Bambu Studio slice cache, invokes the sync agent, serves `/health` / `/api/sync/pull` / `/api/sync/confirm` to the phone. Being replaced by `desktop-vue/`.
- **`desktop-vue/`** — New Electron + Vue 3 + TypeScript desktop app (replaces `desktop-app/`). Electron main process (`electron/main.ts`) handles HTTP server, G-code file watching (chokidar), PowerShell sync agent spawning, Tailscale/LAN endpoint discovery, and IPC to the renderer. Vue renderer (`src/`) displays status, drafts, logs, and configuration. Build: `npm run electron:build` (output via electron-builder).
- **`desktop-agent/`** — PowerShell G-code parser. Extracts model/material/grams from `.gcode` files and writes draft tasks to `outbox/` and state to `state/`.

## Build commands

All Android/Java builds use project-local `.tools/` (JDK, Gradle, Android SDK). The Gradle wrapper is missing its JAR, so do NOT use `gradlew`. Use the PS1 scripts instead:

```powershell
# Android debug build + tests
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1

# Legacy Java desktop app packaging (produces dist/desktop/TuoZhuDesktopSync/)
powershell -ExecutionPolicy Bypass -File .\scripts\build-desktop-app.ps1
```

For the new Electron desktop app (`desktop-vue/`):

```bash
cd desktop-vue
npm ci                          # install dependencies
npm run electron:dev            # dev with hot-reload
npm run electron:build          # production build + package via electron-builder
```

To run a single Android unit test class or method:

```powershell
$env:JAVA_HOME = (Get-ChildItem .\.tools\jdk -Directory | Where-Object { Test-Path "$_\bin\java.exe" } | Sort-Object Name | Select-Object -First 1).FullName
$env:ANDROID_HOME = (Resolve-Path .\.tools\android-sdk).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Resolve-Path .\.tools\gradle-user-home).Path
$gradle = (Get-ChildItem .\.tools\gradle\gradle-*\bin\gradle.bat | Select-Object -First 1).FullName
& $gradle :app:testDebugUnitTest --tests "com.tuozhu.consumablestatistics.data.FilamentRepositoryTest" --no-daemon
```

## Architecture

### Android app (`app/`)

- **Entry point**: `MainActivity.kt` → `ConsumableApplication.kt` (creates DB, repository, sync coordinator, settings store).
- **Data flow**: Room DB (`ConsumableDatabase`) → DAO (`FilamentDao`) → Repository (`FilamentRepository`) → ViewModel (`ConsumableViewModel`) → Composable UI. The UI is split: `ConsumableScreen.kt` is the scaffold/shell, `RefinedSections.kt` holds the main card-based sections (active roll, inventory, archived, print timeline, event history), `RefinedDialogs.kt` holds dialogs (weight change, import preview, archive confirmation).
- **Sync flow**: `ConsumableViewModel.pullSync()` → `FilamentRepository.pullSync()` → `DesktopAgentSyncCoordinator.pull()` (HTTP GET to desktop's `/api/sync/pull`). Confirmation pushes back via POST `/api/sync/confirm`.
- **Entities**: `FilamentRollEntity`, `FilamentEventEntity`, `PrintJobEntity`, `SyncStateEntity` — all in Room with KSP-generated code.
- **Idempotency**: Draft jobs are upserted by `externalJobId`. Confirmed tasks are deduplicated via `countPrintUsageEventsByExternalJobId`.
- **Archive**: Rolls are soft-deleted by prepending `[[ARCHIVED]]` to their `notes` field (never hard-deleted).
- **ViewModel uses mutex** (`pullSyncMutex`) to prevent concurrent sync pulls. `confirmingJobIdsState` Set prevents double-confirmation taps.
- **Data export/import**: `DataExportImport.kt` handles JSON backup/restore of rolls (with events) and print jobs. Export format is versioned (`version: 1`, `appName: "TuoZhuConsumableStatistics"`). Import is idempotent for print jobs (deduplicated by `externalJobId`).

### Desktop app (`desktop-app/`)

- **Entry point**: `DesktopSyncApp.java` — Swing JFrame with dark theme. Resolves Tailscale vs LAN addresses, generates QR codes for phone pairing.
- **Service**: `EmbeddedSyncService.java` — HTTP server on port 8823. Pull returns `desktop-outbox.json` content; confirm writes `confirmation-log.json`.
- **Watchdog**: `GcodeWatchService.java` — polls Bambu Studio cache directories, triggers sync agent on new `.gcode` files after a stability delay.
- **Sync agent invocation**: Builds a PowerShell process via `ProcessBuilder` → `run-sync-agent.ps1`. Output is redirected for logging.

### Desktop Vue app (`desktop-vue/`)

This is the new Electron + Vue 3 + TypeScript replacement for the Java `desktop-app/`. Single-window dark-themed desktop app.

- **Electron main process** (`electron/main.ts`): All system-facing logic lives here — HTTP server on port 8823 (same endpoints as Java version: `/health`, `/api/sync/pull`, `/api/sync/confirm`), G-code file watching via chokidar (stability delay: 1200ms), PowerShell sync agent spawning, Tailscale/LAN endpoint discovery with scoring, config persistence to `state/gui-config.json`.
- **Vue renderer** (`src/`): Components are `StatusBadge`, `EndpointCard`, `DraftList`, `LogViewer`, `ConfigDialog`. State flows via IPC: renderer calls `ipcMain.handle` handlers, main process pushes `state-update` events every 2.5s via `webContents.send`.
- **IPC channels**: `get-state`, `start-service`, `stop-service`, `manual-scan`, `update-config`, `open-directory`, `copy-to-clipboard`.
- **Sync queue**: Only one PowerShell process runs at a time. If a new request arrives while busy, it's queued (`queuedFullSync` or `queuedExplicitPaths`), drained on process close.
- **Endpoint scoring**: Prioritizes Tailscale (100.x.x.x) > LAN (RFC 1918) with bonuses for Wi-Fi/Ethernet, penalties for virtual/hypervisor adapters.
- **Build**: `npm run electron:dev` (Vite dev server + Electron) or `npm run electron:build` (production, electron-builder Windows portable).

### Desktop agent (`desktop-agent/`)

- `run-sync-agent.ps1` is the engine: parses `.gcode` → `inbox/print-history.generated.json` → merges with existing state → writes `outbox/desktop-outbox.json` and `state/state.json`.
- Supports two modes: `-UseSample` (demo data) and `-UseBambuGcode` (real parsing).
- Generates draft tasks with `externalJobId` derived from the G-code filename, plus `modelName`, `estimatedUsageGrams`, `targetMaterial`.

### Material domain (`domain/`)

- `SupportedMaterials`: Only PLA Basic, PETG Basic, PLA Silk. Normalization is fuzzy (keywords in Chinese and English).
- `WeightMath`: Clamps remaining grams to [0, initial], computes progress as a 0..1 ratio, low stock threshold check.

### Testing

- **Android unit tests** (JVM, no device needed): `app/src/test/` — Room DAO, repository, sync coordinator, validator tests. Uses `kotlinx-coroutines-test`.
- **Desktop tests**: `desktop-app/src/test/` — JUnit tests for `EmbeddedSyncService` and `GcodeWatchService`.
- Tests run as part of `build-debug.ps1` (`gradle test assembleDebug`).
- To add a new test, place it in `app/src/test/java/com/tuozhu/consumablestatistics/<package>/` and follow the existing pattern (JUnit 4, `kotlinx-coroutines-test`).
