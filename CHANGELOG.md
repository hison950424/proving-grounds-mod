# 更新紀錄

版本號規則與舊編號對照見 [docs/VERSIONS.md](docs/VERSIONS.md)。最新的在最上面。

## 0.1.0（2026-11-22）

重新編號為測試版。內容等同舊編號 1.0.5，另加：

- 版本號改為單一來源（`gradle.properties` 的 `mod_version`），遊戲內主選單右下角與 `/pg status` 顯示版本。
- `/pg status` 顯示 BR 走路、疾跑速度與縮圈圈邊速度上限。
- 縮圈圈邊速度上限預設改為疾跑速度的 80%（`max_speed_factor` 0.8，約 3.4 格/秒）。
- `tools/release.ps1` 改為完整發佈流程：檢查、更新版本號與更新紀錄、建置、舊 jar 移到 `mods-backup`、提交、標籤、推送、GitHub Release。
- CI 不再在標籤上建立 Release（改由本機發佈腳本負責，避免重複）。

為什麼是 0.1.0：計畫書指定目前這批功能為 0.1.0 起點。

## 0.0.0 ～ 0.0.4（舊編號 1.0.0 ～ 1.0.4）

更早的測試版，對照見 docs/VERSIONS.md。
