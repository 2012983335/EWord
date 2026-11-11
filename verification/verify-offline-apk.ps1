param([string]$Apk = "$PSScriptRoot/../dist/EWord-offline-arm64.apk", [string]$AndroidSdk = 'E:/jiandoc-toolchain/android')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$package = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk))
try {
    $registry = Get-Content "$PSScriptRoot/offline-translation-models.lock.json" -Raw | ConvertFrom-Json
    $count = 0
    foreach ($pair in $registry) {
        foreach ($model in $pair.files) {
            $path = "assets/translation/$($pair.from)-$($pair.to)/$($model.name)"
            $entry = $package.GetEntry($path)
            if (!$entry -or $entry.Length -ne $model.size) { throw "Missing or wrong-size model: $path" }
            $inputStream = $entry.Open()
            $sha = [Security.Cryptography.SHA256]::Create()
            try { $actual = [Convert]::ToHexString($sha.ComputeHash($inputStream)).ToLowerInvariant() }
            finally { $inputStream.Dispose(); $sha.Dispose() }
            if ($actual -ne $model.sha256) { throw "Wrong model hash: $path" }
            $count++
        }
    }
    if ($count -ne 7) { throw "Expected 7 model files, got $count" }
    foreach ($required in @('lib/arm64-v8a/libfoxlet.so', 'assets/licences/NOTICE-foxlet.txt', 'assets/licences/MPL-translation-models.txt', 'assets/licences/foxlet/SOURCE.txt', 'assets/licences/foxlet/THIRD_PARTY_NOTICES.txt')) {
        if (!$package.GetEntry($required)) { throw "Missing packaged component: $required" }
    }
    if (@($package.Entries | Where-Object { $_.FullName -match '^lib/(?!arm64-v8a/)' }).Count) { throw 'Unexpected non-ARM64 native library' }
    "Packaged model checks passed: $count files; both translation directions; licence notices present."
} finally { $package.Dispose() }
$buildTools = Join-Path $AndroidSdk 'build-tools/36.1.0'
$permissions = & "$buildTools/aapt2.exe" dump permissions $Apk
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK permissions' }
if ($permissions -match 'android.permission.INTERNET') { throw 'Unexpected INTERNET permission' }
'No INTERNET permission in final APK.'
& "$buildTools/apksigner.bat" verify --verbose --print-certs $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
& "$buildTools/zipalign.exe" -c -P 16 4 $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK zip alignment verification failed' }
Get-FileHash $Apk -Algorithm SHA256
