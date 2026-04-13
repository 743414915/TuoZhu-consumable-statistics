# Desktop HTTP Sync Server

Use this script when the Android app needs to pull draft print jobs directly from the PC over LAN.

## Start

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\start-sync-server.ps1 `
  -ListenHost 0.0.0.0 `
  -Port 8823 `
  -UseBambuGcode `
  -GcodeSearchRoots "$env:USERPROFILE\Desktop","$env:LOCALAPPDATA\Temp\bamboo_model"
```

If you only want a demo payload:

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\start-sync-server.ps1 `
  -ListenHost 0.0.0.0 `
  -Port 8823 `
  -UseSample
```

## Endpoints

- `GET /health`
- `GET /api/sync/pull`
- `POST /api/sync/confirm`

## Android App Setting

In the app, set the desktop sync address to:

```text
http://<your-pc-lan-ip>:8823
```

Example:

```text
http://192.168.1.8:8823
```

## Notes

- Keep the PowerShell window open while the phone is syncing.
- Keep the phone and PC on the same Wi-Fi.
- `POST /api/sync/confirm` writes to `desktop-agent/outbox/confirmation-log.json` and reruns the sync agent so confirmed drafts disappear from the outbox.
