# Proving Grounds Mod

Forge 1.20.1-47.3.0 伺服器端 + 客戶端模組（BR / TDM / DOM / GK）。

## 下載 jar

- **每次推送的測試版**：GitHub → Actions → 選一次 `Build` 執行 → 頁面最下方 Artifacts → `provinggrounds-<commit>`（zip，裡面是 jar）。
- **正式版**：推送 `v*` 標籤（例如 `v0.1.0`）後，到 GitHub → Releases 下載。
- **本機建置**：`build/libs/provinggrounds-<版本>.jar`。

jar 放進伺服器與客戶端的 `mods/` 資料夾。

## 本機建置

需要已安裝 JDK 17（不必設為預設 Java；`gradle/gradle-daemon-jvm.properties` 會讓 Gradle 自動找 JDK 17）。

```
gradlew.bat build        # 產出 build/libs/*.jar
gradlew.bat runServer    # 測試伺服器（第一次需把 run/eula.txt 改成 eula=true）
gradlew.bat runClient    # 測試客戶端（看 UI / HUD）
```
