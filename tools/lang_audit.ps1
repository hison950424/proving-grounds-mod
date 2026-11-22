# 檔案: lang_audit.ps1
# 目的: 盤點其他模組缺少的繁體中文翻譯（計畫書 A10）。
#   讀 mods 資料夾裡每個 jar 的 assets/<命名空間>/lang/en_us.json 與 zh_tw.json，
#   再加上本模組附的翻譯（src/main/resources/assets/<命名空間>/lang/zh_tw.json），列出每個命名空間還缺幾個鍵。
#   加上 -ShowKeys 會列出缺少的鍵（每個命名空間最多 20 個）。
# 用法: powershell -ExecutionPolicy Bypass -File tools\lang_audit.ps1 -ModsDir "C:\伺服器\mods" [-ShowKeys]
param(
    [Parameter(Mandatory = $true)][string]$ModsDir,
    [switch]$ShowKeys
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = Split-Path -Parent $PSScriptRoot
$ours = Join-Path $repo 'src\main\resources\assets'

function Read-LangKeys([string]$json) {
    $keys = @{}
    foreach ($m in [regex]::Matches($json, '"((?:[^"\\]|\\.)+)"\s*:')) { $keys[$m.Groups[1].Value] = $true }
    return $keys
}

function Read-ZipText($zip, [string]$path) {
    $entry = $zip.GetEntry($path)
    if ($null -eq $entry) { return $null }
    $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

$rows = @()
foreach ($jar in Get-ChildItem -Path $ModsDir -Filter '*.jar') {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $namespaces = $zip.Entries | Where-Object { $_.FullName -match '^assets/([^/]+)/lang/en_us\.json$' } |
            ForEach-Object { $Matches[1] } | Sort-Object -Unique
        foreach ($ns in $namespaces) {
            $en = Read-LangKeys (Read-ZipText $zip "assets/$ns/lang/en_us.json")
            $tw = @{}
            $twText = Read-ZipText $zip "assets/$ns/lang/zh_tw.json"
            if ($twText) { $tw = Read-LangKeys $twText }
            $ourFile = Join-Path $ours "$ns\lang\zh_tw.json"
            if (Test-Path $ourFile) {
                foreach ($k in (Read-LangKeys ([System.IO.File]::ReadAllText($ourFile, [System.Text.Encoding]::UTF8))).Keys) { $tw[$k] = $true }
            }
            $missing = @($en.Keys | Where-Object { -not $tw.ContainsKey($_) } | Sort-Object)
            $rows += [pscustomobject]@{ Jar = $jar.Name; Namespace = $ns; Total = $en.Count; Missing = $missing.Count; Keys = $missing }
        }
    } finally { $zip.Dispose() }
}

$rows | Sort-Object Missing -Descending | Format-Table Jar, Namespace, Total, Missing -AutoSize | Out-String -Width 200 | Write-Host
if ($ShowKeys) {
    foreach ($r in $rows | Where-Object { $_.Missing -gt 0 }) {
        Write-Host "== $($r.Namespace)（缺 $($r.Missing) 個）"
        $r.Keys | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" }
    }
}
