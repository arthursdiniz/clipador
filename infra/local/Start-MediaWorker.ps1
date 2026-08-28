param([string]$EnvFile)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env.local' }
& (Join-Path $PSScriptRoot 'Import-ClipadorEnv.ps1') -EnvFile $EnvFile

if (-not $env:CLIPADOR_FFMPEG_EXECUTABLE) {
    $ffmpegCommand = Get-Command ffmpeg -ErrorAction SilentlyContinue
    $bundledFfmpeg = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/ffmpeg') `
        -Filter 'ffmpeg.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    $shotcutFfmpeg = 'C:\Program Files\Shotcut\ffmpeg.exe'
    if ($bundledFfmpeg) {
        $env:CLIPADOR_FFMPEG_EXECUTABLE = $bundledFfmpeg.FullName
    } elseif ($ffmpegCommand) {
        $env:CLIPADOR_FFMPEG_EXECUTABLE = $ffmpegCommand.Source
    } elseif (Test-Path -LiteralPath $shotcutFfmpeg -PathType Leaf) {
        $env:CLIPADOR_FFMPEG_EXECUTABLE = $shotcutFfmpeg
    }
}

if (-not $env:CLIPADOR_STORAGE_ROOT) {
    $env:CLIPADOR_STORAGE_ROOT = Join-Path $projectRoot 'data/storage'
}
if (-not $env:CLIPADOR_WORKER_INBOX) {
    $env:CLIPADOR_WORKER_INBOX = Join-Path $projectRoot 'data/worker/inbox.sqlite3'
}
if (-not $env:CLIPADOR_WHISPER_MODEL_CACHE) {
    $env:CLIPADOR_WHISPER_MODEL_CACHE = Join-Path $projectRoot 'data/worker/models'
}
$workerRoot = Join-Path $projectRoot 'media-worker'
$python = Join-Path $workerRoot '.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $python -PathType Leaf)) {
    throw 'Worker virtual environment is missing. Create media-worker/.venv and install the project first.'
}
$env:PYTHONPATH = Join-Path $workerRoot 'src'

Push-Location $workerRoot
try {
    & $python -m uvicorn clipador_worker.app:app --host 127.0.0.1 --port 8090
    if ($LASTEXITCODE -ne 0) { throw "Media worker stopped with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
