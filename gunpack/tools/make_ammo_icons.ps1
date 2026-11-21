Add-Type -AssemblyName System.Drawing
$src="C:\mcdatapack\1.20.1 Proving Grounds_Server Side_V3.0.0\tacz\tacz_default_gun\assets\tacz\textures\ammo\slot"
$dst="C:\mcdatapack\proving-grounds-mod\gunpack\provinggrounds_ammo\assets\provinggrounds\textures\ammo\slot"
$cats=@(
 @{id='light';rep='9mm';col='#5BD03F';diag=$false},
 @{id='medium';rep='556x45';col='#F5BF15';diag=$false},
 @{id='heavy';rep='308';col='#18C8C8';diag=$false},
 @{id='shotgun';rep='12g';col='#4A62FF';diag=$false},
 @{id='heavy_weapon';rep='rpg_rocket';col='#B050F0';diag=$true})
$B0=16; $B1=22
foreach($c in $cats){
  $o=New-Object System.Drawing.Bitmap "$src\$($c.rep).png"
  $W=$o.Width; $H=$o.Height
  $px=New-Object 'int[]' ($W*$H)
  for($y=0;$y -lt $H;$y++){ for($x=0;$x -lt $W;$x++){ $px[$y*$W+$x]=$o.GetPixel($x,$y).ToArgb() } }
  $o.Dispose()
  # 橫幅以外出現過的顏色 = 子彈本身的顏色
  $own=@{}
  for($y=0;$y -lt $H;$y++){
    if($y -ge $B0 -and $y -le $B1){ continue }
    for($x=0;$x -lt $W;$x++){ $i=$y*$W+$x; $own[$px[$i]]=1 }
  }
  $out=New-Object 'int[]' ($W*$H)
  for($y=0;$y -lt $H;$y++){ for($x=0;$x -lt $W;$x++){
    $v=$px[$y*$W+$x]
    if($y -ge $B0 -and $y -le $B1 -and ($v -band 0xFF000000) -ne 0 -and (-not $own.ContainsKey($v) -or (-not $c.diag -and ($px[15*$W+$x] -band 0xFF000000) -ne 0 -and ($px[23*$W+$x] -band 0xFF000000) -ne 0))){
      # 橫幅像素：用上下方的子彈補回去（RPG 是斜的，沿斜線補）
      if($c.diag){ $sx=$x-(24-$y); $sy=24 } elseif($y -le 19){ $sx=$x; $sy=15 } else { $sx=$x; $sy=23 }
      if($sx -ge 0 -and $sx -lt $W){ $v=$px[$sy*$W+$sx] } else { $v=0 }
    }
    $out[$y*$W+$x]=$v
  } }
  # 類別顏色：沿著輪廓外圍描一圈
  $cc=[System.Drawing.ColorTranslator]::FromHtml($c.col)
  $edge=[System.Drawing.Color]::FromArgb(255,$cc.R,$cc.G,$cc.B).ToArgb()
  $bmp=New-Object System.Drawing.Bitmap $W,$H,([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  for($y=0;$y -lt $H;$y++){ for($x=0;$x -lt $W;$x++){
    $v=$out[$y*$W+$x]
    if(($v -band 0xFF000000) -eq 0){
      $near=$false
      if($x -gt 0 -and ($out[$y*$W+$x-1] -band 0xFF000000) -ne 0){ $near=$true }
      if($x -lt $W-1 -and ($out[$y*$W+$x+1] -band 0xFF000000) -ne 0){ $near=$true }
      if($y -gt 0 -and ($out[($y-1)*$W+$x] -band 0xFF000000) -ne 0){ $near=$true }
      if($y -lt $H-1 -and ($out[($y+1)*$W+$x] -band 0xFF000000) -ne 0){ $near=$true }
      if($near){ $v=$edge }
    }
    $bmp.SetPixel($x,$y,[System.Drawing.Color]::FromArgb($v))
  } }
  $bmp.Save("$dst\$($c.id).png",[System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}
# 預覽（放大 8 倍，灰底模擬物品欄）
$s=8; $bmp=New-Object System.Drawing.Bitmap (32*$s*5+40),(32*$s)
$g=[System.Drawing.Graphics]::FromImage($bmp); $g.Clear([System.Drawing.Color]::FromArgb(139,139,139))
$g.InterpolationMode='NearestNeighbor'; $g.PixelOffsetMode='Half'
$k=0; foreach($c in $cats){ $img=[System.Drawing.Image]::FromFile("$dst\$($c.id).png"); $g.DrawImage($img,$k*(32*$s+8),0,32*$s,32*$s); $img.Dispose(); $k++ }
$bmp.Save((Join-Path $PSScriptRoot 'ammo_icons_v2.png')); $g.Dispose(); $bmp.Dispose()
"done"
