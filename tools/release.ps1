# 檔案: release.ps1
# 目的: 一鍵發佈（計畫書 F2）。每次更新做完跑一次：
#   1. 檢查：工作目錄乾淨（gradle.properties、CHANGELOG.md 除外）、不在 main、gh 已登入（-SkipGitHub 時不檢查）、標籤不存在
#   2. 決定版本號：-Level big（第二碼 +1、第三碼歸 0）／small（第三碼 +1）／none（維持目前版本），寫回 gradle.properties；
#      -NotesFile 的內容加進 CHANGELOG.md（若該版本的段落已經存在就不重複加）
#   3. 建置（gradlew build），失敗就停止
#   4. 替換本機模組：tools\release.local.json 列出的每個 mods 資料夾，舊的 provinggrounds-*.jar 移到同層的 mods-backup
#      （不刪除、不自動清理），再放入新 jar。檔案被鎖住（伺服器／遊戲執行中）就停止，請關閉後重跑
#   5. 提交 gradle.properties 與 CHANGELOG.md、建立標籤 v<版本>、推送分支與標籤
#   6. gh release create（測試版標成 pre-release，附上 jar 與這次的更新說明）；-SkipGitHub 時略過
#   7. 回報版本號、jar 路徑、替換的資料夾、Release 網址
# 安全：不用 --force、不改寫歷史、標籤或 Release 已存在就停止；腳本中沒有任何密碼或權杖。
# 用法: powershell -ExecutionPolicy Bypass -File tools\release.ps1 -Level small -NotesFile <說明.md> [-SkipGitHub]
param(
    [ValidateSet('big', 'small', 'none')][string]$Level = 'small',
    [string]$NotesFile,
    [switch]$SkipGitHub
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$enc = New-Object System.Text.UTF8Encoding $false
function Stop-Release([string]$msg) { Write-Host "停止：$msg" -ForegroundColor Red; exit 1 }
function Invoke-Git { & git -C $root @args; if ($LASTEXITCODE -ne 0) { Stop-Release "git $($args -join ' ') 失敗" } }

# ── 1. 檢查 ────────────────────────────────────────────────────────────────
$dirty = & git -C $root status --porcelain | Where-Object { $_ -notmatch '(gradle\.properties|CHANGELOG\.md)$' }
if ($dirty) { Stop-Release "工作目錄有未提交的變更：`n$($dirty -join "`n")" }
$branch = (& git -C $root rev-parse --abbrev-ref HEAD).Trim()
if ($branch -eq 'main') { Stop-Release '目前在 main 分支，請在 feature 分支上發佈' }
if (-not $SkipGitHub) {
    # 剛安裝的程式在已開啟的視窗裡可能還不在 PATH，找不到時改用預設安裝位置
    $script:gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
    if (-not $script:gh -and (Test-Path 'C:\Program Files\GitHub CLI\gh.exe')) { $script:gh = 'C:\Program Files\GitHub CLI\gh.exe' }
    if (-not $script:gh) { Stop-Release '找不到 gh（GitHub CLI）。安裝並登入後再跑，或加 -SkipGitHub 只做本機與推送' }
    & $script:gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { Stop-Release 'gh 尚未登入（gh auth login）' }
}
# Java 在 Windows 上不會鎖住 jar，執行中也換得掉，所以要主動檢查伺服器／Prism 客戶端是否正在執行
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -match 'forge/1\.20\.1-[\d.]+/win_args\.txt|Proving Grounds_Client_Mod' }
if ($running) { Stop-Release '偵測到 Minecraft 伺服器或 Prism 客戶端正在執行，請全部關閉後再發佈' }
$localCfg = Join-Path $PSScriptRoot 'release.local.json'
if (-not (Test-Path $localCfg)) { Stop-Release "缺少 $localCfg（列出要替換模組的 mods 資料夾）" }
$modsDirs = @((Get-Content $localCfg -Raw | ConvertFrom-Json).modsDirs)

# ── 2. 版本號 ──────────────────────────────────────────────────────────────
$propsPath = Join-Path $root 'gradle.properties'
$props = [System.IO.File]::ReadAllText($propsPath)
$m = [regex]::Match($props, '(?m)^mod_version=(\d+)\.(\d+)\.(\d+)\s*$')
if (-not $m.Success) { Stop-Release 'gradle.properties 找不到 mod_version=x.y.z' }
$a = [int]$m.Groups[1].Value; $b = [int]$m.Groups[2].Value; $c = [int]$m.Groups[3].Value
switch ($Level) { 'big' { $b++; $c = 0 } 'small' { $c++ } 'none' { } }
$version = "$a.$b.$c"
$tag = "v$version"
if (& git -C $root tag --list $tag) { Stop-Release "標籤 $tag 已存在" }
if (& git -C $root ls-remote --tags origin "refs/tags/$tag") { Stop-Release "遠端已有標籤 $tag" }
$props = $props.Substring(0, $m.Index) + "mod_version=$version" + [Environment]::NewLine + $props.Substring($m.Index + $m.Length)
[System.IO.File]::WriteAllText($propsPath, $props, $enc)
Write-Host "版本：$version（$Level）"

$changelog = Join-Path $root 'CHANGELOG.md'
$notes = if ($NotesFile) { [System.IO.File]::ReadAllText((Resolve-Path $NotesFile), [System.Text.Encoding]::UTF8).Trim() } else { '' }
$log = [System.IO.File]::ReadAllText($changelog, [System.Text.Encoding]::UTF8)
if ($log -notmatch "(?m)^## $([regex]::Escape($version))[（( ]") {
    if (-not $notes) { Stop-Release "CHANGELOG.md 沒有 $version 的段落，請用 -NotesFile 提供更新說明" }
    $section = "## $version（$(Get-Date -Format 'yyyy-MM-dd')）`n`n$notes`n`n"
    $i = $log.IndexOf("`n## ")
    $log = if ($i -ge 0) { $log.Substring(0, $i + 1) + $section + $log.Substring($i + 1) } else { $log.TrimEnd() + "`n`n" + $section }
    [System.IO.File]::WriteAllText($changelog, $log, $enc)
}
# 這一版的說明（給 Release 用）：CHANGELOG 中這個版本的段落
$sec = [regex]::Match($log, "(?ms)^## $([regex]::Escape($version))[^\n]*\n(.*?)(?=^## |\z)")
$releaseNotes = $sec.Groups[1].Value.Trim()

# ── 3. 建置 ────────────────────────────────────────────────────────────────
Push-Location $root
try { & .\gradlew.bat build --console=plain -q; if ($LASTEXITCODE -ne 0) { Stop-Release "建置失敗（exit $LASTEXITCODE）" } } finally { Pop-Location }
$libs = Join-Path $root 'build\libs'
$name = "provinggrounds-$version.jar"
$jar = Join-Path $libs $name
if (-not (Test-Path $jar)) { Stop-Release "找不到 $jar" }
Get-ChildItem $libs -Filter 'provinggrounds-*.jar' | Where-Object { $_.Name -ne $name } | Remove-Item

# ── 4. 替換本機模組（舊的移到 mods-backup） ─────────────────────────────────
$replaced = @()
foreach ($dir in $modsDirs) {
    $dir = [Environment]::ExpandEnvironmentVariables($dir)
    if (-not (Test-Path $dir)) { Write-Host "略過（資料夾不存在）：$dir" -ForegroundColor Yellow; continue }
    $backup = Join-Path (Split-Path -Parent $dir) 'mods-backup'
    New-Item -ItemType Directory -Force $backup | Out-Null
    try {
        foreach ($old in Get-ChildItem $dir -Filter 'provinggrounds-*.jar') {
            $dest = Join-Path $backup $old.Name
            if (Test-Path $dest) { $dest = Join-Path $backup ("{0}_{1}{2}" -f $old.BaseName, (Get-Date -Format 'yyyyMMddHHmmss'), $old.Extension) }
            Move-Item $old.FullName $dest -ErrorAction Stop
        }
        Copy-Item -Force $jar (Join-Path $dir $name) -ErrorAction Stop
        $replaced += $dir
    } catch {
        Stop-Release "無法替換 $dir（伺服器或遊戲可能正在執行），請關閉後用 -Level none 重跑"
    }
}

# ── 5. 提交、標籤、推送 ────────────────────────────────────────────────────
Invoke-Git add gradle.properties CHANGELOG.md
$msgFile = Join-Path $env:TEMP "pg_release_$version.txt"
[System.IO.File]::WriteAllText($msgFile, "發佈：$tag`n`n$releaseNotes`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`n", $enc)
if (& git -C $root diff --cached --name-only) { Invoke-Git commit -q -F $msgFile }
Invoke-Git tag -a $tag -m "Proving Grounds $version"
Invoke-Git push -q origin $branch
Invoke-Git push -q origin $tag

# ── 6. GitHub Release ─────────────────────────────────────────────────────
$url = '（略過：-SkipGitHub）'
if (-not $SkipGitHub) {
    $notesTmp = Join-Path $env:TEMP "pg_notes_$version.md"
    [System.IO.File]::WriteAllText($notesTmp, $releaseNotes, $enc)
    $url = (& $script:gh release create $tag $jar --repo hison950424/proving-grounds-mod --prerelease --title $version --notes-file $notesTmp --verify-tag) | Select-Object -Last 1
    if ($LASTEXITCODE -ne 0) { Stop-Release "gh release create 失敗（標籤 $tag 已推送，可修正後手動建立 Release）" }
}

# ── 7. 回報 ────────────────────────────────────────────────────────────────
Write-Host "完成：$tag" -ForegroundColor Green
Write-Host "jar：$jar"
foreach ($d in $replaced) { Write-Host "已替換：$d（舊版在 mods-backup）" }
Write-Host "Release：$url"
