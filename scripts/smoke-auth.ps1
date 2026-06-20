param(
    [string]$BaseUrl = "http://127.0.0.1:5173/api/v1"
)

$ErrorActionPreference = "Stop"

$email = "smoke-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())@fitback.test"
$password = "secret12"

$registerBody = @{
    email = $email
    password = $password
    name = "Smoke User"
} | ConvertTo-Json -Compress

$register = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/register" -ContentType "application/json" -Body $registerBody

$loginBody = @{
    email = $email
    password = $password
} | ConvertTo-Json -Compress

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $loginBody

$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$customers = Invoke-RestMethod -Method Get -Uri "$BaseUrl/customers" -Headers $headers

[pscustomobject]@{
    Email = $email
    Registered = [bool]$register.userId
    AccessToken = [bool]$login.accessToken
    RefreshToken = [bool]$login.refreshToken
    CustomerCount = $customers.content.Count
}
