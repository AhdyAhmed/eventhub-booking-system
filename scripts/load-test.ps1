[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [ValidateRange(1, 100)]
    [int]$SeatCount = 20,
    [ValidateRange(2, 50)]
    [int]$ContendersPerSeat = 5,
    [ValidateRange(1, 5000)]
    [int]$ReadRequests = 200,
    [ValidateRange(10, 300)]
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-EventHubApi {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST")]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [object]$Body,
        [string]$Token
    )

    $request = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = @{}
    }
    if ($Token) {
        $request.Headers.Authorization = "Bearer $Token"
    }
    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $request.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    Invoke-RestMethod @request
}

function Invoke-ConcurrentBatch {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.Http.HttpClient]$Client,
        [Parameter(Mandatory = $true)]
        [object[]]$Requests,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $messages = [System.Collections.Generic.List[System.Net.Http.HttpRequestMessage]]::new()
    $tasks = [System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]]::new()
    $timer = [System.Diagnostics.Stopwatch]::StartNew()

    try {
        foreach ($requestSpec in $Requests) {
            $message = [System.Net.Http.HttpRequestMessage]::new(
                [System.Net.Http.HttpMethod]::new($requestSpec.Method),
                "$BaseUrl$($requestSpec.Path)"
            )
            if ($requestSpec.Body) {
                $json = $requestSpec.Body | ConvertTo-Json -Depth 8 -Compress
                $message.Content = [System.Net.Http.StringContent]::new(
                    $json,
                    [System.Text.Encoding]::UTF8,
                    "application/json"
                )
            }
            if ($requestSpec.CorrelationId) {
                [void]$message.Headers.TryAddWithoutValidation("X-Correlation-Id", $requestSpec.CorrelationId)
            }
            $messages.Add($message)
            $tasks.Add($Client.SendAsync($message))
        }

        $taskArray = [System.Threading.Tasks.Task[]]$tasks.ToArray()
        if (-not [System.Threading.Tasks.Task]::WaitAll($taskArray, $TimeoutSeconds * 1000)) {
            throw "$Name did not complete within $TimeoutSeconds seconds."
        }
        $timer.Stop()

        $responses = [System.Collections.Generic.List[object]]::new()
        for ($index = 0; $index -lt $tasks.Count; $index++) {
            $response = $tasks[$index].GetAwaiter().GetResult()
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $responses.Add([PSCustomObject]@{
                Index = $index
                StatusCode = [int]$response.StatusCode
                Body = $body
            })
            $response.Dispose()
        }

        [PSCustomObject]@{
            Name = $Name
            DurationMs = $timer.ElapsedMilliseconds
            RequestsPerSecond = if ($timer.Elapsed.TotalSeconds -gt 0) {
                [Math]::Round($Requests.Count / $timer.Elapsed.TotalSeconds, 2)
            } else { 0 }
            Responses = $responses.ToArray()
        }
    }
    finally {
        foreach ($message in $messages) {
            $message.Dispose()
        }
    }
}

function Get-CacheHitCount {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CacheName,
        [Parameter(Mandatory = $true)]
        [string]$Token
    )

    $metric = Invoke-EventHubApi -Method GET -Path "/actuator/metrics/cache.gets?tag=cache:$CacheName&tag=result:hit" -Token $Token
    $count = $metric.measurements | Where-Object { $_.statistic -eq "COUNT" } | Select-Object -First 1
    if ($null -eq $count) { return 0.0 }
    return [double]$count.value
}

Write-Host "Waiting for EventHub readiness at $BaseUrl ..."
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/readiness" -Method GET
        if ($health.status -eq "UP") { break }
    }
    catch {
        # The Compose stack may still be starting.
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
if ($health.status -ne "UP") {
    throw "EventHub did not become ready within $TimeoutSeconds seconds."
}

$runId = [Guid]::NewGuid().ToString("N").Substring(0, 10)
$password = "LoadTest!123"
$auth = Invoke-EventHubApi -Method POST -Path "/api/v1/auth/register" -Body @{
    fullName = "Load Test"
    email = "load-$runId@example.com"
    password = $password
}
if (-not $auth.token) {
    throw "Registration did not return an access token."
}

$venue = Invoke-EventHubApi -Method POST -Path "/api/v1/venues" -Token $auth.token -Body @{
    name = "Load Venue $runId"
    address = "21 Concurrency Street"
    city = "Cairo"
    capacity = [Math]::Max(100, $SeatCount)
}
$event = Invoke-EventHubApi -Method POST -Path "/api/v1/events" -Token $auth.token -Body @{
    name = "Load Event $runId"
    description = "Day 21 concurrent load test"
    category = "CONFERENCE"
    eventDate = [DateTime]::UtcNow.AddDays(7).ToString("o")
    venueId = $venue.id
}

Write-Host "Creating $SeatCount contested seats ..."
$seatIds = [System.Collections.Generic.List[long]]::new()
for ($seatNumber = 1; $seatNumber -le $SeatCount; $seatNumber++) {
    $seat = Invoke-EventHubApi -Method POST -Path "/api/v1/events/$($event.id)/seats" -Token $auth.token -Body @{
        section = "LOAD"
        seatNumber = "L-$seatNumber"
        price = 50.00
    }
    $seatIds.Add([long]$seat.id)
}

$handler = [System.Net.Http.SocketsHttpHandler]::new()
$handler.MaxConnectionsPerServer = [Math]::Max(100, $SeatCount * $ContendersPerSeat)
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
$client.DefaultRequestHeaders.Authorization =
    [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $auth.token)

try {
    $bookingRequests = [System.Collections.Generic.List[object]]::new()
    foreach ($seatId in $seatIds) {
        for ($attempt = 1; $attempt -le $ContendersPerSeat; $attempt++) {
            $bookingRequests.Add([PSCustomObject]@{
                Method = "POST"
                Path = "/api/v1/bookings"
                Body = @{ seatIds = @($seatId) }
                CorrelationId = "load-$runId-$seatId-$attempt"
                SeatId = $seatId
            })
        }
    }

    # Shuffle so requests for every seat overlap instead of arriving in
    # contiguous per-seat blocks.
    $bookingRequests = @($bookingRequests | Sort-Object { Get-Random })
    Write-Host "Racing $($bookingRequests.Count) booking requests ($ContendersPerSeat per seat) ..."
    $bookingBatch = Invoke-ConcurrentBatch -Client $client -Requests $bookingRequests -Name "booking race"

    $winnersBySeat = @{}
    $conflictsBySeat = @{}
    $bookingIds = [System.Collections.Generic.List[long]]::new()
    for ($index = 0; $index -lt $bookingBatch.Responses.Count; $index++) {
        $seatId = [string]$bookingRequests[$index].SeatId
        $response = $bookingBatch.Responses[$index]
        if ($response.StatusCode -eq 201) {
            $currentWinners = if ($winnersBySeat.ContainsKey($seatId)) { [int]$winnersBySeat[$seatId] } else { 0 }
            $winnersBySeat[$seatId] = $currentWinners + 1
            $bookingIds.Add([long](($response.Body | ConvertFrom-Json).id))
        }
        elseif ($response.StatusCode -eq 409) {
            $currentConflicts = if ($conflictsBySeat.ContainsKey($seatId)) { [int]$conflictsBySeat[$seatId] } else { 0 }
            $conflictsBySeat[$seatId] = $currentConflicts + 1
        }
        else {
            throw "Booking request for seat $seatId returned unexpected HTTP $($response.StatusCode): $($response.Body)"
        }
    }

    foreach ($seatId in $seatIds) {
        if ([int]$winnersBySeat[[string]$seatId] -ne 1) {
            throw "Seat $seatId had $($winnersBySeat[[string]$seatId]) winners; expected exactly one."
        }
        if ([int]$conflictsBySeat[[string]$seatId] -ne ($ContendersPerSeat - 1)) {
            throw "Seat $seatId had $($conflictsBySeat[[string]$seatId]) conflicts; expected $($ContendersPerSeat - 1)."
        }
    }

    Write-Host "Waiting for $($bookingIds.Count) Kafka payment results ..."
    $settlementDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $confirmed = 0
        foreach ($bookingId in $bookingIds) {
            $booking = Invoke-EventHubApi -Method GET -Path "/api/v1/bookings/$bookingId" -Token $auth.token
            if ($booking.status -eq "CONFIRMED") {
                $confirmed++
            }
            elseif ($booking.status -eq "FAILED") {
                throw "Booking $bookingId unexpectedly failed payment."
            }
        }
        if ($confirmed -eq $bookingIds.Count) { break }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $settlementDeadline)
    if ($confirmed -ne $bookingIds.Count) {
        throw "Only $confirmed of $($bookingIds.Count) winning bookings settled before timeout."
    }

    # Warm both Redis-backed keys before measuring the concurrent read batch.
    [void](Invoke-EventHubApi -Method GET -Path "/api/v1/events/$($event.id)")
    [void](Invoke-EventHubApi -Method GET -Path "/api/v1/events/$($event.id)/seats")
    $eventHitsBefore = Get-CacheHitCount -CacheName "events" -Token $auth.token
    $seatHitsBefore = Get-CacheHitCount -CacheName "seat-availability" -Token $auth.token

    $readSpecs = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt $ReadRequests; $index++) {
        $path = if ($index % 2 -eq 0) {
            "/api/v1/events/$($event.id)"
        } else {
            "/api/v1/events/$($event.id)/seats"
        }
        $readSpecs.Add([PSCustomObject]@{ Method = "GET"; Path = $path })
    }

    Write-Host "Sending $ReadRequests concurrent cached reads ..."
    $readBatch = Invoke-ConcurrentBatch -Client $client -Requests $readSpecs.ToArray() -Name "cached reads"
    foreach ($response in $readBatch.Responses) {
        if ($response.StatusCode -ne 200) {
            throw "Cached read returned HTTP $($response.StatusCode): $($response.Body)"
        }
    }

    $eventHitDelta = (Get-CacheHitCount -CacheName "events" -Token $auth.token) - $eventHitsBefore
    $seatHitDelta = (Get-CacheHitCount -CacheName "seat-availability" -Token $auth.token) - $seatHitsBefore
    $expectedEventHits = [Math]::Ceiling($ReadRequests / 2)
    $expectedSeatHits = [Math]::Floor($ReadRequests / 2)
    if ($eventHitDelta -lt $expectedEventHits) {
        throw "Events cache recorded $eventHitDelta hits; expected at least $expectedEventHits."
    }
    if ($seatHitDelta -lt $expectedSeatHits) {
        throw "Seat cache recorded $seatHitDelta hits; expected at least $expectedSeatHits."
    }

    $finalSeats = Invoke-EventHubApi -Method GET -Path "/api/v1/events/$($event.id)/seats"
    if (@($finalSeats).Count -ne $SeatCount) {
        throw "Seat cache returned $(@($finalSeats).Count) seats; expected $SeatCount."
    }
    $notBooked = @($finalSeats | Where-Object { $_.status -ne "BOOKED" })
    if ($notBooked.Count -ne 0) {
        throw "Seat cache returned $($notBooked.Count) stale/non-BOOKED seats after payment settlement."
    }

    Write-Host ""
    Write-Host "Day 21 load test passed."
    Write-Host "Booking race: $($bookingRequests.Count) requests, $SeatCount winners, $($bookingRequests.Count - $SeatCount) conflicts, $($bookingBatch.DurationMs) ms, $($bookingBatch.RequestsPerSecond) req/s"
    Write-Host "Cached reads: $ReadRequests responses, 0 failures, $($readBatch.DurationMs) ms, $($readBatch.RequestsPerSecond) req/s"
    Write-Host "Redis cache hits: events=$eventHitDelta, seat-availability=$seatHitDelta"
    Write-Host "Final state: $SeatCount confirmed bookings and $SeatCount BOOKED seats"
}
finally {
    $client.Dispose()
    $handler.Dispose()
}
