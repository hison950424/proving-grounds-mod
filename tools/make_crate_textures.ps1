# 檔案: make_crate_textures.ps1
# 目的: 產生鐵箱、金箱的木桶造型貼圖（計畫書 A7）。
#   以原版木桶貼圖（barrel_top / barrel_top_open / barrel_side / barrel_bottom）為底，
#   依每個像素的亮度套上鐵色或金色的漸層，保留木桶的箍帶與板縫細節。
# 用法: powershell -ExecutionPolicy Bypass -File tools\make_crate_textures.ps1 <原版木桶貼圖資料夾>
param([Parameter(Mandatory = $true)][string]$SourceDir)

Add-Type -AssemblyName System.Drawing
$out = Join-Path (Split-Path -Parent $PSScriptRoot) 'src\main\resources\assets\provinggrounds\textures\block'
New-Item -ItemType Directory -Force $out | Out-Null

# 漸層：暗 → 中 → 亮（RGB）
$palettes = @{
    iron = @(@(0x2A, 0x2E, 0x34), @(0x6E, 0x75, 0x7E), @(0xB9, 0xBF, 0xC6))
    gold = @(@(0x4A, 0x2A, 0x04), @(0xB0, 0x7A, 0x06), @(0xF0, 0xCC, 0x4A))
}
$parts = @('top', 'top_open', 'side', 'bottom')

function Get-Lerp($a, $b, $t) { [int][Math]::Round($a + ($b - $a) * $t) }

foreach ($metal in $palettes.Keys) {
    $p = $palettes[$metal]
    foreach ($part in $parts) {
        $src = New-Object System.Drawing.Bitmap (Join-Path $SourceDir "barrel_$part.png")
        $w = $src.Width; $h = $src.Height
        # 先找亮度範圍，拉開對比
        $lum = New-Object 'double[]' ($w * $h)
        $min = 1.0; $max = 0.0
        for ($y = 0; $y -lt $h; $y++) { for ($x = 0; $x -lt $w; $x++) {
            $c = $src.GetPixel($x, $y)
            $l = (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
            $lum[$y * $w + $x] = $l
            if ($c.A -gt 0) { if ($l -lt $min) { $min = $l }; if ($l -gt $max) { $max = $l } }
        } }
        $range = [Math]::Max(0.001, $max - $min)
        $dst = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        for ($y = 0; $y -lt $h; $y++) { for ($x = 0; $x -lt $w; $x++) {
            $c = $src.GetPixel($x, $y)
            # 只部分拉開對比（0.12～0.88），避免過亮過暗、雜點太明顯
            $t = 0.12 + 0.76 * ($lum[$y * $w + $x] - $min) / $range
            if ($t -lt 0.5) { $a = $p[0]; $b = $p[1]; $k = $t * 2 } else { $a = $p[1]; $b = $p[2]; $k = ($t - 0.5) * 2 }
            $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, (Get-Lerp $a[0] $b[0] $k), (Get-Lerp $a[1] $b[1] $k), (Get-Lerp $a[2] $b[2] $k)))
        } }
        $src.Dispose()
        $dst.Save((Join-Path $out "crate_${metal}_$part.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $dst.Dispose()
        Write-Host "crate_${metal}_$part.png"
    }
}
