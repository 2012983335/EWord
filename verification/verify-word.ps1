$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Read-Package($path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($path)
    try {
        $result = @{}
        foreach ($entry in $zip.Entries) {
            $stream = $entry.Open()
            try {
                $memory = [IO.MemoryStream]::new()
                $stream.CopyTo($memory)
                $result[$entry.FullName] = $memory.ToArray()
            } finally { $stream.Dispose() }
        }
        return ,$result
    } finally { $zip.Dispose() }
}
$before = Read-Package (Join-Path $PSScriptRoot 'fixtures/chinese-notice.docx')
$after = Read-Package (Join-Path $PSScriptRoot 'fixtures/edited-short.docx')
foreach ($part in @('word/document.xml','word/header1.xml','word/footer1.xml')) {
    if (!$after.ContainsKey($part)) { throw "Missing part $part" }
}
[xml]$doc = [Text.Encoding]::UTF8.GetString($after['word/document.xml'])
$ns = [Xml.XmlNamespaceManager]::new($doc.NameTable)
$ns.AddNamespace('w','http://schemas.openxmlformats.org/wordprocessingml/2006/main')
if ($doc.SelectNodes('//w:tbl',$ns).Count -ne 1) { throw 'Table was lost' }
$allText = ($doc.SelectNodes('//w:t',$ns) | ForEach-Object InnerText) -join ''
if (!$allText.Contains('李四') -or !$allText.Contains('2026年11月11日')) { throw 'Edited text missing' }
foreach ($part in $before.Keys | Where-Object { $_ -like 'word/media/*' }) {
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($before[$part]))
    $matches = @($after.Keys | Where-Object { $_ -like 'word/media/*' -and [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($after[$_])) -eq $hash })
    if ($matches.Count -eq 0) { throw "Image bytes were lost: $part" }
}
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $edited = $word.Documents.Open((Join-Path $PSScriptRoot 'fixtures/edited-short.docx'), $false, $true)
    if ($edited.ComputeStatistics(2) -ne 2) { throw 'Word page count changed' }
    if (!$edited.Sections.Item(1).Headers.Item(1).Range.Text.Contains('简文档兼容性验收')) { throw 'Header text lost' }
    if ($edited.Sections.Item(1).Footers.Item(1).Range.Fields.Count -lt 1) { throw 'Page field lost' }
    $edited.ExportAsFixedFormat((Join-Path $PSScriptRoot 'fixtures/edited-short.pdf'),17)
    $edited.Close(0)
    Write-Output 'Word reopened edited DOCX: two pages, table, original image, header, page field, and edited text preserved.'
} finally { $word.Quit() }
