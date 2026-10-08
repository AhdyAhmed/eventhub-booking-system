[CmdletBinding()]
param(
    [switch]$SkipCacheEviction
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$sqlPath = Join-Path $PSScriptRoot "demo-data.sql"

Push-Location $projectRoot
try {
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose configuration is invalid."
    }

    $runningServices = @(docker compose ps --status running --services)
    if ($LASTEXITCODE -ne 0 -or $runningServices -notcontains "postgres") {
        throw "Postgres is not running. Start the stack with: docker compose up -d --build"
    }

    $databaseUser = ((docker compose exec -T postgres printenv POSTGRES_USER) -join "").Trim()
    $databaseName = ((docker compose exec -T postgres printenv POSTGRES_DB) -join "").Trim()
    if (-not $databaseUser -or -not $databaseName) {
        throw "Could not read POSTGRES_USER/POSTGRES_DB from the running container."
    }

    Get-Content -LiteralPath $sqlPath -Raw |
        docker compose exec -T postgres psql --set ON_ERROR_STOP=1 --username $databaseUser --dbname $databaseName
    if ($LASTEXITCODE -ne 0) {
        throw "Demo data import failed."
    }

    if (-not $SkipCacheEviction -and $runningServices -contains "redis") {
        $patterns = @("events::*", "event-search::*", "seat-availability::*")
        foreach ($pattern in $patterns) {
            $keys = @(docker compose exec -T redis redis-cli --raw --scan --pattern $pattern)
            foreach ($key in $keys) {
                if (-not [string]::IsNullOrWhiteSpace($key)) {
                    docker compose exec -T redis redis-cli UNLINK $key | Out-Null
                }
            }
        }
        Write-Host "Cleared EventHub cache entries so the seeded catalog is immediately visible."
    }

    Write-Host "Demo catalog is ready."
    Write-Host "Swagger UI: http://localhost:8080/swagger-ui.html"
    Write-Host "Events API:  http://localhost:8080/api/v1/events"
}
finally {
    Pop-Location
}
