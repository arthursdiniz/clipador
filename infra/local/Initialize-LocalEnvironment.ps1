param([string]$EnvFile)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env.local' }

function New-LocalSecret {
    $bytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', 'A').Replace('/', 'B')
}

function Convert-ToEnvPath([string]$Path) {
    return $Path.Replace('\', '/')
}

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    $ffmpeg = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/ffmpeg') `
        -Filter 'ffmpeg.exe' -File -Recurse | Select-Object -First 1
    $ffprobe = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/ffmpeg') `
        -Filter 'ffprobe.exe' -File -Recurse | Select-Object -First 1
    $ytDlp = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/yt-dlp') `
        -Filter 'yt-dlp.exe' -File -Recurse | Select-Object -First 1
    if (-not $ffmpeg -or -not $ffprobe -or -not $ytDlp) {
        throw 'Portable media tools are missing. Run download_portable_tools.py first.'
    }
    $postgresPassword = New-LocalSecret
    $rabbitPassword = New-LocalSecret
    $securityPassword = New-LocalSecret
    $erlangCookie = New-LocalSecret
    $nodeName = "clipador@$env:COMPUTERNAME"
    $storageRoot = Convert-ToEnvPath (Join-Path $projectRoot 'data/storage')
    $tempRoot = Convert-ToEnvPath (Join-Path $projectRoot 'data/tmp')
    $inboxPath = Convert-ToEnvPath (Join-Path $projectRoot 'data/worker/inbox.sqlite3')
    $modelCache = Convert-ToEnvPath (Join-Path $projectRoot 'data/worker/models')
    $content = @(
        '# Generated local-only configuration. This file is ignored by Git.'
        'SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/clipador'
        'SPRING_DATASOURCE_USERNAME=clipador'
        "SPRING_DATASOURCE_PASSWORD=$postgresPassword"
        'SPRING_RABBITMQ_HOST=127.0.0.1'
        'SPRING_RABBITMQ_PORT=5672'
        'SPRING_RABBITMQ_USERNAME=clipador'
        "SPRING_RABBITMQ_PASSWORD=$rabbitPassword"
        'RABBITMQ_HOST=127.0.0.1'
        'RABBITMQ_PORT=5672'
        'RABBITMQ_USER=clipador'
        "RABBITMQ_PASSWORD=$rabbitPassword"
        "CLIPADOR_RABBITMQ_ERLANG_COOKIE=$erlangCookie"
        "CLIPADOR_RABBITMQ_NODE_NAME=$nodeName"
        'CLIPADOR_SECURITY_USERNAME=admin'
        "CLIPADOR_SECURITY_PASSWORD=$securityPassword"
        'CLIPADOR_API_DOCS_PUBLIC=true'
        'CLIPADOR_MAX_CONCURRENT_REQUESTS=64'
        'CLIPADOR_MAX_CONCURRENT_UPLOADS=2'
        'CLIPADOR_QUEUE_METRICS_INTERVAL=PT15S'
        "CLIPADOR_STORAGE_ROOT=$storageRoot"
        "CLIPADOR_TEMP_ROOT=$tempRoot"
        "CLIPADOR_WORKER_INBOX=$inboxPath"
        "CLIPADOR_WHISPER_MODEL_CACHE=$modelCache"
        "CLIPADOR_FFMPEG_EXECUTABLE=$(Convert-ToEnvPath $ffmpeg.FullName)"
        "CLIPADOR_FFPROBE_EXECUTABLE=$(Convert-ToEnvPath $ffprobe.FullName)"
        "CLIPADOR_YT_DLP_EXECUTABLE=$(Convert-ToEnvPath $ytDlp.FullName)"
        'CLIPADOR_WORKER_TASKS=VALIDATE_MEDIA,EXTRACT_AUDIO,TRANSCRIBE_AUDIO,ANALYZE_CONTENT,RENDER_CLIPS'
        'CLIPADOR_ANALYSIS_PROVIDER=local'
        'CLIPADOR_SMART_REFRAMING_ENABLED=true'
    )
    [System.IO.File]::WriteAllLines(
        $EnvFile,
        [string[]]$content,
        (New-Object System.Text.UTF8Encoding($false))
    )
    Write-Output "Created protected local configuration: $EnvFile"
} else {
    Write-Output "Using existing local configuration: $EnvFile"
}

& (Join-Path $PSScriptRoot 'Start-LocalDependencies.ps1') -EnvFile $EnvFile
