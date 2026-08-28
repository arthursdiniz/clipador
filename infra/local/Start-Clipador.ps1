param(
    [string]$EnvFile,
    [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env.local' }
if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Environment file is missing. Run .\infra\local\Initialize-LocalEnvironment.ps1 first."
}
$EnvFile = (Resolve-Path -LiteralPath $EnvFile).Path

$runDirectory = Join-Path $projectRoot 'data/run'
$logDirectory = Join-Path $projectRoot 'data/logs/apps'
$statePath = Join-Path $runDirectory 'clipador-processes.json'
New-Item -ItemType Directory -Path $runDirectory, $logDirectory -Force | Out-Null

function Test-HttpEndpoint {
    param([Parameter(Mandatory = $true)][string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        return $false
    }
}

function Test-ListeningPort {
    param([Parameter(Mandatory = $true)][int]$Port)
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1)
}

function Start-HiddenClipadorProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ScriptPath,
        [switch]$UsesEnvironmentFile
    )

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $stdout = Join-Path $logDirectory "$Name-$timestamp.out.log"
    $stderr = Join-Path $logDirectory "$Name-$timestamp.err.log"
    $arguments = @(
        '-NoLogo',
        '-NoProfile',
        '-NonInteractive',
        '-ExecutionPolicy', 'Bypass',
        '-File', ('"{0}"' -f $ScriptPath)
    )
    if ($UsesEnvironmentFile) {
        $arguments += @('-EnvFile', ('"{0}"' -f $EnvFile))
    }

    $process = Start-Process -FilePath $script:powerShellExecutable `
        -ArgumentList $arguments `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    return [pscustomobject]@{
        name = $Name
        processId = $process.Id
        startTimeFileTimeUtc = $process.StartTime.ToFileTimeUtc()
        stdoutLog = $stdout
        stderrLog = $stderr
    }
}

function Wait-ForClipadorEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$ProcessId,
        [Parameter(Mandatory = $true)][string]$ErrorLog
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (Test-HttpEndpoint -Url $Url) {
            Write-Output "OK $Name -> $Url"
            return
        }
        if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
            throw "$Name stopped during startup. Check: $ErrorLog"
        }
        Start-Sleep -Milliseconds 750
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "$Name did not become ready within $TimeoutSeconds seconds. Check: $ErrorLog"
}

$applicationEndpoints = @(
    'http://127.0.0.1:8080/actuator/health',
    'http://127.0.0.1:8090/health',
    'http://127.0.0.1:5173/'
)
if (($applicationEndpoints | Where-Object { Test-HttpEndpoint -Url $_ }).Count -eq 3) {
    Write-Output 'Clipador is already running.'
    Write-Output 'Interface -> http://127.0.0.1:5173'
    if ($OpenBrowser) {
        Start-Process 'http://127.0.0.1:5173'
    }
    return
}

$occupiedApplicationPorts = @(
    @(5173, 8080, 8090) | Where-Object { Test-ListeningPort -Port $_ }
)
if ($occupiedApplicationPorts.Count -gt 0) {
    throw "Could not start Clipador because these ports are already in use: $($occupiedApplicationPorts -join ', ')"
}

Write-Output 'Checking native prerequisites...'
& (Join-Path $PSScriptRoot 'Test-Prerequisites.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Clipador prerequisites are incomplete.' }

Write-Output 'Starting PostgreSQL and RabbitMQ...'
$savedErrorActionPreference = $ErrorActionPreference
try {
    # Windows PowerShell 5 wraps expected native stderr from RabbitMQ diagnostics
    # as ErrorRecord. The dependency script validates native exit codes itself.
    $ErrorActionPreference = 'Continue'
    & (Join-Path $PSScriptRoot 'Start-LocalDependencies.ps1') -EnvFile $EnvFile
} finally {
    $ErrorActionPreference = $savedErrorActionPreference
}

$powerShellExecutable = (Get-Process -Id $PID).Path
$processes = @()
try {
    Write-Output 'Starting backend, media worker and frontend in the background...'
    $processes += Start-HiddenClipadorProcess -Name 'backend' `
        -ScriptPath (Join-Path $PSScriptRoot 'Start-Backend.ps1') -UsesEnvironmentFile
    $processes += Start-HiddenClipadorProcess -Name 'media-worker' `
        -ScriptPath (Join-Path $PSScriptRoot 'Start-MediaWorker.ps1') -UsesEnvironmentFile
    $processes += Start-HiddenClipadorProcess -Name 'frontend' `
        -ScriptPath (Join-Path $PSScriptRoot 'Start-Frontend.ps1')

    $state = [pscustomobject]@{
        schemaVersion = 1
        projectRoot = $projectRoot
        startedAtUtc = [DateTime]::UtcNow.ToString('O')
        processes = $processes
    }
    [System.IO.File]::WriteAllText(
        $statePath,
        ($state | ConvertTo-Json -Depth 5),
        (New-Object System.Text.UTF8Encoding($false))
    )

    $backend = $processes | Where-Object name -eq 'backend'
    $worker = $processes | Where-Object name -eq 'media-worker'
    $frontend = $processes | Where-Object name -eq 'frontend'
    Wait-ForClipadorEndpoint -Name 'Backend' -Url $applicationEndpoints[0] -TimeoutSeconds 120 `
        -ProcessId $backend.processId -ErrorLog $backend.stderrLog
    Wait-ForClipadorEndpoint -Name 'Media worker' -Url $applicationEndpoints[1] -TimeoutSeconds 60 `
        -ProcessId $worker.processId -ErrorLog $worker.stderrLog
    Wait-ForClipadorEndpoint -Name 'Frontend' -Url $applicationEndpoints[2] -TimeoutSeconds 60 `
        -ProcessId $frontend.processId -ErrorLog $frontend.stderrLog
} catch {
    foreach ($entry in $processes) {
        if (Get-Process -Id $entry.processId -ErrorAction SilentlyContinue) {
            & taskkill.exe /PID $entry.processId /T /F 2>$null | Out-Null
        }
    }
    Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
    throw
}

& (Join-Path $PSScriptRoot 'Import-ClipadorEnv.ps1') -EnvFile $EnvFile
Write-Output ''
Write-Output 'Clipador started successfully. This window can now be closed.'
Write-Output 'Interface -> http://127.0.0.1:5173'
Write-Output "Username  -> $env:CLIPADOR_SECURITY_USERNAME"
Write-Output "Logs      -> $logDirectory"
if ($OpenBrowser) {
    Start-Process 'http://127.0.0.1:5173'
}
