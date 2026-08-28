param(
    [string]$HostAddress = '127.0.0.1',
    [int]$Port = 5173
)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$frontendRoot = Join-Path $projectRoot 'frontend'
$packageJson = Join-Path $frontendRoot 'package.json'
$viteBinary = Join-Path $frontendRoot 'node_modules/.bin/vite.cmd'

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw 'Node.js is missing. Install Node.js 24 LTS or newer.'
}
if (-not (Test-Path -LiteralPath $packageJson -PathType Leaf)) {
    throw "Frontend package is missing: $packageJson"
}
if (-not (Test-Path -LiteralPath $viteBinary -PathType Leaf)) {
    throw "Frontend dependencies are missing. Run 'npm install' inside the frontend directory."
}

Write-Output "Clipador web -> http://${HostAddress}:$Port"
Push-Location $frontendRoot
try {
    & npm run dev -- --host $HostAddress --port $Port
    if ($LASTEXITCODE -ne 0) { throw "Frontend stopped with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
