# =====================================================================
# Start OCR Service (Flask :5001, PaddleOCR / DeepSeek-OCR-2 engines)
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\start-ocr-local.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$ocrDir = Join-Path $PSScriptRoot "..\ocr-service"
$venvPath = Join-Path $ocrDir ".venv"
$venvPython = Join-Path $venvPath "Scripts\python.exe"

if (-not (Test-Path -LiteralPath (Join-Path $ocrDir "app.py"))) {
    Write-Error "ocr-service\app.py not found"
    exit 1
}

# Init .env if missing
if (-not (Test-Path -LiteralPath (Join-Path $ocrDir ".env"))) {
    Copy-Item -LiteralPath (Join-Path $ocrDir ".env.example") -Destination (Join-Path $ocrDir ".env") -Force
    Write-Host "Created ocr-service\.env with default PaddleOCR route." -ForegroundColor Yellow
}

# Create venv if missing
if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Host "==> Creating Python venv ..." -ForegroundColor Cyan
    python -m venv $venvPath
    if ($LASTEXITCODE -ne 0) { Write-Error "venv creation failed"; exit 1 }
}

# Install dependencies (paddle legacy route, manual inspection).
# 注意：不用管道接 Select（管道后 $LASTEXITCODE 会变成 Select 的退出码，导致安装失败被吞掉）
$pipLog = & $venvPython -m pip install -r (Join-Path $ocrDir "requirements-paddle-legacy.txt") 2>&1
$pipLog | Select-Object -Last 3
if ($LASTEXITCODE -ne 0) { Write-Error "pip install 失败，详见上方输出"; exit 1 }

Write-Host "==> Starting OCR service on :5001 ..." -ForegroundColor Cyan
Push-Location $ocrDir
try {
    & $venvPython app.py
} finally {
    Pop-Location
}
