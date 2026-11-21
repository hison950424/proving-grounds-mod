# 檔案: release.ps1
# 目的: 每次更新模組後執行：
#   1. build.gradle 的 version 最後一碼加 1（例如 1.0.1 → 1.0.2；加 -NoBump 則不加）
#   2. 打包（gradlew build）→ build\libs\provinggrounds-<版本>.jar
#   3. build\libs 只留這個版本的 jar
#   4. 複製到桌面，並刪掉桌面上舊版本的 provinggrounds-*.jar（桌面永遠只有最新一個）
# 用法: powershell -ExecutionPolicy Bypass -File tools\release.ps1 [-NoBump]
param([switch]$NoBump)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root 'build.gradle'
$enc = New-Object System.Text.UTF8Encoding $false

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
$jar = Join-Path $libs "provinggrounds-$version.jar"
if (-not (Test-Path $jar)) { throw "找不到 $jar" }

# 3. build\libs 只留這個版本
Get-ChildItem $libs -Filter 'provinggrounds-*.jar' | Where-Object { $_.Name -ne "provinggrounds-$version.jar" } | Remove-Item

# 4. 複製到桌面（只留最新一個）
$desk = [Environment]::GetFolderPath('Desktop')
Get-ChildItem $desk -Filter 'provinggrounds-*.jar' | Where-Object { $_.Name -ne "provinggrounds-$version.jar" } | Remove-Item
Copy-Item -Force $jar $desk
Write-Host "已複製到桌面：$(Join-Path $desk ("provinggrounds-$version.jar"))"
