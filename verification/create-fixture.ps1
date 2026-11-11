$ErrorActionPreference = 'Stop'
$folder = Join-Path $PSScriptRoot 'fixtures'
New-Item -ItemType Directory -Force $folder | Out-Null
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Add()
    $doc.Content.Font.NameFarEast = '宋体'
    $doc.Content.Font.Size = 12
    $doc.Sections.Item(1).Headers.Item(1).Range.Text = '简文档兼容性验收'
    $footer = $doc.Sections.Item(1).Footers.Item(1).Range
    $footer.Fields.Add($footer, 33) | Out-Null
    $selection = $word.Selection
    $selection.TypeText("日常办公通知`r日期：2026年11月10日`r请各位同事按时参加会议。`r")
    $table = $doc.Tables.Add($selection.Range, 2, 2)
    $table.Cell(1,1).Range.Text = '姓名'
    $table.Cell(1,2).Range.Text = '部门'
    $table.Cell(2,1).Range.Text = '张三'
    $table.Cell(2,2).Range.Text = '行政部'
    $selection.EndKey(6) | Out-Null
    $selection.TypeParagraph()
    $png = Join-Path $folder 'sample.png'
    Add-Type -AssemblyName System.Drawing
    $image = [Drawing.Bitmap]::new(120,80)
    $graphics = [Drawing.Graphics]::FromImage($image)
    $graphics.Clear([Drawing.Color]::RoyalBlue)
    $graphics.FillEllipse([Drawing.Brushes]::White,20,10,60,60)
    $image.Save($png,[Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $image.Dispose()
    $picture = $selection.InlineShapes.AddPicture($png)
    $picture.Width = 80
    $picture.Height = 40
    $selection.EndKey(6) | Out-Null
    $selection.InsertBreak(7)
    $selection.TypeText("会议安排`r第二页用于核对分页、页眉和页码。")
    $doc.SaveAs2((Join-Path $folder 'chinese-notice.docx'), 16)
    $doc.ExportAsFixedFormat((Join-Path $folder 'chinese-notice.pdf'), 17)
    Write-Output ('Fixture pages: ' + $doc.ComputeStatistics(2))
    $doc.Close(0)
} finally {
    $word.Quit()
    [Runtime.InteropServices.Marshal]::FinalReleaseComObject($word) | Out-Null
}
