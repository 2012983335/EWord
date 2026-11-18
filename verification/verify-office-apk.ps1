param([Parameter(Mandatory)][string]$Apk)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Apk))
try {
    foreach ($name in @('lib/arm64-v8a/libandroidapp.so', 'lib/arm64-v8a/liblo-native-code.so', 'assets/dist/cool.html', 'assets/unpack/program/sofficerc', 'assets/license.html', 'assets/notice.txt', 'assets/licences/NOTICE-officecompat.txt')) {
        if (!$zip.GetEntry($name)) { throw "Missing offline Office component: $name" }
    }
    $reader = [IO.StreamReader]::new($zip.GetEntry('assets/program/versionrc').Open())
    try { if (!$reader.ReadToEnd().Contains('buildid=31fe13e27b')) { throw 'Unexpected Office source revision' } } finally { $reader.Dispose() }
    $inputStream = $zip.GetEntry('lib/arm64-v8a/libc++_shared.so').Open()
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $actual = [Convert]::ToHexString($sha.ComputeHash($inputStream)) } finally { $inputStream.Dispose(); $sha.Dispose() }
    if ($actual -ne 'C4C2FE5CBCB1FBA0003A31FC7AB29A9BB12DF6CC187EC45A806462540E83D93B') { throw 'Unexpected shared C++ runtime; retest both native engines' }
    'Offline Office runtime, source revision, licences and shared C++ runtime verified.'
} finally { $zip.Dispose() }
