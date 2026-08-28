param([string]$EnvFile)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env.local' }
& (Join-Path $PSScriptRoot 'Import-ClipadorEnv.ps1') -EnvFile $EnvFile

$postgresBin = 'C:\Program Files\PostgreSQL\18\bin'
$initDb = Join-Path $postgresBin 'initdb.exe'
$pgCtl = Join-Path $postgresBin 'pg_ctl.exe'
$pgReady = Join-Path $postgresBin 'pg_isready.exe'
$psql = Join-Path $postgresBin 'psql.exe'
$createDb = Join-Path $postgresBin 'createdb.exe'
foreach ($required in @($initDb, $pgCtl, $pgReady, $psql, $createDb)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "PostgreSQL 18 executable is missing: $required"
    }
}
$postgresData = Join-Path $projectRoot 'data/postgres'
$postgresLog = Join-Path $projectRoot 'data/logs/postgres.log'
New-Item -ItemType Directory -Path (Split-Path -Parent $postgresLog) -Force | Out-Null
$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
if (-not (Test-Path -LiteralPath (Join-Path $postgresData 'PG_VERSION') -PathType Leaf)) {
    New-Item -ItemType Directory -Path $postgresData -Force | Out-Null
    $passwordFile = Join-Path $projectRoot 'data/.postgres-password.tmp'
    try {
        [System.IO.File]::WriteAllText(
            $passwordFile,
            $env:SPRING_DATASOURCE_PASSWORD,
            (New-Object System.Text.UTF8Encoding($false))
        )
        & $initDb --pgdata $postgresData --username clipador --encoding UTF8 `
            --auth-host scram-sha-256 --auth-local scram-sha-256 --pwfile $passwordFile
        if ($LASTEXITCODE -ne 0) { throw 'Could not initialize Clipador PostgreSQL cluster.' }
    } finally {
        Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue
    }
}
$readyOutput = & $pgReady -h 127.0.0.1 -p 55432 2>$null
if ($LASTEXITCODE -ne 0) {
    & $pgCtl start --pgdata $postgresData --log $postgresLog `
        --options '-p 55432 -h 127.0.0.1' --wait --timeout 30
    if ($LASTEXITCODE -ne 0) { throw 'Could not start Clipador PostgreSQL cluster.' }
}
$databaseResult = & $psql -w -h 127.0.0.1 -p 55432 -U clipador -d postgres `
    -tAc "SELECT 1 FROM pg_database WHERE datname = 'clipador'"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect Clipador databases.' }
$databaseExists = ($databaseResult | Out-String).Trim()
if ($databaseExists -ne '1') {
    & $createDb -w -h 127.0.0.1 -p 55432 -U clipador --owner clipador clipador
    if ($LASTEXITCODE -ne 0) { throw 'Could not create Clipador database.' }
}
Write-Output 'OK PostgreSQL -> 127.0.0.1:55432/clipador'

$erl = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/erlang') `
    -Filter 'erl.exe' -File -Recurse | Select-Object -First 1
$rabbitCtl = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/rabbitmq') `
    -Filter 'rabbitmqctl.bat' -File -Recurse | Select-Object -First 1
if (-not $erl -or -not $rabbitCtl) { throw 'Portable Erlang or RabbitMQ is missing.' }
$rabbitSbin = $rabbitCtl.Directory.FullName
$rabbitServer = Join-Path $rabbitSbin 'rabbitmq-server.bat'
$rabbitPlugins = Join-Path $rabbitSbin 'rabbitmq-plugins.bat'
$rabbitDiagnostics = Join-Path $rabbitSbin 'rabbitmq-diagnostics.bat'
$env:ERLANG_HOME = Split-Path -Parent $erl.Directory.FullName
$env:RABBITMQ_BASE = Join-Path $projectRoot 'data/rabbitmq'
$env:RABBITMQ_MNESIA_BASE = Join-Path $env:RABBITMQ_BASE 'mnesia'
$env:RABBITMQ_LOG_BASE = Join-Path $projectRoot 'data/logs/rabbitmq'
$env:RABBITMQ_PID_FILE = Join-Path $env:RABBITMQ_BASE 'rabbitmq.pid'
$env:RABBITMQ_CONFIG_FILE = Join-Path $env:RABBITMQ_BASE 'rabbitmq'
$env:RABBITMQ_NODENAME = $env:CLIPADOR_RABBITMQ_NODE_NAME
$env:RABBITMQ_ERLANG_COOKIE = $env:CLIPADOR_RABBITMQ_ERLANG_COOKIE
$rabbitDirectories = @($env:RABBITMQ_BASE, $env:RABBITMQ_MNESIA_BASE, $env:RABBITMQ_LOG_BASE)
New-Item -ItemType Directory -Path $rabbitDirectories -Force | Out-Null
$rabbitConfig = @(
    'listeners.tcp.1 = 127.0.0.1:5672'
    'management.tcp.ip = 127.0.0.1'
    'management.tcp.port = 15672'
    'collect_statistics_interval = 5000'
)
[System.IO.File]::WriteAllLines(
    ($env:RABBITMQ_CONFIG_FILE + '.conf'),
    [string[]]$rabbitConfig,
    (New-Object System.Text.UTF8Encoding($false))
)
& $rabbitPlugins --offline enable rabbitmq_management | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not enable RabbitMQ management plugin.' }
& $rabbitDiagnostics --node $env:RABBITMQ_NODENAME `
    --erlang-cookie $env:RABBITMQ_ERLANG_COOKIE -q check_running *> $null
$rabbitReady = $LASTEXITCODE -eq 0
if (-not $rabbitReady) {
    Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', "`"$rabbitServer`" -detached") `
        -WindowStyle Hidden
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        Start-Sleep -Milliseconds 750
        & $rabbitDiagnostics --node $env:RABBITMQ_NODENAME `
            --erlang-cookie $env:RABBITMQ_ERLANG_COOKIE -q check_running *> $null
        $rabbitReady = $LASTEXITCODE -eq 0
    } while (-not $rabbitReady -and [DateTime]::UtcNow -lt $deadline)
    if (-not $rabbitReady) { throw 'RabbitMQ did not become ready within 45 seconds.' }
}
$users = (& $rabbitCtl.FullName --node $env:RABBITMQ_NODENAME `
    --erlang-cookie $env:RABBITMQ_ERLANG_COOKIE -q list_users) -join "`n"
if ($users -notmatch '(?m)^clipador\s') {
    & $rabbitCtl.FullName --node $env:RABBITMQ_NODENAME --erlang-cookie $env:RABBITMQ_ERLANG_COOKIE `
        add_user clipador $env:RABBITMQ_PASSWORD *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not create RabbitMQ user.' }
}
& $rabbitCtl.FullName --node $env:RABBITMQ_NODENAME --erlang-cookie $env:RABBITMQ_ERLANG_COOKIE `
    set_user_tags clipador administrator *> $null
if ($LASTEXITCODE -ne 0) { throw 'Could not assign RabbitMQ user tags.' }
& $rabbitCtl.FullName --node $env:RABBITMQ_NODENAME --erlang-cookie $env:RABBITMQ_ERLANG_COOKIE `
    set_permissions -p / clipador '.*' '.*' '.*' *> $null
if ($LASTEXITCODE -ne 0) { throw 'Could not assign RabbitMQ permissions.' }
if ($users -match '(?m)^guest\s') {
    & $rabbitCtl.FullName --node $env:RABBITMQ_NODENAME --erlang-cookie $env:RABBITMQ_ERLANG_COOKIE `
        delete_user guest *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not remove the RabbitMQ guest user.' }
}
Write-Output 'OK RabbitMQ -> 127.0.0.1:5672 (management 127.0.0.1:15672)'
