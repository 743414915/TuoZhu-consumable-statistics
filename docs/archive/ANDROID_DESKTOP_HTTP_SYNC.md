# Android Desktop HTTP Sync

## What Was Added

- Desktop LAN sync server: `desktop-agent/start-sync-server.ps1`
- Android desktop endpoint configuration on the main screen
- Real draft pull from the desktop server
- Confirmation receipt pushback after local confirmation
- Safer confirmation rules:
  - material must match the active roll
  - remaining grams must be enough before deduction

## Transport Contract

### Pull

`GET /api/sync/pull`

Response shape:

```json
{
  "status": "SUCCESS",
  "source": "DESKTOP_AGENT",
  "syncedAt": 1775806800000,
  "message": "Desktop sync finished. Pending drafts: 2",
  "draftJobs": [],
  "warnings": [],
  "inputMode": "bambu-gcode"
}
```

### Confirm

`POST /api/sync/confirm`

Request shape:

```json
{
  "externalJobId": "bambu-gcode-23028-0",
  "confirmedAt": 1775806800000,
  "targetRollId": 1
}
```

## Android UX

- The sync card on the home screen now contains:
  - current sync state
  - desktop address summary
  - a collapsible address editor
  - one-tap manual pull
- Pending print jobs now show the target material and block confirmation when the active roll material does not match.

## Regression

Five successful regression rounds were completed on 2026-04-13:

1. `scripts/build-debug.ps1`
2. `desktop-agent/run-sync-agent.ps1 -UseSample`
3. `desktop-agent/run-sync-agent.ps1 -UseBambuGcode -GcodePaths 'C:\Users\Administrator\Desktop\.23028.0.gcode'`
4. `start-sync-server.ps1` smoke test with:
   - `GET /api/sync/pull`
   - `POST /api/sync/confirm`
5. final `scripts/build-debug.ps1`

## Artifact

- Debug APK: `dist/tuozhu-consumable-manager-debug.apk`
