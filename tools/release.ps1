# 檔案: release.ps1
# 目的: 每次更新模組後執行：
#   1. build.gradle 的 version 最後一碼加 1（例如 1.0.3 → 1.0.4；加 -NoBump 則不加）
#   2. 打包（gradlew build）→ build\libs\provinggrounds-<版本>.jar，build\libs 只留這個版本
#   3. 直接替換伺服器與 Prism 客戶端 mods 資料夾裡的模組：先刪舊的 provinggrounds-*.jar，再放新的
#      （兩個版本同時存在 Forge 會開不起來）
#   伺服器或遊戲正在執行時 jar 被鎖住換不了，會顯示提示；關閉後用 -NoBump 再跑一次即可。
# 用法: powershell -ExecutionPolicy Bypass -File tools\release.ps1 [-NoBump]
param([switch]$NoBump)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root 'build.gradle'
$enc = New-Object System.Text.UTF8Encoding $false

# 要替換模組的資料夾（伺服器、Prism 客戶端）
$targets = @(
    'C:\mcdatapack\1.20.1 Proving Grounds_Server_Mod\mods',
    (Join-Path $env:APPDATA 'PrismLauncher\instances\1.20.1 Proving Grounds_Client_Mod\minecraft\mods')
)

# 1. 版本號加 1
$text = [System.IO.File]::ReadAllText($gradle)
$m = [regex]::Match($text, "(?m)^version\s*=\s*'(\d+)\.(\d+)\.(\d+)'")
if (-not $m.Success) { throw "build.gradle 找不到 version = 'x.y.z'" }
$version = "$($m.Groups[1].Value).$($m.Groups[2].Value).$($m.Groups[3].Value)"
if (-not $NoBump) {
    $version = "$($m.Groups[1].Value).$($m.Groups[2].Value).$([int]$m.Groups[3].Value + 1)"
    $text = $text.Substring(0, $m.Index) + "version = '$version'" + $text.Substring($m.Index + $m.Length)
    [System.IO.File]::WriteAllText($gradle, $text, $enc)
}
Write-Host "版本：$version"

# 2. 打包
Push-Location $root
try {
    & .\gradlew.bat build --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "打包失敗（exit $LASTEXITCODE）" }
} finally { Pop-Location }

$libs = Join-Path $root 'build\libs'
$name = "provinggrounds-$version.jar"
$jar = Join-Path $libs $name
if (-not (Test-Path $jar)) { throw "找不到 $jar" }
Get-ChildItem $libs -Filter 'provinggrounds-*.jar' | Where-Object { $_.Name -ne $name } | Remove-Item

# 3. 替換伺服器與客戶端的模組
$failed = $false
foreach ($dir in $targets) {
    if (-not (Test-Path $dir)) {
        Write-Host "略過（資料夾不存在）：$dir" -ForegroundColor Yellow
        continue
    }
    try {
        Get-ChildItem $dir -Filter 'provinggrounds-*.jar' | Where-Object { $_.Name -ne $name } | Remove-Item -ErrorAction Stop
        Copy-Item -Force $jar (Join-Path $dir $name) -ErrorAction Stop
        Write-Host "已替換：$dir\$name" -ForegroundColor Green
    } catch {
        $failed = $true
        Write-Host "替換失敗（伺服器或遊戲可能正在執行）：$dir" -ForegroundColor Red
    }
}
if ($failed) { Write-Host "請關閉伺服器／遊戲後執行：powershell -ExecutionPolicy Bypass -File tools\release.ps1 -NoBump" -ForegroundColor Red }
