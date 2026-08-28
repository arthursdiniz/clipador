param([string]$EnvFile)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env.local' }
& (Join-Path $PSScriptRoot 'Import-ClipadorEnv.ps1') -EnvFile $EnvFile

$rabbitCtl = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/rabbitmq') `
    -Filter 'rabbitmqctl.bat' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
$erl = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools/erlang') `
    -Filter 'erl.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if ($rabbitCtl -and $erl) {
    $env:ERLANG_HOME = Split-Path -Parent $erl.Directory.FullName
    $env:RABBITMQ_NODENAME = $env:CLIPADOR_RABBITMQ_NODE_NAME
    & $rabbitCtl.FullName --node $env:RABBITMQ_NODENAME `
        --erlang-cookie $env:CLIPADOR_RABBITMQ_ERLANG_COOKIE shutdown 2>$null | Out-Null
}

$pgCtl = 'C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe'
$postgresData = Join-Path $projectRoot 'data/postgres'
if ((Test-Path -LiteralPath $pgCtl -PathType Leaf) -and
        (Test-Path -LiteralPath (Join-Path $postgresData 'postmaster.pid') -PathType Leaf)) {
    & $pgCtl stop --pgdata $postgresData --mode fast --wait --timeout 30 | Out-Null
}
Write-Output 'Clipador local dependencies stopped.'
