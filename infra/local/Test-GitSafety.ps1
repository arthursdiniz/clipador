$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath '.git' -PathType Container)) {
        throw 'Git repository not initialized. Run: git init -b main'
    }

    $tracked = @(git ls-files)
    if ($LASTEXITCODE -ne 0) { throw 'Could not list tracked files.' }

    $forbiddenPaths = @(
        '(?i)(^|/)\.env($|\.)',
        '(?i)(^|/)(data|storage|tmp|work|outputs|downloads|tools|models)/',
        '(?i)\.(mp4|mov|mkv|webm|wav|mp3|pem|key|p12|pfx|jks|keystore|sqlite|sqlite3|db)$',
        '(?i)(^|/)(credentials|secrets|service-account)[^/]*\.json$'
    )
    $allowedEnvironmentExamples = @('.env.example', '.env.local.example')
    $unsafe = foreach ($path in $tracked) {
        $normalized = $path.Replace('\', '/')
        foreach ($pattern in $forbiddenPaths) {
            if ($normalized -match $pattern -and $normalized -notin $allowedEnvironmentExamples) {
                $normalized
                break
            }
        }
    }
    if ($unsafe) {
        $unsafe | Sort-Object -Unique | ForEach-Object { Write-Error "Sensitive or generated path is tracked: $_" }
        throw 'Git safety check failed because forbidden paths are tracked.'
    }

    $oversized = foreach ($path in $tracked) {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $item = Get-Item -LiteralPath $path
            if ($item.Length -gt 10MB) { "{0} ({1:N1} MiB)" -f $path, ($item.Length / 1MB) }
        }
    }
    if ($oversized) {
        $oversized | ForEach-Object { Write-Error "Unexpected large tracked file: $_" }
        throw 'Git safety check failed because files larger than 10 MiB are tracked.'
    }

    $secretPatterns = @(
        '-----BEGIN [A-Z ]*PRIVATE KEY-----',
        'AKIA[0-9A-Z]{16}',
        'github_pat_[A-Za-z0-9_]{20,}',
        'gh[pousr]_[A-Za-z0-9]{20,}',
        'sk-(proj-)?[A-Za-z0-9_-]{20,}',
        'xox[baprs]-[A-Za-z0-9-]{20,}'
    )
    $matches = foreach ($path in $tracked) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        $item = Get-Item -LiteralPath $path
        if ($item.Length -gt 2MB) { continue }
        $content = Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
        foreach ($pattern in $secretPatterns) {
            if ($content -match $pattern) {
                $path
                break
            }
        }
    }
    if ($matches) {
        $matches | Sort-Object -Unique | ForEach-Object { Write-Error "Possible credential detected in tracked file: $_" }
        throw 'Git safety check failed because possible credentials were detected.'
    }

    Write-Host "Git safety check passed for $($tracked.Count) tracked files."
} finally {
    Pop-Location
}
