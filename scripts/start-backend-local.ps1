# =====================================================================
# Start Backend (Spring Boot :8080, requires Java 8)
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\start-backend-local.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$backendDir = Join-Path $PSScriptRoot "..\backend"

# Load env templates (.env.local / .env.secrets.local) into process env
$envFiles = @(
    (Join-Path $PSScriptRoot "..\.env.local"),
    (Join-Path $PSScriptRoot "..\.env.secrets.local")
)
foreach ($envFile in $envFiles) {
    if (Test-Path -LiteralPath $envFile) {
        Get-Content -LiteralPath $envFile | ForEach-Object {
            if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
                $key = $Matches[1]; $value = $Matches[2]
                if (-not [string]::IsNullOrWhiteSpace($value)) {
                    Set-Item -Path "Env:$key" -Value $value -ErrorAction SilentlyContinue
                }
            }
        }
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $backendDir "mvnw.cmd"))) {
    Write-Error "backend\mvnw.cmd not found"
    exit 1
}

Write-Host "==> Starting backend (requires Java 8) ..." -ForegroundColor Cyan
Push-Location $backendDir
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
