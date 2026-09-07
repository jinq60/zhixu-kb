<#
.SYNOPSIS
  Clean up Docker disk space after the failed Docling PDF-service build.

.DESCRIPTION
  Make sure Docker Desktop is running before executing this script.
  It will:
  1. Stop and remove all zhixu-kb project containers (data volumes are kept)
  2. Remove the oversized zhixu-pdf-service image
  3. Prune dangling images, build cache and unused networks
  4. Optionally remove all unused images (requires rebuilding backend/frontend next time)

  Project data is stored in named volumes: mysql-data, neo4j-data, redis-data, backend-uploads.
  These are NOT removed by this script. If you want to wipe them too, run 'docker volume prune' manually.
#>

[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [switch]$Aggressive
)

$ErrorActionPreference = "Stop"

function Test-DockerReady {
    try {
        docker info >$null 2>&1
        return $?
    } catch {
        return $false
    }
}

if (-not (Test-DockerReady)) {
    Write-Host "Docker daemon is not responding. Please restart Docker Desktop and run this script again." -ForegroundColor Red
    exit 1
}

Write-Host "Docker daemon is ready. Starting cleanup..." -ForegroundColor Green

Write-Host ""
Write-Host "[1/4] Stopping and removing zhixu-kb containers..." -ForegroundColor Cyan
$containers = docker compose ps -q 2>$null
if ($containers) {
    docker compose down --remove-orphans
} else {
    Write-Host "No running zhixu-kb containers found."
}

Write-Host ""
Write-Host "[2/4] Removing obsolete pdf-service images..." -ForegroundColor Cyan
docker rmi -f ghcr.io/xberg-io/xberg:1.0.14 2>$null
docker rmi -f zhixu-pdf-service:latest 2>$null

Write-Host ""
Write-Host "[3/4] Pruning dangling images, build cache and unused networks..." -ForegroundColor Cyan
docker image prune -f
docker builder prune -f
docker network prune -f

if ($Aggressive) {
    Write-Host ""
    Write-Host "[4/4] Aggressive mode: removing all unused images..." -ForegroundColor Yellow
    docker image prune -a -f
} else {
    Write-Host ""
    Write-Host "[4/4] Non-aggressive mode: keeping base images for backend/frontend/mysql/redis/neo4j." -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Cleanup finished. Current Docker disk usage:" -ForegroundColor Green
docker system df

Write-Host ""
Write-Host "If C drive is still tight, move the Docker disk image to D drive:" -ForegroundColor Green
Write-Host "  1. Quit Docker Desktop" -ForegroundColor Green
Write-Host "  2. Open Docker Desktop -> Settings -> Resources -> Advanced" -ForegroundColor Green
Write-Host "  3. Change 'Disk image location' to D:\Docker" -ForegroundColor Green
Write-Host "  4. Click Apply & Restart (migration takes time and requires free space on D:)" -ForegroundColor Green
