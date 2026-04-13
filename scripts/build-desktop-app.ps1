$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkHome = Get-ChildItem (Join-Path $projectRoot ".tools\jdk") -Directory | Select-Object -First 1 -ExpandProperty FullName
$gradleHome = Get-ChildItem (Join-Path $projectRoot ".tools\gradle") -Directory | Where-Object { $_.Name -like "gradle-*" } | Select-Object -First 1 -ExpandProperty FullName
$gradleUserHome = Join-Path $projectRoot ".tools\gradle-user-home"
$distRoot = Join-Path $projectRoot "dist\desktop"
$imageName = "TuoZhuDesktopSync"

if (-not (Test-Path $jdkHome)) {
    throw "Project-local JDK not found: $jdkHome"
}

if (-not (Test-Path $gradleHome)) {
    throw "Project-local Gradle not found: $gradleHome"
}

$env:JAVA_HOME = $jdkHome
$env:GRADLE_USER_HOME = $gradleUserHome

New-Item -ItemType Directory -Force $gradleUserHome | Out-Null

$gradle = Join-Path $gradleHome "bin\gradle.bat"
$jpackage = Join-Path $jdkHome "bin\jpackage.exe"
$jarDir = Join-Path $projectRoot "desktop-app\build\libs"
$jarPath = Join-Path $jarDir "desktop-app.jar"
$imageRoot = Join-Path $distRoot $imageName

Push-Location $projectRoot
try {
    & $gradle :desktop-app:clean :desktop-app:jar --no-daemon --stacktrace

    if (-not (Test-Path $jarPath)) {
        throw "Desktop jar not found: $jarPath"
    }

    New-Item -ItemType Directory -Force $distRoot | Out-Null
    if (Test-Path $imageRoot) {
        Remove-Item -LiteralPath $imageRoot -Recurse -Force
    }

    & $jpackage `
        --type app-image `
        --name $imageName `
        --dest $distRoot `
        --input $jarDir `
        --main-jar "desktop-app.jar" `
        --main-class "com.tuozhu.desktop.DesktopSyncApp" `
        --java-options "-Dfile.encoding=UTF-8"

    Copy-Item -LiteralPath (Join-Path $projectRoot "desktop-agent") -Destination (Join-Path $imageRoot "desktop-agent") -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $projectRoot "desktop-agent\HTTP_SERVER_USAGE.md") -Destination (Join-Path $imageRoot "HTTP_SERVER_USAGE.md") -Force
    Copy-Item -LiteralPath (Join-Path $projectRoot "docs\DESKTOP_GUI_USAGE.md") -Destination (Join-Path $imageRoot "DESKTOP_GUI_USAGE.md") -Force
} finally {
    Pop-Location
}
