$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkHome = Get-ChildItem (Join-Path $projectRoot ".tools\jdk") -Directory | Select-Object -First 1 -ExpandProperty FullName
$gradleHome = Get-ChildItem (Join-Path $projectRoot ".tools\gradle") -Directory | Where-Object { $_.Name -like "gradle-*" } | Select-Object -First 1 -ExpandProperty FullName
$sdkRoot = Join-Path $projectRoot ".tools\android-sdk"
$androidUserHome = Join-Path $projectRoot ".tools\android-user-home"
$gradleUserHome = Join-Path $projectRoot ".tools\gradle-user-home"

if (-not (Test-Path $jdkHome)) {
    throw "Project-local JDK not found: $jdkHome"
}

if (-not (Test-Path $gradleHome)) {
    throw "Project-local Gradle not found: $gradleHome"
}

if (-not (Test-Path $sdkRoot)) {
    throw "Project-local Android SDK not found: $sdkRoot"
}

$env:JAVA_HOME = $jdkHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = $androidUserHome
$env:GRADLE_USER_HOME = $gradleUserHome

New-Item -ItemType Directory -Force $androidUserHome | Out-Null
New-Item -ItemType Directory -Force $gradleUserHome | Out-Null

$gradle = Join-Path $gradleHome "bin\gradle.bat"
Push-Location $projectRoot
try {
    & $gradle test assembleDebug --no-daemon --stacktrace
} finally {
    Pop-Location
}

