# 檔案: make_backpack_textures.ps1
# 目的: 產生一～三級背包的物品貼圖（計畫書 B3）：同一個 16x16 像素圖案，套三種顏色（灰、藍、紫）。
# 用法: powershell -ExecutionPolicy Bypass -File tools\make_backpack_textures.ps1
Add-Type -AssemblyName System.Drawing
$out = Join-Path (Split-Path -Parent $PSScriptRoot) 'src\main\resources\assets\provinggrounds\textures\item'
New-Item -ItemType Directory -Force $out | Out-Null

# . 透明｜o 外框｜l 亮｜m 中｜d 暗｜b 扣環
$pattern = @(
    '................',
    '.....oooooo.....',
    '....om....mo....',
    '....o......o....',
    '..oooooooooooo..',
    '.omllllllllllmo.',
    '.omlmmmmmmmmlmo.',
    '.omlmmmbbmmmlmo.',
    '.omlmmmbbmmmlmo.',
    '.ommmmmmmmmmmmo.',
    '.omdooooooooodo.',
    '.omdmmmmmmmmmdo.',
    '.omdmmmmmmmmmdo.',
    '.omddddddddddmo.',
    '..oooooooooooo..',
    '................'
)
$palettes = [ordered]@{
    backpack_1 = @{ o = '#2E2E2E'; l = '#C8C8C8'; m = '#9A9A9A'; d = '#6A6A6A' }
    backpack_2 = @{ o = '#15243F'; l = '#7FB2FF'; m = '#3C78D8'; d = '#24508F' }
    backpack_3 = @{ o = '#2A1238'; l = '#D39BFF'; m = '#9C4DCC'; d = '#68308A' }
}
foreach ($name in $palettes.Keys) {
    $pal = $palettes[$name]
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $ch = [string]$pattern[$y][$x]
            if ($ch -eq '.') { continue }
            $hex = if ($ch -eq 'b') { '#E8C547' } else { $pal[$ch] }
            $bmp.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($hex))
        }
    }
    $bmp.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "OK: $name.png"
}
