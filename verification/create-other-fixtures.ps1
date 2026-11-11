$ErrorActionPreference = 'Stop'
$folder = Join-Path $PSScriptRoot 'fixtures'
$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false
try {
    $book = $excel.Workbooks.Add()
    $sheet = $book.Worksheets.Item(1)
    $sheet.Name = '收支记录'
    $sheet.Cells.Item(1,1) = '项目'
    $sheet.Cells.Item(1,2) = '金额'
    $sheet.Cells.Item(2,1) = '办公用品'
    $sheet.Cells.Item(2,2) = 120
    $sheet.Cells.Item(1,26) = '横向滚动验收'
    $second = $book.Worksheets.Add()
    $second.Name = '会议安排'
    $second.Cells.Item(1,1) = '会议室'
    $second.Cells.Item(2,1) = '二号会议室'
    $book.SaveAs((Join-Path $folder 'daily.xlsx'), 51)
    $book.Close($false)
} finally { $excel.Quit() }
$ppt = New-Object -ComObject PowerPoint.Application
try {
    $deck = $ppt.Presentations.Add(0)
    foreach ($number in 1..2) {
        $slide = $deck.Slides.Add($number, 12)
        $box = $slide.Shapes.AddTextbox(1, 60, 100, 600, 120)
        $box.TextFrame.TextRange.Text = "简文档演示 第 $number 页"
        $box.TextFrame.TextRange.Font.Size = 30
    }
    $deck.SaveAs((Join-Path $folder 'meeting.pptx'),24)
    $deck.Close()
} finally { $ppt.Quit() }
Add-Type -AssemblyName System.Drawing
$image = [Drawing.Bitmap]::new(600,400)
$g = [Drawing.Graphics]::FromImage($image)
try {
    $g.Clear([Drawing.Color]::White)
    $g.FillRectangle([Drawing.Brushes]::RoyalBlue,40,40,240,160)
    $g.FillEllipse([Drawing.Brushes]::Orange,320,140,180,180)
    $image.Save((Join-Path $folder 'picture.png'),[Drawing.Imaging.ImageFormat]::Png)
    $image.Save((Join-Path $folder 'picture.jpg'),[Drawing.Imaging.ImageFormat]::Jpeg)
} finally { $g.Dispose(); $image.Dispose() }
