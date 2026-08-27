# =====================================================================
# Start Backend (Spring Boot :8080, requires JDK 17+)
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\start-backend-local.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$backendDir = Join-Path $PSScriptRoot "..\backend"

# ---------- JDK 17 探测：当前 java 低于 17 时自动从常见位置寻找并为本会话切换 ----------
function Get-JavaMajorVersion {
    try {
        $output = & java -version 2>&1 | Select-Object -First 1
        if ($output -match '"(\d+)\.') { return [int]$Matches[1] }
        if ($output -match '"(\d+)"') { return [int]$Matches[1] }
    } catch { }
    return 0
}

if ((Get-JavaMajorVersion) -lt 17) {
    $candidates = @(
        $env:JAVA_HOME,
        (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "jdk-?17" } | Select-Object -ExpandProperty FullName),
        (Get-ChildItem "D:\HZC\Java" -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "jdk-?17" } | Select-Object -ExpandProperty FullName)
    ) | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) }
    if ($candidates.Count -eq 0) {
        Write-Error "未找到 JDK 17+。请安装 Eclipse Temurin 17 并设置 JAVA_HOME（桌面版打包 jpackage 也依赖 17）"
        exit 1
    }
    $env:JAVA_HOME = $candidates[0]
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    Write-Host "==> Using JDK 17: $env:JAVA_HOME" -ForegroundColor Yellow
}

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

Write-Host "==> Starting backend (requires JDK 17+) ..." -ForegroundColor Cyan
Push-Location $backendDir
try {
    # 本机若使用 Clash/Mihomo 等 fake-ip 模式代理，AI 端点域名会解析到 198.18.0.0/15
    # 或 IPv6 ULA 段（默认被 SSRF 防护拒绝）。设置 SSRF_ALLOW_FAKE_IP=1 可在开发环境放行。
    if ($env:SSRF_ALLOW_FAKE_IP -eq "1") {
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Dssrf.allow-fake-ip-ranges=true"
    } else {
        .\mvnw.cmd spring-boot:run
    }
} finally {
    Pop-Location
}
