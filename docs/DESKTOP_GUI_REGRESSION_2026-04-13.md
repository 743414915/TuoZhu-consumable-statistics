# Desktop GUI Regression 2026-04-13

## Scope

Current delivery includes:

- packaged Windows desktop GUI
- bundled `desktop-agent`
- real Bambu `.gcode` scan path
- HTTP sync service for Android pull and confirm

## Regression Rounds

### 1. Desktop packaging

Command:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-desktop-app.ps1
```

Result:

- passed
- app image generated under `dist/desktop/TuoZhuDesktopSync`
- packaged output includes `TuoZhuDesktopSync.exe`
- packaged output includes `desktop-agent`
- packaged output includes `DESKTOP_GUI_USAGE.md`

### 2. GUI smoke launch

Check:

- launched packaged `TuoZhuDesktopSync.exe`
- process stayed alive during smoke window
- process could be closed cleanly after validation

Result:

- passed

### 3. Sample sync scan

Command class:

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\run-sync-agent.ps1 -UseSample
```

Validation:

- outbox generated successfully
- pending draft count = 2
- first sample draft material remained valid

Result:

- passed

### 4. Real Bambu gcode scan

Input:

- `C:\Users\Administrator\Desktop\.23028.0.gcode`

Validation:

- parsed `externalJobId = bambu-gcode-23028-0`
- parsed `targetMaterial = PETG Basic`
- parsed `estimatedUsageGrams = 43`
- warning produced for material conflict
- generated note no longer contains mojibake

Result:

- passed

### 5. HTTP health / pull / confirm

Validation:

- `GET /health` returned `ok`
- `GET /api/sync/pull` returned sample drafts
- `POST /api/sync/confirm` recorded confirmation
- local state showed confirmed job count = 1

Result:

- passed

## Current Conclusion

The desktop visual entrypoint is buildable, launchable, and the desktop-to-Android sync protocol is working on current Windows runtime validation.
