param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$Serial)
$ErrorActionPreference = 'Stop'
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk.' }
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$aapt = Join-Path $AndroidSdk 'build-tools/36.1.0/aapt2.exe'
$app = (Resolve-Path "$PSScriptRoot/../upstream/android/app/build/outputs/apk/debug/app-debug.apk").Path
$test = (Resolve-Path "$PSScriptRoot/../upstream/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk").Path
$devices = @(& $adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_.Line -split '\s+')[0] })
if (!$Serial) {
    if ($devices.Count -ne 1) { throw 'Connect one authorised device or specify -Serial.' }
    $Serial = $devices[0]
}
if ($Serial -notin $devices) { throw 'Selected device is not connected and authorised.' }
$appId = 'cn.jiandoc.app.debug'
$testId = "$appId.test"
if (!((& $aapt dump badging $app | Select-Object -First 1) -match "name='$([regex]::Escape($appId))'")) { throw 'Refusing to install a non-debug package.' }
if (!((& $aapt dump badging $test | Select-Object -First 1) -match "name='$([regex]::Escape($testId))'")) { throw 'Unexpected test package.' }
$hadApp = [bool](& $adb -s $Serial shell pm path $appId)
$hadTest = [bool](& $adb -s $Serial shell pm path $testId)
$installedApp = $false; $installedTest = $false
try {
    & $adb -s $Serial install -r -t $app
    if ($LASTEXITCODE -ne 0) { throw 'Debug package installation failed.' }
    $installedApp = $true
    & $adb -s $Serial install -r -t $test
    if ($LASTEXITCODE -ne 0) { throw 'Test package installation failed.' }
    $installedTest = $true
    $result = & $adb -s $Serial shell am instrument -w "$testId/com.chethan616.clearpdf.checks.SignatureRasterChecks"
    $result
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'SIGNATURE_CHECKS_OK') { throw 'Signature device checks failed.' }
} finally {
    # Remove only temporary packages installed by this run; preserve pre-existing debug data.
    if ($installedTest -and !$hadTest) { & $adb -s $Serial uninstall $testId | Out-Null }
    if ($installedApp -and !$hadApp) { & $adb -s $Serial uninstall $appId | Out-Null }
}
