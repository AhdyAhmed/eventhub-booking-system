[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$StartupTimeoutSeconds = 120,
    [int]$BookingTimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-EventHubApi {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST", "PUT", "DELETE")]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [object]$Body,
        [string]$Token
    )

    $headers = @{}
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }

    $request = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
    }

    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $request.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }

    Invoke-RestMethod @request
}

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Actual,
        [Parameter(Mandatory = $true)]
        [object]$Expected,
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if ($Actual -ne $Expected) {
        throw "$Message Expected '$Expected', received '$Actual'."
    }
}

Write-Host "Waiting for EventHub readiness at $BaseUrl ..."
$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/readiness" -Method GET
        if ($health.status -eq "UP") {
            $ready = $true
            break
        }
    }
    catch {
        # The application may still be starting.
    }
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    throw "EventHub did not become ready within $StartupTimeoutSeconds seconds."
}

$runId = [Guid]::NewGuid().ToString("N").Substring(0, 10)
$email = "smoke-$runId@example.com"
$password = "SmokeTest!123"

Write-Host "Registering smoke-test user ..."
$auth = Invoke-EventHubApi -Method POST -Path "/api/v1/auth/register" -Body @{
    fullName = "Smoke Test"
    email = $email
    password = $password
}
if (-not $auth.token) {
    throw "Registration did not return an access token."
}
$token = $auth.token

Write-Host "Creating venue, event, and seat ..."
$venue = Invoke-EventHubApi -Method POST -Path "/api/v1/venues" -Token $token -Body @{
    name = "Smoke Venue $runId"
    address = "1 Integration Test Street"
    city = "Cairo"
    capacity = 100
}

$event = Invoke-EventHubApi -Method POST -Path "/api/v1/events" -Token $token -Body @{
    name = "Smoke Event $runId"
    description = "Automated end-to-end smoke test"
    category = "CONFERENCE"
    eventDate = [DateTime]::UtcNow.AddDays(7).ToString("o")
    venueId = $venue.id
}

$seat = Invoke-EventHubApi -Method POST -Path "/api/v1/events/$($event.id)/seats" -Token $token -Body @{
    section = "A"
    seatNumber = "A-1"
    price = 50.00
}

Write-Host "Creating booking and waiting for Kafka payment processing ..."
$booking = Invoke-EventHubApi -Method POST -Path "/api/v1/bookings" -Token $token -Body @{
    seatIds = @($seat.id)
}

$bookingDeadline = (Get-Date).AddSeconds($BookingTimeoutSeconds)
do {
    $booking = Invoke-EventHubApi -Method GET -Path "/api/v1/bookings/$($booking.id)" -Token $token
    if ($booking.status -eq "CONFIRMED") {
        break
    }
    if ($booking.status -eq "FAILED") {
        throw "Mock payment failed unexpectedly."
    }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $bookingDeadline)

Assert-Equal -Actual $booking.status -Expected "CONFIRMED" -Message "Booking was not confirmed."
$seats = Invoke-EventHubApi -Method GET -Path "/api/v1/events/$($event.id)/seats" -Token $token
$bookedSeat = $seats | Where-Object { $_.id -eq $seat.id }
Assert-Equal -Actual $bookedSeat.status -Expected "BOOKED" -Message "Seat was not marked as booked."

Write-Host "Cancelling booking and verifying seat release ..."
$cancelled = Invoke-EventHubApi -Method POST -Path "/api/v1/bookings/$($booking.id)/cancel" -Token $token
Assert-Equal -Actual $cancelled.status -Expected "CANCELLED" -Message "Booking was not cancelled."
$seats = Invoke-EventHubApi -Method GET -Path "/api/v1/events/$($event.id)/seats" -Token $token
$releasedSeat = $seats | Where-Object { $_.id -eq $seat.id }
Assert-Equal -Actual $releasedSeat.status -Expected "AVAILABLE" -Message "Cancelled seat was not released."

Write-Host "Verifying correlation-ID propagation ..."
$correlationId = "smoke-$runId"
$probe = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Method GET -Headers @{
    "X-Correlation-Id" = $correlationId
} -UseBasicParsing
Assert-Equal -Actual $probe.Headers["X-Correlation-Id"] -Expected $correlationId -Message "Correlation ID was not echoed."

Write-Host "EventHub full-stack smoke test passed."
