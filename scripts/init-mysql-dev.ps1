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
Get-Command mysql -ErrorAction Stop | Out-Null
# source 需正斜杠路径并加引号（含空格路径），显式指定连接参数避免连错库
$src = ($schemaPath -replace '\\','/')
$env:MYSQL_PWD = $DB_PASSWORD
try {
    mysql -h 127.0.0.1 -P 3306 -u $DB_USERNAME --default-character-set=utf8mb4 -e "source `"$src`"" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "MySQL init failed. Check that MySQL is running and credentials are correct."
        exit 1
    }

    Write-Host "==> Verifying tables ..." -ForegroundColor Cyan
    mysql -h 127.0.0.1 -P 3306 -u $DB_USERNAME --default-character-set=utf8mb4 -e "USE zhixu_kb; SHOW TABLES;" 2>&1
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}
Write-Host "==> Done. Core tables: sys_user / note / category / ask_records / ai_endpoints" -ForegroundColor Green
