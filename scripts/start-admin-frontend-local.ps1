# =====================================================================
# Start Admin Frontend (Vite :5175)
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\start-admin-frontend-local.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$adminDir = Join-Path $PSScriptRoot "..\admin-frontend"

if (-not (Test-Path -LiteralPath (Join-Path $adminDir "package.json"))) {
    Write-Error "admin-frontend\package.json not found"
    exit 1
}

if (-not (Test-Path -LiteralPath (Join-Path $adminDir "node_modules"))) {
    Write-Host "==> Installing dependencies (first run) ..." -ForegroundColor Cyan
    Push-Location $adminDir
    try { npm install } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Error "npm install failed"; exit 1 }
}

Write-Host "==> Starting admin frontend on :5175 ..." -ForegroundColor Cyan
Push-Location $adminDir
try {
    npm run dev
} finally {
    Pop-Location
}
