param([string]$EnvFile)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env.local' }
& (Join-Path $PSScriptRoot 'Import-ClipadorEnv.ps1') -EnvFile $EnvFile

if (-not $env:CLIPADOR_FFPROBE_EXECUTABLE) {
    $ffprobeCommand = Get-Command ffprobe -ErrorAction SilentlyContinue
    $bundledFfprobe = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/ffmpeg') `
        -Filter 'ffprobe.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    $shotcutFfprobe = 'C:\Program Files\Shotcut\ffprobe.exe'
    if ($bundledFfprobe) {
        $env:CLIPADOR_FFPROBE_EXECUTABLE = $bundledFfprobe.FullName
    } elseif ($ffprobeCommand) {
        $env:CLIPADOR_FFPROBE_EXECUTABLE = $ffprobeCommand.Source
    } elseif (Test-Path -LiteralPath $shotcutFfprobe -PathType Leaf) {
        $env:CLIPADOR_FFPROBE_EXECUTABLE = $shotcutFfprobe
    }
}

if (-not $env:CLIPADOR_STORAGE_ROOT) {
    $env:CLIPADOR_STORAGE_ROOT = Join-Path $projectRoot 'data/storage'
}
if (-not $env:CLIPADOR_TEMP_ROOT) {
    $env:CLIPADOR_TEMP_ROOT = Join-Path $projectRoot 'data/tmp'
}

Push-Location $projectRoot
try {
    & mvn -pl backend spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Backend stopped with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
