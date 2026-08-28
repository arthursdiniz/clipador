$required = @('java', 'mvn', 'ffmpeg', 'ffprobe', 'yt-dlp', 'psql', 'rabbitmqctl')
$missing = @()
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
foreach ($name in $required) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    $portableExecutable = switch ($name) {
        'yt-dlp' {
            Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/yt-dlp') `
                -Filter 'yt-dlp.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        }
        'rabbitmqctl' {
            Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/rabbitmq') `
                -Filter 'rabbitmqctl.bat' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        }
        'psql' {
            $systemPsql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
            if (Test-Path -LiteralPath $systemPsql -PathType Leaf) { Get-Item -LiteralPath $systemPsql }
        }
    }
    if (-not $command -and $portableExecutable) {
        Write-Output ("OK      {0} -> {1}" -f $name, $portableExecutable.FullName)
        continue
    }
    if (-not $command -and $name -in @('ffmpeg', 'ffprobe')) {
        $bundledExecutable = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/ffmpeg') `
            -Filter "$name.exe" -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($bundledExecutable) {
            Write-Output ("OK      {0} -> {1}" -f $name, $bundledExecutable.FullName)
            continue
        }
        $shotcutExecutable = "C:\Program Files\Shotcut\$name.exe"
        if (Test-Path -LiteralPath $shotcutExecutable -PathType Leaf) {
            Write-Output ("OK      {0} -> {1}" -f $name, $shotcutExecutable)
            continue
        }
    }
    if ($command) {
        Write-Output ("OK      {0} -> {1}" -f $name, $command.Source)
    } else {
        Write-Output ("MISSING {0}" -f $name)
        $missing += $name
    }
}

$ffmpegCapabilityPath = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/ffmpeg') `
    -Filter 'ffmpeg.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (-not $ffmpegCapabilityPath) {
    $ffmpegCapabilityCommand = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($ffmpegCapabilityCommand) { $ffmpegCapabilityPath = $ffmpegCapabilityCommand.Source }
}
if (-not $ffmpegCapabilityPath -and (Test-Path -LiteralPath 'C:\Program Files\Shotcut\ffmpeg.exe')) {
    $ffmpegCapabilityPath = 'C:\Program Files\Shotcut\ffmpeg.exe'
}
if ($ffmpegCapabilityPath) {
    $filters = (& $ffmpegCapabilityPath -hide_banner -filters 2>&1) -join "`n"
    $encoders = (& $ffmpegCapabilityPath -hide_banner -encoders 2>&1) -join "`n"
    if ($filters -notmatch '\bsubtitles\b' -or $encoders -notmatch '\blibx264\b') {
        Write-Output 'MISSING ffmpeg capabilities: libass/subtitles and libx264 are required'
        $missing += 'ffmpeg(libass/libx264)'
    } else {
        Write-Output 'OK      ffmpeg capabilities -> libass/subtitles + libx264'
    }
}

$workerPython = Join-Path $projectRoot 'media-worker/.venv/Scripts/python.exe'
if (Test-Path -LiteralPath $workerPython -PathType Leaf) {
    Write-Output ("OK      worker Python -> {0}" -f $workerPython)
} else {
    Write-Output 'MISSING media-worker/.venv'
    $missing += 'media-worker/.venv'
}

if ($missing.Count -gt 0) {
    Write-Output ("Missing prerequisites: {0}" -f ($missing -join ', '))
    exit 1
}
Write-Output 'Native prerequisites are available.'
