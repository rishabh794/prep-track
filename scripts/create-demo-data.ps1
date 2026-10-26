param(
    [Parameter(Mandatory = $true)][string]$Email,
    [System.Security.SecureString]$Password
)

$ErrorActionPreference = 'Stop'
if (-not $Password) { $Password = Read-Host 'New demo account password (at least 8 characters)' -AsSecureString }
$demoCredential = [System.Management.Automation.PSCredential]::new($Email, $Password)
$previousDemoEmail = $env:DEMO_EMAIL
$previousDemoPassword = $env:DEMO_PASSWORD
try {
    $env:DEMO_EMAIL = $Email
    $env:DEMO_PASSWORD = $demoCredential.GetNetworkCredential().Password
    Push-Location (Join-Path $PSScriptRoot '..\backend')
    try {
        & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo' '-Dspring-boot.run.arguments=--preptrack.demo.exit=true --server.port=0'
        if ($LASTEXITCODE -ne 0) { throw 'Demo generation failed; review the application output above.' }
    } finally { Pop-Location }
} finally {
    $env:DEMO_EMAIL = $previousDemoEmail
    $env:DEMO_PASSWORD = $previousDemoPassword
}
