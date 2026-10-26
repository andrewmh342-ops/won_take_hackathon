param([switch]$Claude, [switch]$Fake)
$ErrorActionPreference = 'Stop'
if ($Claude -and $Fake) { throw 'Choose either -Claude or -Fake.' }
$repoDir = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoDir 'backend'
$javaConfig = Join-Path $backendDir '.java-home'
$gradleConfig = Join-Path $backendDir '.gradle-user-home'
if (Test-Path -LiteralPath $javaConfig) { $env:JAVA_HOME = (Get-Content -Raw -LiteralPath $javaConfig).Trim() }
if (Test-Path -LiteralPath $gradleConfig) { $env:GRADLE_USER_HOME = (Get-Content -Raw -LiteralPath $gradleConfig).Trim() }
if ($Claude) {
    $env:AI_PROVIDER = 'claude'
    if (-not $env:ANTHROPIC_API_KEY) {
        $secret = Read-Host 'Claude API key (hidden input)' -AsSecureString
        $secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
        try { $env:ANTHROPIC_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr); $secret.Dispose() }
    }
} elseif ($Fake) { $env:AI_PROVIDER = 'fake' }
Push-Location $backendDir
try {
    & .\gradlew.bat bootRun --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error. Check Java 21 and the startup message.' }
} finally { Pop-Location }
