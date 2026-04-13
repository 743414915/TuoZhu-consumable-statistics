param(
    [int]$Port = 8823,
    [string]$ListenHost = "+",
    [string]$InboxPath = "",
    [string]$OutboxPath = "",
    [string]$StatePath = "",
    [string]$ConfirmationPath = "",
    [string]$GeneratedInboxPath = "",
    [string[]]$GcodePaths = @(),
    [string[]]$GcodeSearchRoots = @(),
    [int]$MaxFileAgeDays = 7,
    [switch]$UseSample,
    [switch]$UseBambuGcode
)

$ErrorActionPreference = "Stop"

$agentRoot = $PSScriptRoot
$syncAgentPath = Join-Path $agentRoot "run-sync-agent.ps1"
if (-not $OutboxPath) { $OutboxPath = Join-Path $agentRoot "outbox\desktop-outbox.json" }
if (-not $StatePath) { $StatePath = Join-Path $agentRoot "state\state.json" }
if (-not $ConfirmationPath) { $ConfirmationPath = Join-Path $agentRoot "outbox\confirmation-log.json" }
if (-not $GeneratedInboxPath) { $GeneratedInboxPath = Join-Path $agentRoot "inbox\print-history.generated.json" }

$script:SyncProcess = $null

function Ensure-ParentDirectory {
    param([string]$Path)
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
}

function Read-JsonArray {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) { return @() }
    $parsed = $raw | ConvertFrom-Json
    if ($parsed -is [System.Array]) { return @($parsed) }
    return @($parsed)
}

function Read-State {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return @{
            updatedAt = $null
            warnings = @()
            jobs = @()
            inputMode = $null
        }
    }
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @{
            updatedAt = $null
            warnings = @()
            jobs = @()
            inputMode = $null
        }
    }
    return $raw | ConvertFrom-Json
}

function Write-AtomicJson {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )
    Ensure-ParentDirectory -Path $Path
    $json = ConvertTo-Json -InputObject $Value -Depth 8
    $tempPath = "$Path.tmp"
    Set-Content -LiteralPath $tempPath -Value $json -Encoding UTF8
    Move-Item -LiteralPath $tempPath -Destination $Path -Force
}

function Send-JsonResponse {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)]$Body,
        [int]$StatusCode = 200
    )
    $json = ConvertTo-Json -InputObject $Body -Depth 8
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $Context.Response.StatusCode = $StatusCode
    $Context.Response.ContentType = "application/json; charset=utf-8"
    $Context.Response.ContentEncoding = [System.Text.Encoding]::UTF8
    $Context.Response.ContentLength64 = $bytes.Length
    $Context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $Context.Response.OutputStream.Close()
}

function Read-RequestBody {
    param([Parameter(Mandatory = $true)]$Request)
    $reader = New-Object System.IO.StreamReader($Request.InputStream, $Request.ContentEncoding)
    try {
        return $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
    }
}

function Invoke-SyncAgent {
    $invokeArgs = @{
        OutboxPath = $OutboxPath
        StatePath = $StatePath
        ConfirmationPath = $ConfirmationPath
        GeneratedInboxPath = $GeneratedInboxPath
        MaxFileAgeDays = $MaxFileAgeDays
    }
    if (-not [string]::IsNullOrWhiteSpace($InboxPath)) { $invokeArgs.InboxPath = $InboxPath }
    if ($GcodePaths.Count -gt 0) { $invokeArgs.GcodePaths = $GcodePaths }
    if ($GcodeSearchRoots.Count -gt 0) { $invokeArgs.GcodeSearchRoots = $GcodeSearchRoots }
    if ($UseSample) { $invokeArgs.UseSample = $true }
    if ($UseBambuGcode) { $invokeArgs.UseBambuGcode = $true }
    & $syncAgentPath @invokeArgs | Out-Null
}

function Get-SyncAgentArgumentLine {
    $parts = @(
        '-ExecutionPolicy Bypass',
        "-File `"$syncAgentPath`"",
        "-OutboxPath `"$OutboxPath`"",
        "-StatePath `"$StatePath`"",
        "-ConfirmationPath `"$ConfirmationPath`"",
        "-GeneratedInboxPath `"$GeneratedInboxPath`"",
        "-MaxFileAgeDays $MaxFileAgeDays"
    )
    if (-not [string]::IsNullOrWhiteSpace($InboxPath)) {
        $parts += "-InboxPath `"$InboxPath`""
    }
    foreach ($path in $GcodePaths) {
        $parts += "-GcodePaths `"$path`""
    }
    foreach ($root in $GcodeSearchRoots) {
        $parts += "-GcodeSearchRoots `"$root`""
    }
    if ($UseSample) {
        $parts += "-UseSample"
    }
    if ($UseBambuGcode) {
        $parts += "-UseBambuGcode"
    }
    return [string]::Join(" ", $parts)
}

function Test-SyncProcessRunning {
    if ($null -eq $script:SyncProcess) {
        return $false
    }
    try {
        if ($script:SyncProcess.HasExited) {
            [Console]::Out.WriteLine("Background sync finished with exit code $($script:SyncProcess.ExitCode)")
            $script:SyncProcess.Dispose()
            $script:SyncProcess = $null
            return $false
        }
        return $true
    } catch {
        $script:SyncProcess = $null
        return $false
    }
}

function Start-BackgroundSync {
    param([string]$Reason = "manual")
    if (Test-SyncProcessRunning) {
        return $false
    }

    $argumentLine = Get-SyncAgentArgumentLine
    $process = Start-Process -FilePath powershell.exe -ArgumentList $argumentLine -WorkingDirectory $agentRoot -WindowStyle Hidden -PassThru
    if ($null -ne $process) {
        $script:SyncProcess = $process
        [Console]::Out.WriteLine("Background sync started ($Reason).")
        return $true
    }
    return $false
}

function Build-PullPayload {
    $draftJobs = Read-JsonArray -Path $OutboxPath
    $state = Read-State -Path $StatePath
    $warningList = if ($state.warnings) { @($state.warnings) } else { @() }
    $syncRunning = Test-SyncProcessRunning
    $hasCache = (Test-Path -LiteralPath $OutboxPath) -or (Test-Path -LiteralPath $StatePath)

    $message = if (-not $hasCache -and $syncRunning) {
        "桌面端正在准备同步数据，请稍后再试。"
    } elseif ($syncRunning) {
        "已返回当前缓存，桌面端正在后台刷新。"
    } else {
        "桌面同步完成，待确认任务 $($draftJobs.Count) 条。"
    }

    return [pscustomobject]@{
        status = if (-not $hasCache -and $syncRunning) { "IDLE" } else { "SUCCESS" }
        source = "DESKTOP_AGENT"
        syncedAt = if ($state.updatedAt) { [long]$state.updatedAt } else { [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }
        message = $message
        draftJobs = @($draftJobs)
        warnings = @($warningList)
        inputMode = $state.inputMode
        syncBusy = $syncRunning
    }
}

function Upsert-ConfirmationReceipt {
    param($Receipt)
    $entries = [System.Collections.Generic.List[object]]::new()
    foreach ($item in (Read-JsonArray -Path $ConfirmationPath)) {
        $entries.Add($item)
    }
    $existingIndex = -1
    for ($i = 0; $i -lt $entries.Count; $i++) {
        if ([string]$entries[$i].externalJobId -eq [string]$Receipt.externalJobId) {
            $existingIndex = $i
            break
        }
    }
    if ($existingIndex -ge 0) {
        $entries[$existingIndex] = $Receipt
    } else {
        $entries.Add($Receipt)
    }
    Write-AtomicJson -Value $entries.ToArray() -Path $ConfirmationPath
}

$prefix = "http://${ListenHost}:$Port/"
$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add($prefix)
$listener.Start()

[void](Start-BackgroundSync -Reason "startup")

Write-Output "Desktop sync server started at $prefix"
Write-Output "GET  /health"
Write-Output "GET  /api/sync/pull"
Write-Output "POST /api/sync/confirm"

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        try {
            $path = $context.Request.Url.AbsolutePath.TrimEnd("/")
            if ([string]::IsNullOrWhiteSpace($path)) { $path = "/" }
            Write-Output "$($context.Request.HttpMethod) $path"

            switch ("$($context.Request.HttpMethod) $path") {
                "GET /health" {
                    Send-JsonResponse -Context $context -Body @{
                        status = "ok"
                        source = "DESKTOP_AGENT"
                        serverTime = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                        syncBusy = (Test-SyncProcessRunning)
                    }
                }
                "GET /api/sync/pull" {
                    [void](Start-BackgroundSync -Reason "pull")
                    Send-JsonResponse -Context $context -Body (Build-PullPayload)
                }
                "POST /api/sync/confirm" {
                    $rawBody = Read-RequestBody -Request $context.Request
                    $payload = if ([string]::IsNullOrWhiteSpace($rawBody)) { $null } else { $rawBody | ConvertFrom-Json }
                    if ($null -eq $payload -or [string]::IsNullOrWhiteSpace([string]$payload.externalJobId)) {
                        throw "externalJobId is required."
                    }
                    if (-not $payload.PSObject.Properties.Name.Contains("confirmedAt")) {
                        throw "confirmedAt is required."
                    }

                    $receipt = [pscustomobject]@{
                        externalJobId = [string]$payload.externalJobId
                        confirmedAt = [long]$payload.confirmedAt
                        targetRollId = if ($payload.PSObject.Properties.Name.Contains("targetRollId")) { $payload.targetRollId } else { $null }
                    }

                    Upsert-ConfirmationReceipt -Receipt $receipt
                    [void](Start-BackgroundSync -Reason "confirm")

                    Send-JsonResponse -Context $context -Body @{
                        status = "SUCCESS"
                        source = "DESKTOP_AGENT"
                        syncedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                        message = "Confirmation recorded: $($receipt.externalJobId)"
                    }
                }
                default {
                    Send-JsonResponse -Context $context -StatusCode 404 -Body @{
                        status = "ERROR"
                        message = "Not found: $($context.Request.HttpMethod) $path"
                    }
                }
            }
        } catch {
            Send-JsonResponse -Context $context -StatusCode 500 -Body @{
                status = "ERROR"
                source = "DESKTOP_AGENT"
                message = $_.Exception.Message
            }
        }
    }
} finally {
    if (Test-SyncProcessRunning) {
        try {
            $script:SyncProcess.Kill()
            $script:SyncProcess.Dispose()
        } catch {
        }
        $script:SyncProcess = $null
    }
    $listener.Stop()
    $listener.Close()
}
