# FitTracker 改進 Prompt 清單

> 用法：**一次只貼一個任務**給 AI（Claude Code / Cursor 等），跑得起來、驗證通過、`git commit` 後再貼下一個。不要一次全貼，避免 AI 一次改太多、彼此衝突、難以除錯。
>
> 最後更新：2026-11-26。這份清單原本是「單機版強化」的 0–7 階段，大部分已經做完，下面先列進度，再列還沒做的任務。
> 專案怎麼執行請看根目錄的 [README.md](../README.md)。

---

## 原本 0–7 階段的進度

| 階段 | 內容 | 狀態 | 現況 |
|---|---|---|---|
| 0 | 資安清理（崩潰日誌、.gitignore、密碼改讀環境變數） | ✅ 完成 | `hs_err_pid*.log` 已從目前檔案移除並加入 `.gitignore`；但舊的 git 歷史裡還找得到（見任務 E） |
| 1 | 輸入驗證＋資料一致性 | ✅ 完成 | `CompletionStatus` enum、Bean Validation（`@Valid`） |
| 2 | 全域錯誤處理 | ✅ 完成 | `ResourceNotFoundException`、`GlobalExceptionHandler`、`error.html` |
| 3 | API 輸出 DTO 化、刪除測試殘留 | ✅ 完成 | `WorkoutResponse` 等 Response DTO、`ExerciseRequest`；`HelloController` 已刪 |
| 4 | 分頁＋延遲載入 | ✅ 完成 | `/api/workouts`、`/api/body-weights` 支援 `?page=&size=`；`sets` 改 `LAZY` + `@EntityGraph` |
| 5 | Logging | 🟡 大致完成 | 主要 Service 與例外處理器已用 SLF4J；`application-prod.properties` 有分級設定 |
| 6 | AI 建議規則引擎 | ❌ 尚未開始 | Dashboard 還沒有「建議」卡片（見任務 D） |
| 7 | 單元測試補強 | 🟡 大致完成 | 共 131 個測試；其中 9 個需要真的 MySQL 才會過（見任務 A） |

另外，原本清單寫的「不引入使用者系統、不部署上線」已經不成立：專案現在有帳號系統（註冊、登入、記住我、忘記密碼、ADMIN 角色）、訓練計劃引擎，以及 Docker 部署設定。

---

## 共用前言（每個任務開頭都先貼這段）

```
你是一位熟悉 Java 17、Spring Boot 3.5、Spring Data JPA、Spring Security、Thymeleaf、MySQL 8 的全端工程師。
請基於我現有的 FitTracker 專案「最小改動」地實作以下任務，保持與現有架構相容。

現有架構重點：
- 程式碼在 fitness-tracker/，套件名 fitness_tracker
- 三層結構：Controller → Service → Repository
- Entity：User、WorkoutSession、WorkoutSet、BodyWeight、Exercise、BodyPart、TrainingPlan、LiftPr、EmailToken
- 每筆訓練/體重資料都屬於某個 User，Controller 透過 CurrentUserService 取得目前登入者
- 網頁路由用 Thymeleaf（/、/plan、/workout、/body-weight、/manage），API 路由回 JSON（/api/...、/plan/api/...）
- API 輸入輸出都用 DTO（dto/ 資料夾），錯誤由 GlobalExceptionHandler 統一處理
- 資料庫帳密與 REMEMBER_ME_KEY 一律從環境變數讀取，不要寫進會被 git 追蹤的檔案
- ddl-auto=update，欄位由 JPA 自動建立（還沒有 Flyway）

限制：
- 不要重構整個專案、不要引入新前端框架
- 只做「本任務」要求的事，不要順手改其他無關的地方
- 每次改動後要能正常編譯、正常啟動（mvnw spring-boot:run），既有測試要繼續通過

完成後請說明：改了哪些檔案、實作了什麼、如何驗證。
```

---

## 建議順序

1. **任務 A**（測試不依賴 MySQL）→ **任務 B**（GitHub Actions 自動測試）：先有安全網，之後每次改動都有人幫你檢查。
2. **任務 C**（部署小修正）：風險低、改動小。
3. **任務 D**（AI 建議規則引擎）：補上唯一還沒做的功能。
4. **任務 E、F**：選做，等前面穩了再評估。

---

## 任務 A：測試不依賴本機 MySQL

> 目前 `FitnessTrackerApplicationTests`、`WorkoutPlanServiceIntegrationTest` 用 `@SpringBootTest` 連真正的 MySQL，沒開 MySQL 就失敗（9 個測試）。

```
請讓 mvnw test 在「沒有安裝 MySQL」的電腦上也能全部通過：

1. 在 pom.xml 加入 H2（scope=test），新增 src/test/resources/application-test.properties：
   - 用 H2 記憶體資料庫，並開 MySQL 相容模式（MODE=MySQL）
   - 給 DB_USERNAME、DB_PASSWORD、REMEMBER_ME_KEY 測試用的假值
2. 兩個 @SpringBootTest 測試類別加上 @ActiveProfiles("test")
3. 注意 schema.sql（persistent_logins 表）與 User.email 欄位的 columnDefinition
   （VARCHAR(255) COLLATE utf8mb4_bin）在 H2 是否能執行；不能的話在 test profile 下做最小調整，
   不要改到正式環境的行為
4. 如果 H2 相容性問題太多，改用 Testcontainers（需要 Docker），並說明取捨

驗收：
- 關掉 MySQL、不設任何環境變數，mvnw test 全部通過
- 正式啟動（mvnw spring-boot:run）行為不變
```

---

## 任務 B：GitHub Actions 自動跑測試

```
請新增 .github/workflows/ci.yml，讓每次 push 與 pull request 都自動跑測試：

1. 使用 ubuntu-latest、actions/setup-java（Temurin 17，開啟 Maven 快取）
2. working-directory 設為 fitness-tracker
3. 執行 sh ./mvnw -B test（mvnw 在 git 裡沒有執行權限，用 sh 執行）
4. 測試失敗時讓 workflow 失敗

前提：任務 A 已完成（測試不需要 MySQL）。

驗收：
- 開一個 PR 後，GitHub 上看得到綠色勾勾
```

---

## 任務 C：部署小修正

```
請做以下幾個小修正，每一項都要最小改動：

1. mvnw 在 git 裡沒有執行權限：執行 git update-index --chmod=+x fitness-tracker/mvnw
2. docker-compose.yml 目前 SPRING_PROFILES_ACTIVE=local，請改成可以用 .env 切換：
   SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}，並在 .env.example 補上說明
3. Dockerfile 改成「多階段建置」：第一階段用 maven + JDK 17 編譯打包，第二階段只放 JRE 和 jar，
   這樣使用者不用先在本機跑 mvnw package 才能 docker compose up
4. 同步更新根目錄 README.md 的 Docker 章節

驗收：
- 全新 clone 下來，只要建立 .env 再 docker compose up --build 就能跑起來
- 本機 mvnw spring-boot:run 不受影響
```

---

## 任務 D：AI 建議規則引擎（原階段 6）

> Dashboard 目前只有統計數字，還沒有「建議」。這個任務補上規則引擎並顯示在首頁。
> 注意：課表頁的新手模式已經有「卡關就退回約 90%」的漸進邏輯（WorkoutService），規則要跟它一致、不要互相矛盾。

```
請實作「規則型」訓練建議引擎（不要用機器學習），並顯示在首頁 Dashboard。

1. 新增 SuggestionService 與結果物件：
   record Suggestion(String level, String exerciseName, String message)
   level 為 INFO / WARN / DANGER（前端決定卡片顏色）

2. 只看「目前登入者」的資料，依每個動作名稱分組，取最近幾次紀錄套用規則：
   規則 A（減量）：某動作最近連續 2 次 RPE >= 9 且 completionStatus = FAILED
     → WARN，例：「深蹲 連續兩次高強度失敗，建議下次減量 10%」
   規則 B（加重）：某動作最近 3 次都 COMPLETE 且平均 RPE < 8
     → INFO，例：「臥推 連續三次順利完成，可嘗試加 2.5kg」
   規則 C（休息）：最近一次訓練出現 PAIN，或同一次 2 次以上 FAILED
     → DANGER，例：「上次訓練出現疼痛，建議安排休息或降低強度」

3. HomeController 的 home() 把 suggestions 放進 model
4. templates/index.html 加「訓練建議」卡片區塊：
   - 無建議顯示「目前沒有特別建議，保持節奏 👍」
   - 有建議依 level 用 Bootstrap alert-info / alert-warning / alert-danger
   - 沿用現有 Bootstrap 樣式，不引入新框架
5. 為 SuggestionService 寫單元測試（Mockito），分別觸發 A、B、C 與「沒有建議」

驗收：
- 測試能分別觸發 A、B、C
- 首頁看得到建議卡片，樣式一致
- 沒有訓練資料時不報錯，顯示預設訊息
```

---

## 任務 E（選做）：清掉 git 歷史裡的崩潰日誌

> 崩潰日誌（`hs_err_pid*.log`、`replay_pid*.log`）已經從目前的檔案移除，但在舊的 commit 裡還看得到，裡面有電腦路徑等環境資訊。
> 要徹底清除必須「改寫 git 歷史」（例如用 git filter-repo），會影響所有 clone 過這個 repo 的人，而且**無法復原**。
> 如果 repo 是公開的、你在意這些資訊，再做；做之前先把整個 repo 備份一份。這一步建議自己手動做，不要交給 AI 自動執行。

---

## 任務 F（選做）：導入 Flyway 資料庫遷移

```
目前 ddl-auto=update，只會「加」欄位/表格，改欄位型別或改名不會報錯，會悄悄留下不一致的結構。
請導入 Flyway：

1. pom.xml 加入 flyway-core 與 flyway-mysql
2. 以目前的資料表結構產生 V1__baseline.sql（含 schema.sql 的 persistent_logins）
3. 既有資料庫用 baseline-on-migrate=true 銜接，不要刪任何資料
4. ddl-auto 改為 validate
5. 在 README 補一段「改資料表結構要新增 migration 檔」的說明

驗收：
- 全新資料庫可以從 0 建好所有表
- 既有資料庫啟動後資料完整、不報錯
```

---

### 節奏提醒
- **每完成一個任務就 `git commit` 一次**，出問題方便回退。
- 做完一個任務，回來把上面的進度表更新一下。
