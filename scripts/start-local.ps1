param(
    [switch]$CoreOnly
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return
    }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') {
            return
        }
        $name, $value = $_ -split '=', 2
        if ($name) {
            [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
        }
    }
}

Import-DotEnv (Join-Path $root ".env")

if (-not $env:JWT_SECRET) {
    throw "JWT_SECRET is required. Add it to .env or set it in the current shell."
}

if ($CoreOnly) {
    & .\gradlew.bat bootRunCoreLocal
    exit $LASTEXITCODE
}

if (Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "PostgreSQL is already listening on port 5432; skipping docker postgres."
    & docker compose up -d redis
} else {
    & docker compose up -d postgres redis
}

& .\gradlew.bat bootRun
