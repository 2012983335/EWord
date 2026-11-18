param([string]$Apk)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/..").Path
if (!$Apk) {
    $Apk = Join-Path $root '.tools/collabora/collabora-arm64.apk'
    if (!(Test-Path -LiteralPath $Apk)) {
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Apk)) | Out-Null
        Invoke-WebRequest 'https://www.collaboraoffice.com/downloads/Collabora-Office-Android-Snapshot/collabora-office-mobile-26.04.7.0-979-snapshot-arm64-v8a-2026-11-10.apk' -OutFile $Apk
    }
}
if ((Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash -ne '903B78D7614C289AACAED3D5555A3F3825DDA2A4FC6034E112EBA5B2F2217A52') { throw 'Office runtime checksum mismatch' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$targetRoot = [IO.Path]::GetFullPath((Join-Path $root 'upstream/android/officecompat/runtime'))
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Apk))
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.StartsWith('assets/dexopt/')) { continue }
        $relative = $null
        if ($entry.FullName.StartsWith('assets/')) { $relative = $entry.FullName }
        elseif ($entry.FullName.StartsWith('lib/arm64-v8a/')) { $relative = 'jniLibs/' + $entry.FullName.Substring(4) }
        if (!$relative -or $relative.EndsWith('/')) { continue }
        $target = [IO.Path]::GetFullPath((Join-Path $targetRoot $relative))
        if (!$target.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe archive path' }
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    }
} finally { $zip.Dispose() }
'Pinned offline Office runtime prepared.'
