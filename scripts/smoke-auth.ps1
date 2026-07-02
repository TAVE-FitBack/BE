param(
    [string]$BaseUrl = "http://localhost:5173/api/v1"
)

$ErrorActionPreference = "Stop"

$runId = "$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
$email = "smoke-$runId@fitback.test"
$password = "secret12"

$registerBody = @{
    email = $email
    password = $password
    passwordConfirm = $password
    nickname = "smoke-$runId"
    agreeTerms = $true
    agreeMarketing = $false
} | ConvertTo-Json -Compress

$register = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/register" -ContentType "application/json" -Body $registerBody

$loginBody = @{
    email = $email
    password = $password
} | ConvertTo-Json -Compress

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $loginBody

$loginData = if ($login.data) { $login.data } else { $login }
$registerData = if ($register.data) { $register.data } else { $register }

$headers = @{ Authorization = "Bearer $($loginData.accessToken)" }
$customers = Invoke-RestMethod -Method Get -Uri "$BaseUrl/customers" -Headers $headers
$customerData = if ($customers.data) { $customers.data } else { $customers }

[pscustomobject]@{
    Email = $email
    Registered = [bool]$registerData.userId
    AccessToken = [bool]$loginData.accessToken
    RefreshToken = [bool]$loginData.refreshToken
    CustomerCount = $customerData.content.Count
}
