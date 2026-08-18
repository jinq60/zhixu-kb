# =====================================================================
# Start User Frontend (Vite :5173)
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\start-frontend-local.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$frontendDir = Join-Path $PSScriptRoot "..\frontend"

if (-not (Test-Path -LiteralPath (Join-Path $frontendDir "package.json"))) {
    Write-Error "frontend\package.json not found"
    exit 1
}

if (-not (Test-Path -LiteralPath (Join-Path $frontendDir "node_modules"))) {
    Write-Host "==> Installing dependencies (first run) ..." -ForegroundColor Cyan
    Push-Location $frontendDir
    try { npm install } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Error "npm install failed"; exit 1 }
}

Write-Host "==> Starting user frontend on :5173 ..." -ForegroundColor Cyan
Push-Location $frontendDir
try {
    npm run dev
} finally {
    Pop-Location
}
