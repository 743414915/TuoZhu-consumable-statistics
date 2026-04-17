# Desktop GUI Plan

## Goal

Replace the manual PowerShell entrypoint with a visual Windows desktop application.

## Current Delivery

- Desktop GUI module: `desktop-app`
- Technology: Java 17 + Swing
- Packaging: project-local Gradle + project-local JDK `jpackage`
- Sync engine: current desktop GUI still drives the existing stable PowerShell sync scripts internally

## Why This Version

- no system environment changes
- no extra runtime install
- can be packaged into a clickable Windows app image
- reuses the already validated sync protocol and file layout

## Main UI Areas

- top status strip
  - service state
  - phone endpoint
  - pending drafts
  - warning count
  - last sync time
- left control panel
  - desktop-agent root
  - mode switch: sample / real gcode
  - gcode search roots
  - HTTP port
  - max file age
  - scan once / start service / stop service
- right preview panel
  - draft preview
  - warning preview
- bottom log panel
  - process output and operator log

## Next Step After This Version

When the GUI flow is accepted, the next refactor should move the sync engine itself from PowerShell into Java so the desktop app no longer shells out at all.
