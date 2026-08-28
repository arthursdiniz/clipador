param([string]$EnvFile)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env.local' }
$statePath = Join-Path $projectRoot 'data/run/clipador-processes.json'

if (Test-Path -LiteralPath $statePath -PathType Leaf) {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($state.schemaVersion -ne 1 -or $state.projectRoot -ne $projectRoot) {
        throw 'Invalid Clipador process state file. Refusing to stop unverified processes.'
    }

    $remainingProcesses = @()
    foreach ($entry in @($state.processes)) {
        $process = Get-Process -Id $entry.processId -ErrorAction SilentlyContinue
        if (-not $process) {
            Write-Output "$($entry.name) was already stopped."
            continue
        }

        if (-not $entry.startTimeFileTimeUtc -or
                $process.StartTime.ToFileTimeUtc() -ne [long]$entry.startTimeFileTimeUtc) {
            Write-Warning "PID $($entry.processId) was reused; $($entry.name) will not be stopped."
            $remainingProcesses += $entry
            continue
        }

        & taskkill.exe /PID $entry.processId /T /F 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0 -and (Get-Process -Id $entry.processId -ErrorAction SilentlyContinue)) {
            Write-Warning "Could not stop $($entry.name)."
            $remainingProcesses += $entry
        } else {
            Write-Output "Stopped $($entry.name)."
        }
    }
    if ($remainingProcesses.Count -gt 0) {
        $state.processes = $remainingProcesses
        [System.IO.File]::WriteAllText(
            $statePath,
            ($state | ConvertTo-Json -Depth 5),
            (New-Object System.Text.UTF8Encoding($false))
        )
    } else {
        Remove-Item -LiteralPath $statePath -Force
    }
} else {
    Write-Output 'No Clipador application processes were registered by the unified launcher.'
}

& (Join-Path $PSScriptRoot 'Stop-LocalDependencies.ps1') -EnvFile $EnvFile
Write-Output 'Clipador stopped.'
