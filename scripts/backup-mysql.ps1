# =====================================================================
# MySQL 全量备份（Docker 部署）
# 用法: powershell -ExecutionPolicy Bypass -File .\scripts\backup-mysql.ps1
# 建议通过 Windows 任务计划每天执行一次：
#   schtasks /Create /TN "zhixu-mysql-backup" /TR "powershell -ExecutionPolicy Bypass -File D:\path\scripts\backup-mysql.ps1" /SC DAILY /ST 03:00
# =====================================================================
param(
    [string]$ContainerName = "zhixu-mysql",
    [string]$Database = "zhixu_kb",
    [string]$KeepDays = 14
)

$ErrorActionPreference = "Stop"

$backupDir = Join-Path $PSScriptRoot "..\backups\mysql"
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

# 读取备份密码（.env 的 DB_PASSWORD）
$password = "root"
$envFile = Join-Path $PSScriptRoot "..\.env"
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        if ($_ -match '^\s*DB_PASSWORD\s*=\s*(.*)\s*$') {
            $password = $Matches[1]
        }
    }
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$target = Join-Path $backupDir "$($Database)-$stamp.sql"

Write-Host "==> 备份 $Database -> $target" -ForegroundColor Cyan
docker exec $ContainerName sh -c "exec mysqldump -uroot -p'$password' --single-transaction --routines --triggers $Database" > $target
if ($LASTEXITCODE -ne 0) {
    Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
    Write-Error "mysqldump 失败，请检查容器 $ContainerName 是否在运行"
}

# 压缩为 zip 并删除原始 sql
$zipTarget = "$target.zip"
Compress-Archive -Path $target -DestinationPath $zipTarget -Force
Remove-Item -LiteralPath $target -Force
$backupFile = Get-Item -LiteralPath $zipTarget
Write-Host "==> 备份完成: $($backupFile.Name) ($([math]::Round($backupFile.Length / 1KB, 1)) KB)" -ForegroundColor Green

# 清理过期备份
$cutoff = (Get-Date).AddDays(-([int]$KeepDays))
Get-ChildItem -LiteralPath $backupDir -Filter "$($Database)-*" | Where-Object { $_.LastWriteTime -lt $cutoff } | Remove-Item -Force
Write-Host "==> 已保留最近 $KeepDays 天备份，目录: $backupDir" -ForegroundColor Yellow
