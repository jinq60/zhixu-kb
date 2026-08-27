# =====================================================================
# 知序知识库 桌面版一键打包脚本
# 产物：backend/target/desktop-dist/ZhixuKB/（app-image 目录，内含 ZhixuKB.exe）
#       若安装 WiX Toolset v3.14，可加 -Msi 产出 ZhixuKB-1.0.0.exe 安装包
# 前置：JDK 17+（jpackage）、Node.js、Maven（或 mvnw）
# =====================================================================
param(
    [switch]$Msi,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

# ---------- 1. 定位 JDK 17（jpackage 所在） ----------
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome "bin\jpackage.exe"))) {
    $JavaHome = $null
    foreach ($dir in @("D:\HZC\Java", "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java")) {
        if (-not (Test-Path $dir)) { continue }
        Get-ChildItem $dir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            if ([string]::IsNullOrEmpty($JavaHome) -and $_.Name -match "jdk-?17" `
                    -and (Test-Path (Join-Path $_.FullName "bin\jpackage.exe"))) {
                $JavaHome = $_.FullName
            }
        }
        if ($JavaHome) { break }
    }
    if (-not $JavaHome) {
        Write-Error "未找到含 jpackage 的 JDK 17+，请安装 Eclipse Temurin 17 或用 -JavaHome 指定"
        exit 1
    }
}
$env:JAVA_HOME = $JavaHome
Write-Host "==> JDK: $JavaHome" -ForegroundColor Cyan

# ---------- 2. 构建前端 ----------
Write-Host "==> [1/3] 构建前端 (npm run build) ..." -ForegroundColor Cyan
Push-Location (Join-Path $root "frontend")
try { npm run build; if ($LASTEXITCODE -ne 0) { throw "前端构建失败" } } finally { Pop-Location }

# ---------- 3. 构建后端（含前端静态资源嵌入） ----------
Write-Host "==> [2/3] 构建后端 (mvn package -Pdesktop-package) ..." -ForegroundColor Cyan
Push-Location (Join-Path $root "backend")
try {
    .\mvnw.cmd -q package "-Pdesktop-package" -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "后端构建失败" }
} finally { Pop-Location }
$jar = Join-Path $root "backend\target\zhixu-kb-backend-1.0.0.jar"
if (-not (Test-Path $jar)) { Write-Error "未找到 $jar"; exit 1 }

# ---------- 4. jpackage 打包 ----------
Write-Host "==> [3/3] jpackage 打包 ..." -ForegroundColor Cyan
$inputDir = Join-Path $root "backend\target\desktop-input"
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item $jar $inputDir -Force

$dest = Join-Path $root "backend\target\desktop-dist"
if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }

$type = "app-image"
$extraArgs = @()
if ($Msi) {
    # WiX 查找顺序：系统安装目录 → 仓库便携版（backend/packaging/wix314）
    $wixBin = "C:\Program Files (x86)\WiX Toolset v3.14\bin"
    if (-not (Test-Path (Join-Path $wixBin "candle.exe"))) {
        $portable = Join-Path $root "backend\packaging\wix314"
        if (Test-Path (Join-Path $portable "candle.exe")) {
            $wixBin = $portable
            $env:PATH = "$wixBin;$env:PATH"
        }
    }
    if (-not (Test-Path (Join-Path $wixBin "candle.exe"))) {
        Write-Warning "未检测到 WiX Toolset v3.14（系统安装或 backend/packaging/wix314 均无），退化为 app-image"
    } else {
        Write-Host "==> WiX: $wixBin" -ForegroundColor Cyan
        $type = "exe"
        $extraArgs = @("--win-menu", "--win-shortcut", "--win-dir-chooser")
    }
}

$jpackage = Join-Path $JavaHome "bin\jpackage.exe"
& $jpackage `
    --name "ZhixuKB" `
    --app-version "1.0.0" `
    --vendor "ZhiXu Tech" `
    --description "知序知识库 桌面版" `
    --type $type `
    --input $inputDir `
    --main-jar "zhixu-kb-backend-1.0.0.jar" `
    --icon (Join-Path $root "backend\packaging\icon.ico") `
    --dest $dest `
    --java-options "-Dspring.profiles.active=desktop" `
    --java-options "-Xms256m" `
    --java-options "-Xmx768m" `
    --java-options "-Dssrf.allow-fake-ip-ranges=true" `
    @extraArgs
if ($LASTEXITCODE -ne 0) { Write-Error "jpackage 失败"; exit 1 }

Write-Host ""
Write-Host "✅ 打包完成: $dest" -ForegroundColor Green
if ($type -eq "app-image") {
    Write-Host "   运行方式: $(Join-Path $dest 'ZhixuKB\ZhixuKB.exe')" -ForegroundColor Yellow
    Write-Host "   （想要带开始菜单/卸载器的安装包：安装 WiX Toolset v3.14 后加 -Msi 参数重跑）"
} else {
    Write-Host "   安装包: $(Get-ChildItem $dest -Filter *.exe | Select-Object -First 1 -ExpandProperty FullName)" -ForegroundColor Yellow
}
