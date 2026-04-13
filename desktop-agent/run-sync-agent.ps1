param(
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
$inboxPathProvided = -not [string]::IsNullOrWhiteSpace($InboxPath)
$generatedInboxPathProvided = -not [string]::IsNullOrWhiteSpace($GeneratedInboxPath)

if (-not $InboxPath) {
    $InboxPath = if ($UseSample) {
        Join-Path $agentRoot "inbox\print-history.sample.json"
    } else {
        Join-Path $agentRoot "inbox\print-history.json"
    }
}
if (-not $OutboxPath) {
    $OutboxPath = Join-Path $agentRoot "outbox\desktop-outbox.json"
}
if (-not $StatePath) {
    $StatePath = Join-Path $agentRoot "state\state.json"
}
if (-not $ConfirmationPath) {
    $ConfirmationPath = Join-Path $agentRoot "outbox\confirmation-log.json"
}
if (-not $GeneratedInboxPath) {
    $GeneratedInboxPath = Join-Path $agentRoot "inbox\print-history.generated.json"
}

$SupportedMaterials = @("PLA Basic", "PETG Basic", "PLA Silk")
$SupportedSources = @("DESKTOP_AGENT", "CLOUD")
$EarliestAllowedTimestamp = [DateTimeOffset]::Parse("2020-01-01T00:00:00Z").ToUnixTimeMilliseconds()
$MaxFutureDriftMs = 24 * 60 * 60 * 1000

function Ensure-ParentDirectory {
    param([string]$Path)
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path $parent)) {
        New-Item -ItemType Directory -Force $parent | Out-Null
    }
}

function Read-JsonArray {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return @()
    }
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @()
    }
    $parsed = $raw | ConvertFrom-Json
    if ($parsed -is [System.Array]) {
        return @($parsed)
    }
    return @($parsed)
}

function Read-State {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return @{ updatedAt = $null; warnings = @(); jobs = @(); inputMode = $null }
    }
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @{ updatedAt = $null; warnings = @(); jobs = @(); inputMode = $null }
    }
    $parsed = $raw | ConvertFrom-Json
    $jobs = @()
    if ($parsed.jobs) {
        $jobs = @($parsed.jobs)
    }
    $warnings = @()
    if ($parsed.warnings) {
        $warnings = @($parsed.warnings)
    }
    return @{
        updatedAt = if ($parsed.PSObject.Properties.Name.Contains("updatedAt")) { $parsed.updatedAt } else { $null }
        warnings = $warnings
        jobs = $jobs
        inputMode = if ($parsed.PSObject.Properties.Name.Contains("inputMode")) { $parsed.inputMode } else { $null }
    }
}

function Write-PrettyJson {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$ForceArray
    )
    Ensure-ParentDirectory -Path $Path
    if ($ForceArray) {
        $items = @($Value)
        if ($items.Count -eq 0) {
            $json = "[]"
        } elseif ($items.Count -eq 1) {
            $json = "[`r`n$(ConvertTo-Json -InputObject $items[0] -Depth 8)`r`n]"
        } else {
            $json = ConvertTo-Json -InputObject $items -Depth 8
        }
    } else {
        $json = ConvertTo-Json -InputObject $Value -Depth 8
    }
    $tempPath = "$Path.tmp"
    Set-Content -LiteralPath $tempPath -Value $json -Encoding UTF8
    Move-Item -LiteralPath $tempPath -Destination $Path -Force
}

function Get-FirstRegexGroup {
    param(
        [string]$Text,
        [string]$Pattern
    )
    $match = [regex]::Match(
        $Text,
        $Pattern,
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Multiline
    )
    if ($match.Success) {
        return $match.Groups[1].Value.Trim()
    }
    return $null
}

function Get-QuotedValues {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return @()
    }
    return @(
        [regex]::Matches($Text, '"([^"]+)"') |
            ForEach-Object { $_.Groups[1].Value.Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Map-ToSupportedMaterial {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    if ($Text -match "PLA\s*Silk|PLA丝绸|丝绸") {
        return "PLA Silk"
    }
    if ($Text -match "PETG\s*Basic|\bPETG\b") {
        return "PETG Basic"
    }
    if ($Text -match "PLA\s*Basic|\bPLA\b") {
        return "PLA Basic"
    }
    return $null
}

function Resolve-BambuMaterial {
    param(
        [string]$FilamentSettingsIdLine,
        [string]$FilamentTypeLine,
        [string]$DefaultProfileLine
    )

    $fromSettings = Map-ToSupportedMaterial -Text $FilamentSettingsIdLine
    $fromFilamentType = Map-ToSupportedMaterial -Text $FilamentTypeLine
    $fromDefaultProfile = Map-ToSupportedMaterial -Text $DefaultProfileLine

    $selected = $null
    $selectedSource = $null
    foreach ($candidate in @(
        @{ Source = "filament_settings_id"; Value = $fromSettings },
        @{ Source = "filament_type"; Value = $fromFilamentType },
        @{ Source = "default_filament_profile"; Value = $fromDefaultProfile }
    )) {
        if ($candidate.Value) {
            $selected = $candidate.Value
            $selectedSource = $candidate.Source
            break
        }
    }

    return @{
        material = $selected
        source = $selectedSource
        candidates = @(
            $fromSettings,
            $fromFilamentType,
            $fromDefaultProfile
        ) | Where-Object { $_ } | Select-Object -Unique
    }
}

function Resolve-BambuModelName {
    param(
        [string]$FilamentSettingsIdLine,
        [string]$Path
    )

    foreach ($quotedValue in Get-QuotedValues -Text $FilamentSettingsIdLine) {
        if ($quotedValue -match "\((?<name>[^()]+?)\.3mf\)") {
            return $Matches.name.Trim()
        }
        if ($quotedValue -match "\((?<name>[^()]+)\)") {
            return $Matches.name.Trim()
        }
    }

    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($Path).Trim(".")
    if (-not [string]::IsNullOrWhiteSpace($baseName)) {
        return $baseName
    }

    return [System.IO.Path]::GetFileName($Path)
}

function Get-BambuExternalJobId {
    param(
        [string]$Path,
        [string]$FingerprintSource
    )

    $leaf = [System.IO.Path]::GetFileName($Path)
    if ($leaf -match '^\.(?<job>\d+)\.(?<plate>\d+)\.gcode$') {
        return "bambu-gcode-$($Matches.job)-$($Matches.plate)"
    }
    if ($Path -match '#(?<job>\d+)#(?<slot>\d+)(?:\\|$)') {
        return "bambu-slice-$($Matches.job)-$($Matches.slot)"
    }

    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    try {
        $hashBytes = $sha1.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($FingerprintSource))
    } finally {
        $sha1.Dispose()
    }
    $hash = ([System.BitConverter]::ToString($hashBytes)).Replace("-", "").ToLowerInvariant()
    return "bambu-gcode-$($hash.Substring(0, 16))"
}

function Get-DefaultGcodeTargets {
    $targets = @()
    if ($env:USERPROFILE) {
        $targets += [pscustomobject]@{
            Path = Join-Path $env:USERPROFILE "Desktop"
            Recurse = $false
        }
    }
    foreach ($candidate in @(
        (Join-Path $env:LOCALAPPDATA "Temp\bamboo_model"),
        (Join-Path $env:TEMP "bamboo_model")
    )) {
        if ($candidate -and -not ($targets.Path -contains $candidate)) {
            $targets += [pscustomobject]@{
                Path = $candidate
                Recurse = $true
            }
        }
    }
    return $targets
}

function Get-GcodeFileItemsFromSearchRoot {
    param(
        [string]$Root,
        [bool]$Recurse
    )
    if ([string]::IsNullOrWhiteSpace($Root) -or -not (Test-Path -LiteralPath $Root)) {
        return @()
    }
    if ($Recurse) {
        return @(Get-ChildItem -LiteralPath $Root -Filter *.gcode -File -Recurse -Force -ErrorAction SilentlyContinue)
    }
    return @(Get-ChildItem -LiteralPath $Root -Filter *.gcode -File -Force -ErrorAction SilentlyContinue)
}

function Get-GcodeFileItemsFromPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) {
        return @()
    }
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.PSIsContainer) {
        return @(Get-ChildItem -LiteralPath $item.FullName -Filter *.gcode -File -Recurse -Force -ErrorAction SilentlyContinue)
    }
    if ($item.Extension -ieq ".gcode") {
        return @($item)
    }
    return @()
}

function Resolve-GcodeCandidates {
    param(
        [string[]]$ExplicitPaths,
        [string[]]$SearchRoots,
        [int]$FileAgeDays
    )

    $itemsByPath = @{}
    foreach ($path in $ExplicitPaths) {
        foreach ($item in Get-GcodeFileItemsFromPath -Path $path) {
            $itemsByPath[$item.FullName] = $item
        }
    }

    if ($SearchRoots.Count -gt 0) {
        foreach ($root in $SearchRoots) {
            foreach ($item in Get-GcodeFileItemsFromSearchRoot -Root $root -Recurse $true) {
                $itemsByPath[$item.FullName] = $item
            }
        }
    } else {
        foreach ($target in Get-DefaultGcodeTargets) {
            foreach ($item in Get-GcodeFileItemsFromSearchRoot -Root $target.Path -Recurse $target.Recurse) {
                $itemsByPath[$item.FullName] = $item
            }
        }
    }

    $items = @($itemsByPath.Values)
    if ($FileAgeDays -gt 0) {
        $cutoff = [DateTime]::UtcNow.AddDays(-$FileAgeDays)
        $items = @($items | Where-Object { $_.LastWriteTimeUtc -ge $cutoff })
    }

    return @($items | Sort-Object LastWriteTimeUtc, FullName)
}

function Convert-BambuGcodeToJob {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )

    $item = Get-Item -LiteralPath $Path -Force
    $lines = Get-Content -LiteralPath $Path -Encoding UTF8 -TotalCount 2000
    $text = ($lines -join "`n")
    if ($text -notmatch '(?m)^;\s*BambuStudio\b') {
        throw "File '$Path' is not recognized as a Bambu Studio gcode."
    }

    $weightText = Get-FirstRegexGroup -Text $text -Pattern '^\;\s*total filament weight \[g\]\s*:\s*([0-9]+(?:\.[0-9]+)?)\s*$'
    if (-not $weightText) {
        throw "Unable to find total filament weight in '$Path'."
    }
    $rawWeight = [double]$weightText
    if ($rawWeight -le 0) {
        throw "Parsed filament weight is not positive in '$Path'."
    }

    $printTime = Get-FirstRegexGroup -Text $text -Pattern '^\;\s*model printing time:\s*(.+?)\s*;\s*total estimated time:'
    $printerModel = Get-FirstRegexGroup -Text $text -Pattern '^\;\s*printer_model\s*=\s*(.+?)\s*$'
    $filamentSettingsIdLine = Get-FirstRegexGroup -Text $text -Pattern '^\;\s*filament_settings_id\s*=\s*(.+?)\s*$'
    $filamentTypeLine = Get-FirstRegexGroup -Text $text -Pattern '^\;\s*filament_type\s*=\s*(.+?)\s*$'
    $defaultProfileLine = Get-FirstRegexGroup -Text $text -Pattern '^\;\s*default_filament_profile\s*=\s*(.+?)\s*$'

    $materialResolution = Resolve-BambuMaterial `
        -FilamentSettingsIdLine $filamentSettingsIdLine `
        -FilamentTypeLine $filamentTypeLine `
        -DefaultProfileLine $defaultProfileLine

    $warnings = New-Object System.Collections.Generic.List[string]
    if ($materialResolution.candidates.Count -gt 1) {
        $warnings.Add(
            "材料识别存在冲突，已按 $($materialResolution.source) 采用 '$($materialResolution.material)'。"
        )
    }
    if (-not $materialResolution.material) {
        $warnings.Add("未能从切片文件识别材料，将按未指定材料导入草稿。")
    }

    $modelName = Resolve-BambuModelName -FilamentSettingsIdLine $filamentSettingsIdLine -Path $Path
    $fingerprintSource = @(
        $modelName
        $weightText
        $printTime
        $filamentSettingsIdLine
        $filamentTypeLine
        $defaultProfileLine
    ) -join "|"

    $estimatedUsageGrams = [int][math]::Ceiling($rawWeight)
    $externalJobId = Get-BambuExternalJobId -Path $Path -FingerprintSource $fingerprintSource
    $createdAt = ([DateTimeOffset]$item.LastWriteTimeUtc).ToUnixTimeMilliseconds()

    $noteParts = New-Object System.Collections.Generic.List[string]
    $noteParts.Add("来自 Bambu Studio 切片文件 $([System.IO.Path]::GetFileName($Path))")
    if ($printTime) {
        $noteParts.Add("打印时长 $printTime")
    }
    $noteParts.Add("原始估算 $([math]::Round($rawWeight, 2))g")
    if ($printerModel) {
        $noteParts.Add($printerModel)
    }

    return [pscustomobject]@{
        job = [pscustomobject]@{
            externalJobId = $externalJobId
            source = "DESKTOP_AGENT"
            modelName = $modelName
            estimatedUsageGrams = $estimatedUsageGrams
            targetMaterial = $materialResolution.material
            note = [string]::Join(" · ", $noteParts)
            createdAt = $createdAt
        }
        warnings = $warnings.ToArray()
    }
}

function Get-BambuGcodeJobs {
    param(
        [string[]]$ExplicitPaths,
        [string[]]$SearchRoots,
        [int]$FileAgeDays
    )

    $warnings = New-Object System.Collections.Generic.List[string]
    $jobs = New-Object System.Collections.Generic.List[object]
    $scannedFiles = New-Object System.Collections.Generic.List[string]
    $candidates = Resolve-GcodeCandidates -ExplicitPaths $ExplicitPaths -SearchRoots $SearchRoots -FileAgeDays $FileAgeDays

    if ($candidates.Count -eq 0) {
        $warnings.Add("未找到最近生成的 Bambu Studio gcode 文件。")
    }

    foreach ($candidate in $candidates) {
        $scannedFiles.Add($candidate.FullName)
        try {
            $result = Convert-BambuGcodeToJob -Path $candidate.FullName
            $job = [pscustomobject]@{
                externalJobId = $result.job.externalJobId
                source = $result.job.source
                modelName = $result.job.modelName
                estimatedUsageGrams = $result.job.estimatedUsageGrams
                targetMaterial = $result.job.targetMaterial
                note = "来自 Bambu Studio 切片文件 $([System.IO.Path]::GetFileName($candidate.FullName))"
                createdAt = $result.job.createdAt
            }
            $jobs.Add($job)
            foreach ($warning in $result.warnings) {
                $warnings.Add($warning)
            }
        } catch {
            $warnings.Add("已忽略切片文件 '$($candidate.FullName)'：$($_.Exception.Message)")
        }
    }

    return [pscustomobject]@{
        jobs = $jobs.ToArray()
        warnings = $warnings.ToArray()
        scannedFiles = $scannedFiles.ToArray()
    }
}

function Normalize-Job {
    param(
        $Job,
        [long]$Now
    )

    $required = @("externalJobId", "modelName", "estimatedUsageGrams", "createdAt")
    foreach ($field in $required) {
        if (-not $Job.PSObject.Properties.Name.Contains($field)) {
            throw "Inbox job is missing required field '$field'."
        }
    }

    if ([string]::IsNullOrWhiteSpace([string]$Job.externalJobId)) {
        throw "externalJobId cannot be blank."
    }

    if ([string]::IsNullOrWhiteSpace([string]$Job.modelName)) {
        throw "modelName cannot be blank for '$($Job.externalJobId)'."
    }

    $usage = [int]$Job.estimatedUsageGrams
    if ($usage -le 0) {
        throw "estimatedUsageGrams must be greater than 0 for '$($Job.externalJobId)'."
    }

    $source = if ([string]::IsNullOrWhiteSpace([string]$Job.source)) {
        "DESKTOP_AGENT"
    } else {
        [string]$Job.source
    }

    if ($SupportedSources -notcontains $source) {
        throw "source '$source' is not supported for '$($Job.externalJobId)'."
    }

    $targetMaterial = if ($Job.PSObject.Properties.Name.Contains("targetMaterial")) { [string]$Job.targetMaterial } else { $null }
    if (-not [string]::IsNullOrWhiteSpace($targetMaterial) -and $SupportedMaterials -notcontains $targetMaterial) {
        throw "targetMaterial '$targetMaterial' is not supported for '$($Job.externalJobId)'."
    }

    $createdAt = [long]$Job.createdAt
    if ($createdAt -lt $EarliestAllowedTimestamp -or $createdAt -gt ($Now + $MaxFutureDriftMs)) {
        throw "createdAt '$createdAt' is outside the accepted range for '$($Job.externalJobId)'."
    }

    return [pscustomobject]@{
        externalJobId = [string]$Job.externalJobId
        source = $source
        modelName = ([string]$Job.modelName).Trim()
        estimatedUsageGrams = $usage
        targetMaterial = if ([string]::IsNullOrWhiteSpace($targetMaterial)) { $null } else { $targetMaterial.Trim() }
        note = if ($Job.PSObject.Properties.Name.Contains("note")) { [string]$Job.note } else { "" }
        createdAt = $createdAt
    }
}

function Get-JobSignature {
    param($Job)
    return "$($Job.modelName)|$($Job.estimatedUsageGrams)|$($Job.targetMaterial)|$($Job.note)|$($Job.createdAt)"
}

$inputMode = $null
$sourceJobs = @()
$sourceWarnings = New-Object System.Collections.Generic.List[string]
$scannedGcodeFiles = @()

if ($UseSample) {
    $inputMode = "sample-json"
    $sourceJobs = Read-JsonArray -Path $InboxPath
} elseif ($UseBambuGcode -or $GcodePaths.Count -gt 0 -or $GcodeSearchRoots.Count -gt 0) {
    $inputMode = "bambu-gcode"
    $gcodeResult = Get-BambuGcodeJobs -ExplicitPaths $GcodePaths -SearchRoots $GcodeSearchRoots -FileAgeDays $MaxFileAgeDays
    $sourceJobs = $gcodeResult.jobs
    $scannedGcodeFiles = $gcodeResult.scannedFiles
    foreach ($warning in $gcodeResult.warnings) {
        $sourceWarnings.Add($warning)
    }
    Write-PrettyJson -Value $sourceJobs -Path $GeneratedInboxPath -ForceArray
} elseif ($inboxPathProvided -or (Test-Path -LiteralPath $InboxPath)) {
    $inputMode = "inbox-json"
    $sourceJobs = Read-JsonArray -Path $InboxPath
} else {
    $inputMode = "bambu-gcode"
    $gcodeResult = Get-BambuGcodeJobs -ExplicitPaths $GcodePaths -SearchRoots $GcodeSearchRoots -FileAgeDays $MaxFileAgeDays
    $sourceJobs = $gcodeResult.jobs
    $scannedGcodeFiles = $gcodeResult.scannedFiles
    foreach ($warning in $gcodeResult.warnings) {
        $sourceWarnings.Add($warning)
    }
    Write-PrettyJson -Value $sourceJobs -Path $GeneratedInboxPath -ForceArray
}

$state = Read-State -Path $StatePath
$stateJobs = @{}
foreach ($job in $state.jobs) {
    $stateJobs[[string]$job.externalJobId] = $job
}

$warnings = New-Object System.Collections.Generic.List[string]
foreach ($warning in $sourceWarnings) {
    $warnings.Add($warning)
}
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

$confirmations = Read-JsonArray -Path $ConfirmationPath
foreach ($confirmation in $confirmations) {
    $externalJobId = [string]$confirmation.externalJobId
    if ([string]::IsNullOrWhiteSpace($externalJobId)) {
        $warnings.Add("Ignored confirmation with blank externalJobId.")
        continue
    }
    if (-not $confirmation.PSObject.Properties.Name.Contains("confirmedAt")) {
        $warnings.Add("Ignored confirmation '$externalJobId' because confirmedAt is missing.")
        continue
    }
    $confirmedAt = [long]$confirmation.confirmedAt
    if ($confirmedAt -lt $EarliestAllowedTimestamp -or $confirmedAt -gt ($now + $MaxFutureDriftMs)) {
        $warnings.Add("Ignored confirmation '$externalJobId' because confirmedAt is outside the accepted range.")
        continue
    }
    if ($stateJobs.ContainsKey($externalJobId)) {
        $existing = $stateJobs[$externalJobId]
        if ($existing.status -ne "CONFIRMED") {
            $existing.status = "CONFIRMED"
            $existing.confirmedAt = $confirmedAt
            $existing.targetRollId = if ($confirmation.PSObject.Properties.Name.Contains("targetRollId")) { $confirmation.targetRollId } else { $null }
        }
        $stateJobs[$externalJobId] = $existing
    } else {
        $warnings.Add("Ignored confirmation '$externalJobId' because the job does not exist in local state.")
    }
}

$normalizedJobs = @()
$seenExternalJobIds = @{}
for ($index = 0; $index -lt $sourceJobs.Count; $index++) {
    $job = $sourceJobs[$index]
    try {
        $normalized = Normalize-Job -Job $job -Now $now
        if ($seenExternalJobIds.ContainsKey($normalized.externalJobId)) {
            $warnings.Add("Inbox contains duplicate externalJobId '$($normalized.externalJobId)'; latest payload wins.")
        }
        $seenExternalJobIds[$normalized.externalJobId] = $true
        $normalizedJobs += $normalized
    } catch {
        $warnings.Add("Rejected inbox job[$index]: $($_.Exception.Message)")
    }
}

foreach ($job in $normalizedJobs) {
    $signature = Get-JobSignature -Job $job
    if ($stateJobs.ContainsKey($job.externalJobId)) {
        $existing = $stateJobs[$job.externalJobId]
        if ($existing.status -ne "CONFIRMED") {
            $existing.source = $job.source
            $existing.modelName = $job.modelName
            $existing.estimatedUsageGrams = $job.estimatedUsageGrams
            $existing.targetMaterial = $job.targetMaterial
            $existing.note = $job.note
            $existing.createdAt = $job.createdAt
            $existing.signature = $signature
            $existing.lastSeenAt = $now
            $stateJobs[$job.externalJobId] = $existing
        }
    } else {
        $stateJobs[$job.externalJobId] = [pscustomobject]@{
            externalJobId = $job.externalJobId
            source = $job.source
            modelName = $job.modelName
            estimatedUsageGrams = $job.estimatedUsageGrams
            targetMaterial = $job.targetMaterial
            note = $job.note
            createdAt = $job.createdAt
            signature = $signature
            status = "DRAFT"
            lastSeenAt = $now
            confirmedAt = $null
            targetRollId = $null
        }
    }
}

$drafts = @(
    $stateJobs.Values |
        Where-Object { $_.status -eq "DRAFT" } |
        Sort-Object createdAt |
        ForEach-Object {
            [pscustomobject]@{
                externalJobId = $_.externalJobId
                source = $_.source
                modelName = $_.modelName
                estimatedUsageGrams = $_.estimatedUsageGrams
                targetMaterial = $_.targetMaterial
                note = $_.note
                createdAt = $_.createdAt
            }
        }
)

$persistedState = [pscustomobject]@{
    updatedAt = $now
    inputMode = $inputMode
    warnings = @($warnings)
    jobs = @(
        $stateJobs.Values |
            Sort-Object createdAt |
            ForEach-Object {
                [pscustomobject]@{
                    externalJobId = $_.externalJobId
                    source = $_.source
                    modelName = $_.modelName
                    estimatedUsageGrams = $_.estimatedUsageGrams
                    targetMaterial = $_.targetMaterial
                    note = $_.note
                    createdAt = $_.createdAt
                    signature = $_.signature
                    status = $_.status
                    lastSeenAt = $_.lastSeenAt
                    confirmedAt = $_.confirmedAt
                    targetRollId = $_.targetRollId
                }
            }
    )
}

Write-PrettyJson -Value $drafts -Path $OutboxPath -ForceArray
Write-PrettyJson -Value $persistedState -Path $StatePath

Write-Output "Desktop sync agent finished."
Write-Output "Input mode: $inputMode"
Write-Output "Inbox: $InboxPath"
if ($inputMode -eq "bambu-gcode") {
    Write-Output "Generated inbox: $GeneratedInboxPath"
    Write-Output "Scanned gcode files: $($scannedGcodeFiles.Count)"
}
Write-Output "Draft outbox: $OutboxPath"
Write-Output "State: $StatePath"
Write-Output "Pending drafts: $($drafts.Count)"
Write-Output "Warnings: $($warnings.Count)"
foreach ($warning in $warnings) {
    Write-Warning $warning
}
