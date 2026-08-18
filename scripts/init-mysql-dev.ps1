# =====================================================================
# Init MySQL (zhixu_kb)
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\init-mysql-dev.ps1
# =====================================================================
param(
    [string]$DB_USERNAME = "root",
    [string]$DB_PASSWORD = "root"
)

$ErrorActionPreference = "Stop"

# Load DB credentials from .env.local / .env.secrets.local if present
$envFiles = @(
    (Join-Path $PSScriptRoot "..\.env.local"),
    (Join-Path $PSScriptRoot "..\.env.secrets.local")
)
foreach ($envFile in $envFiles) {
    if (Test-Path -LiteralPath $envFile) {
        Get-Content -LiteralPath $envFile | ForEach-Object {
            if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
                $key = $Matches[1]; $value = $Matches[2]
                switch ($key) {
                    "DB_USERNAME" { $script:DB_USERNAME = $value }
                    "DB_PASSWORD" { $script:DB_PASSWORD = $value }
                }
            }
        }
    }
}

$schemaPath = Join-Path $PSScriptRoot "..\backend\sql\mysql-schema.sql"
if (-not (Test-Path -LiteralPath $schemaPath)) {
    Write-Error "Schema file not found: $schemaPath"
    exit 1
}

Write-Host "==> Initializing database zhixu_kb (user: $DB_USERNAME) ..." -ForegroundColor Cyan
mysql -u $DB_USERNAME -p"$DB_PASSWORD" -e "source $schemaPath" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "MySQL init failed. Check that MySQL is running and credentials are correct."
    exit 1
}

Write-Host "==> Verifying tables ..." -ForegroundColor Cyan
mysql -u $DB_USERNAME -p"$DB_PASSWORD" -e "USE zhixu_kb; SHOW TABLES;" 2>&1
Write-Host "==> Done. Core tables: sys_user / note / category / ask_records / ai_endpoints" -ForegroundColor Green
