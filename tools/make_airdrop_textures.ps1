# 檔案: make_airdrop_textures.ps1
# 目的: 產生空投箱的方塊貼圖（原創的軍用補給箱風格，16x16）：
#   crate_airdrop_side（側面：深色鋼框、橄欖綠箱體、中間一條橘紅警示條紋）
#   crate_airdrop_top（頂面：鋼框、降落傘吊帶十字、中間白色補給標記圓）
#   crate_airdrop_bottom（底面：鋼框、深色底板）
# 用法: powershell -ExecutionPolicy Bypass -File tools\make_airdrop_textures.ps1
Add-Type -AssemblyName System.Drawing
$out = Join-Path (Split-Path -Parent $PSScriptRoot) 'src\main\resources\assets\provinggrounds\textures\block'
New-Item -ItemType Directory -Force $out | Out-Null

$pal = @{
    'F' = '#2B2F33'  # 鋼框
    'H' = '#454B51'  # 鋼框亮面
    'G' = '#4E5B33'  # 橄欖綠
    'L' = '#5E6C3E'  # 橄欖綠亮面
    'D' = '#3C4727'  # 橄欖綠暗面
    'R' = '#D9502A'  # 橘紅警示條
    'Y' = '#F2C230'  # 黃色警示條
    'S' = '#1F1F1F'  # 吊帶
    'W' = '#E8E8E0'  # 白色標記
    'B' = '#24282B'  # 底板
    'P' = '#8C9296'  # 鉚釘
}

$textures = [ordered]@{
    crate_airdrop_side = @(
        'FFFFFFFFFFFFFFFF',
        'FPHHHHHHHHHHHHPF',
        'FHLLLLLLLLLLLLDF',
        'FHLGGGGGGGGGGGDF',
        'FHLGGGGGGGGGGGDF',
        'FHLGGGGGGGGGGGDF',
        'FRRYYRRYYRRYYRRF',
        'FRYYRRYYRRYYRRYF',
        'FYYRRYYRRYYRRYYF',
        'FHLGGGGGGGGGGGDF',
        'FHLGGGGGGGGGGGDF',
        'FHLGGGGGGGGGGGDF',
        'FHDDDDDDDDDDDDDF',
        'FHHHHHHHHHHHHHHF',
        'FPHHHHHHHHHHHHPF',
        'FFFFFFFFFFFFFFFF')
    crate_airdrop_top = @(
        'FFFFFFFSSFFFFFFF',
        'FPHHHHHSSHHHHHPF',
        'FHLLLLGSSLLLLDDF',
        'FHLGGGGSSGGGGGDF',
        'FHLGGGWSSWGGGGDF',
        'FHLGGWWSSWWGGGDF',
        'FHLGWWGSSGWWGGDF',
        'SSSSSSSSSSSSSSSS',
        'SSSSSSSSSSSSSSSS',
        'FHLGWWGSSGWWGGDF',
        'FHLGGWWSSWWGGGDF',
        'FHLGGGWSSWGGGGDF',
        'FHLGGGGSSGGGGGDF',
        'FHDDDDDSSDDDDDDF',
        'FPHHHHHSSHHHHHPF',
        'FFFFFFFSSFFFFFFF')
    crate_airdrop_bottom = @(
        'FFFFFFFFFFFFFFFF',
        'FPHHHHHHHHHHHHPF',
        'FHBBBBBBBBBBBBHF',
        'FHBBBBBBBBBBBBHF',
        'FHBBHHBBBBHHBBHF',
        'FHBBHHBBBBHHBBHF',
        'FHBBBBBBBBBBBBHF',
        'FHBBBBBBBBBBBBHF',
        'FHBBBBBBBBBBBBHF',
        'FHBBBBBBBBBBBBHF',
        'FHBBHHBBBBHHBBHF',
        'FHBBHHBBBBHHBBHF',
        'FHBBBBBBBBBBBBHF',
        'FHBBBBBBBBBBBBHF',
        'FPHHHHHHHHHHHHPF',
        'FFFFFFFFFFFFFFFF')
}
foreach ($name in $textures.Keys) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $rows = $textures[$name]
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $ch = [string]$rows[$y][$x]
            $bmp.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($pal[$ch]))
        }
    }
    $bmp.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "OK: $name.png"
}
