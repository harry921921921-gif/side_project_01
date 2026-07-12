# 單機版強化 Prompt 清單（先把本機版做好）

> 目標：在**不部署上線、不引入使用者系統**的前提下，把 FitTracker 的本機版做穩、做完整，並逐步往「大公司標準」靠攏。
> 用法：**一次只貼一個階段**給 AI（Claude Code / Cursor 等），跑得起來、驗證通過、`git commit` 後，再貼下一個。**不要一次全貼**，避免 AI 一次改太多、彼此衝突、難以除錯。
> 建議順序：階段 0 →1 →2 →3 →4 →5 →6 →7（數字越小越優先、風險越低）。

---

## 專案問題總覽（體檢結果）

### 🔴 必須先處理（資安 / 會壞）
- **崩潰日誌 `hs_err_pid*.log`、`replay_pid*.log` 被 commit 進公開 GitHub repo**（含系統路徑等環境資訊，不該公開）。
- 崩潰原因是 **記憶體不足（OOM）**——程式曾因 RAM 不夠而當機數次。
- **資料庫密碼在本機明碼儲存**，且 repo 為公開；密碼雖未進 git 歷史，但保險起見應更換一次。
- **`orElseThrow()` 找不到資料直接噴 HTTP 500 + stack trace**（狀態碼錯、洩漏內部堆疊、無全域例外處理）。

### 🟠 品質 / 大公司標準落差
- **API 直接回傳 JPA Entity**（如 `List<WorkoutSession>`），未使用輸出用 Response DTO，內部結構外洩、欄位不可控。
- **完全沒有輸入驗證**（無 `@Valid` / Bean Validation）。
- **`bodyPart`、`completionStatus` 是自由字串**，打錯字會造成統計漏資料（命名不一致風險）。
- **`ExerciseApiController` 用 `Map<String,String>` 當 request body**，非型別安全，應改 DTO。
- **`HelloController` 三個 `/hello` 是測試殘留**，應刪除。
- **沒有分頁（pagination）**，`findAll()` 全撈，資料量大會慢甚至 OOM。
- **`@OneToMany` 用 `FetchType.EAGER`**，每次載入全部 sets，效能差；大公司用 `LAZY` + fetch join。
- **幾乎沒有 logging**（只有 `show-sql`，無分級 SLF4J log）。
- **測試覆蓋率極低**（只有 `contextLoads()`）。
- **AI 建議規則引擎尚未實作**（Dashboard 只有統計，沒有建議）。

### 🟢 已經做得對的地方（不用改）
- 三層架構 Controller→Service→Repository 分層清楚。
- 用建構子注入（非 `@Autowired` 欄位）——Spring 官方推薦。
- 輸入有用 DTO；寫入方法有 `@Transactional`。
- `@JsonIgnore` 擋住 JPA 序列化迴圈；已關閉 `open-in-view`。

---

## 共用前言（每個階段開頭都先貼這段）

```
你是一位熟悉 Java 17、Spring Boot 3、Spring Data JPA、Thymeleaf、MySQL 的全端工程師。
請基於我現有的 FitTracker 專案「最小改動」地實作以下任務，保持與現有架構相容。

現有架構重點：
- 三層結構：Controller → Service → Repository
- Entity：WorkoutSession、WorkoutSet、BodyWeight、Exercise、BodyPart
- DTO：WorkoutRequest（含內層 ExerciseDto）、BodyWeightRequest
- 網頁路由用 Thymeleaf（如 /workout），API 路由回 JSON（如 /api/workouts）
- ddl-auto=update，欄位由 JPA 自動建立

限制：
- 不要重構整個專案、不要引入使用者系統 / JWT / OAuth2
- 不要引入新前端框架
- 只做「本階段」要求的事，不要順手改其他無關的地方
- 每次改動後要能正常編譯、正常啟動（mvn spring-boot:run）

完成後請說明：改了哪些檔案、實作了什麼、如何驗證可正常啟動。
```

---

## 階段 0：資安清理（最優先，先做）

```
請做以下資安與清理工作，讓專案乾淨、避免敏感資料留在公開 repo：

1. 這些崩潰日誌目前是被 git 追蹤的，請「從 git 移除追蹤並刪除檔案」：
   - fitness-tracker/hs_err_pid*.log
   - 專案根目錄的 hs_err_pid*.log、replay_pid*.log
   做法：對每個檔案執行 `git rm --cached <檔案>` 後再實體刪除，或直接 `git rm <檔案>`。
2. 在 .gitignore 補上（若尚未存在）：
   hs_err_pid*.log
   replay_pid*.log
   *.log
3. 確認 .env 與 application-local.properties 已被 .gitignore 忽略，
   用 `git status` 確認它們不會被追蹤。
4. 請「不要」把我的資料庫密碼寫進任何會被 git 追蹤的檔案。
   （密碼更換我會自己在 MySQL 端手動處理，你不用改密碼值，只要確保設定檔讀環境變數即可。）
5. 完成後列出你 git rm / 刪除 / 修改了哪些檔案，並提醒我最後要 git commit。

注意：這階段只做清理，不要動任何 Java 原始碼。
```

> 做完這階段後，我（使用者）要記得：到 MySQL 手動把 root 密碼改掉一次，並更新本機 `application-local.properties`。

---

## 階段 1：輸入驗證 + 資料一致性

> 解決：使用者亂填不會炸、`bodyPart` 與 `completionStatus` 不會因打錯字漏資料。

```
請為訓練與體重的輸入加入驗證，並把兩個「自由字串」欄位改成受控值：

A. completionStatus 改為 enum
- 新增 enum CompletionStatus { COMPLETE, FAILED, DROPPED, PAIN }
- WorkoutSet.completionStatus 由 String 改為此 enum（用 @Enumerated(EnumType.STRING) 存字串）
- Service、DTO、Controller、Thymeleaf 表單的下拉選單都改用這個 enum 的值
- 舊資料相容：值本來就一致，直接對應即可

B. bodyPart 一致性
- 表單新增訓練時，bodyPart 一律從既有 BodyPart 清單「下拉選取」，不要自由輸入
- 後端 save/update 時驗證傳入的 bodyPart 必須存在於 BodyPart 表，否則回錯誤

C. 加入 Bean Validation
- pom.xml 加入 spring-boot-starter-validation
- WorkoutRequest / BodyWeightRequest 欄位加驗證：
  - workoutDate / recordedDate：@NotNull，不可未來日期（@PastOrPresent）
  - bodyPart：@NotBlank
  - weightKg / actualWeight：@PositiveOrZero（允許 null）
  - sets / reps / actualReps：非負整數
  - rpe：0~10（@DecimalMin/@DecimalMax，允許 null）
- API Controller 的 @RequestBody 前加 @Valid

驗收：
- 送不合法資料（未來日期、rpe=20、空 bodyPart）時 API 回 400 有清楚訊息，不是 500
- 表單的 bodyPart 與完成狀態都是下拉選單
- 現有新增/編輯/刪除仍正常
```

---

## 階段 2：全域錯誤處理

```
目前 Service 用 orElseThrow()，找不到資料會回 500。請加入統一錯誤處理：

1. 新增自訂例外 ResourceNotFoundException（繼承 RuntimeException）
2. Service 中 findById(...).orElseThrow() 改丟 ResourceNotFoundException，附清楚訊息
   （例：「找不到 id=5 的訓練紀錄」）
3. 新增 @RestControllerAdvice 全域例外處理器：
   - ResourceNotFoundException → 404 + JSON { "error": "...", "message": "..." }
   - MethodArgumentNotValidException（驗證失敗）→ 400 + 欄位錯誤清單
   - 其他未預期例外 → 500 + 通用訊息（不要回 stack trace）
4. 新增簡單 error.html，網頁遇錯顯示友善畫面而非白頁

驗收：
- GET /api/workouts/99999 回 404 而非 500
- 送不合法資料回 400 並列出錯誤欄位
- 瀏覽器遇錯看到自訂 error 頁面
```

---

## 階段 3：API 輸出 DTO 化 + 清掉測試殘留

> 讓 API 不再直接吐 JPA Entity，並移除殘留程式。

```
請調整 API 層，讓輸入與輸出都用 DTO，不直接暴露 Entity：

1. 新增輸出用的 Response DTO（例如 WorkoutResponse、BodyWeightResponse、ExerciseResponse），
   只放前端真正需要的欄位；在 Service 或 Controller 做 Entity → DTO 的轉換。
2. 所有 REST API（/api/workouts、/api/body-weights、/api/exercises）的回傳型別，
   改成回 Response DTO（或 DTO 清單），不要再回 WorkoutSession 等 Entity。
3. ExerciseApiController 的 POST 目前用 Map<String,String> 當 body，
   請改成一個明確的 ExerciseRequest DTO（含 name、bodyPart、category），並加 @Valid。
4. 刪除 HelloController（/api/hello、/hello2、/hello3 是測試殘留，沒有用途）。

限制：不要改動網頁 Thymeleaf 頁面的資料流，只調整 REST API 與相關 DTO。

驗收：
- GET /api/workouts 回的 JSON 是 DTO 結構，不含 JPA 內部欄位
- 新增動作的 API 用型別安全的 DTO
- 專案正常啟動、現有頁面不受影響
```

---

## 階段 4：效能（分頁 + 延遲載入）

```
請做兩個效能改善，避免資料變多後變慢或記憶體不足（先前有 OOM 崩潰紀錄）：

1. 分頁：
   - 為列表型 API（至少 /api/workouts、/api/body-weights）加入分頁支援，
     使用 Spring Data 的 Pageable（參數如 ?page=0&size=20，預設 size=20）
   - Repository 對應方法回 Page<...>，Controller 回分頁結果
   - 網頁頁面若一次載入全部，也改成合理上限或分頁（可先只做 API，網頁維持現狀）

2. 延遲載入：
   - WorkoutSession 的 @OneToMany sets 由 FetchType.EAGER 改為 LAZY
   - 需要一次帶出 sets 的查詢，改用 JPQL 的 fetch join 或 @EntityGraph，避免 N+1
   - 確認 Dashboard 統計、workout 頁面仍能正確取得 sets（必要處補 fetch join）

限制：改 LAZY 後要特別測試「首頁 Dashboard」與「訓練頁面」是否還能正常顯示動作明細
（因為 open-in-view 已關閉，LAZY 若沒 fetch join 可能報 LazyInitializationException）。

驗收：
- 列表 API 可用 ?page=&size= 分頁
- 改 LAZY 後所有頁面仍正常、無 LazyInitializationException
- 專案正常啟動
```

---

## 階段 5：導入 Logging

```
請導入結構化的日誌，取代目前只靠 show-sql 的做法：

1. 在 Service 層加入 SLF4J logger（private static final Logger log = LoggerFactory.getLogger(...)）
2. 關鍵操作記錄 INFO（新增/更新/刪除訓練、體重）
3. 例外處理器（@RestControllerAdvice）記錄 WARN/ERROR，包含足夠除錯資訊但不含密碼等敏感值
4. application.properties 設定合理的 log level（例如 root=INFO、fitness_tracker=DEBUG）
5. 可考慮把 spring.jpa.show-sql 關掉，改用 logging.level.org.hibernate.SQL=DEBUG 控制

驗收：
- 啟動與 CRUD 操作時 console 有清楚的分級日誌
- 錯誤發生時 log 有記錄，但不外洩敏感資料
```

---

## 階段 6：AI 建議規則引擎（補齊未完成功能）

> Dashboard 目前只有統計數字，還沒有「建議」。這階段補上規則引擎並顯示在首頁。

```
請實作「規則型」訓練建議引擎（不要用機器學習），並顯示在首頁 Dashboard。

1. 新增 SuggestionService 與結果物件：
   record Suggestion(String level, String exerciseName, String message)
   level 為 INFO / WARN / DANGER（前端決定卡片顏色）

2. 依「每個動作名稱」分組，取最近幾次紀錄套用規則：
   規則 A（減量）：某動作最近連續 2 次 RPE >= 9 且 completionStatus = FAILED
     → WARN，例：「深蹲 連續兩次高強度失敗，建議下次減量 10%」
   規則 B（加重）：某動作最近 3 次都 COMPLETE 且平均 RPE < 8
     → INFO，例：「臥推 連續三次順利完成，可嘗試加 2.5kg」
   規則 C（休息）：最近一次訓練出現 PAIN，或同一次 2 次以上 FAILED
     → DANGER，例：「上次訓練出現疼痛，建議安排休息或降低強度」

3. HomeController 的 home() 把 suggestions 放進 model
4. templates/index.html 加「AI 建議」卡片區塊：
   - 無建議顯示「目前沒有特別建議，保持節奏 👍」
   - 有建議依 level 用 Bootstrap alert-info / alert-warning / alert-danger
   - 沿用現有 Bootstrap 樣式，不引入新框架

驗收：
- 造測試資料能分別觸發 A、B、C
- 首頁看得到建議卡片，樣式一致
- 沒有訓練資料時不報錯，顯示預設訊息
```

---

## 階段 7：單元測試補強

```
請為核心邏輯補單元測試，使用 JUnit 5 + Mockito（Spring Boot Test 已內建）。

1. WorkoutService（用 Mockito mock Repository）：
   - save(...)：正確組出 WorkoutSet 並過濾空的 exerciseName
   - update(...)：清掉舊 sets 再寫入新的
   - computeDashboardStats()：平均 RPE、只計 COMPOUND 訓練量、近 3 次完成率
   - countThisWeek() / findRecentWithinDays() 的日期邊界（剛好 7 天內/外）

2. SuggestionService（若已完成階段 6）：
   - 分別建構觸發規則 A / B / C 的假資料，驗證產生正確 Suggestion
   - 無符合條件時回空清單

3. Controller（@WebMvcTest + MockMvc，mock Service）：
   - GET /api/workouts 回 200 與 JSON
   - GET /api/workouts/{不存在} 回 404
   - POST /api/workouts 帶不合法資料回 400（需先完成階段 1、2）

驗收：
- mvn test 全部通過
- 至少涵蓋 WorkoutService 主要方法與三條建議規則
- 說明覆蓋了哪些邏輯、還有哪些未覆蓋
```

---

## 附註：關於 MySQL（單機版可選）

目前用 MySQL，需要本機先開好 MySQL 才能啟動。若希望「單機版更易一鍵執行、不依賴外部 MySQL」，可另開一個 profile 用 H2 記憶體資料庫做開發/展示——但這會動到設定，屬選配，**不列入上面階段**，等單機都穩了再評估。

---

### 建議節奏
- **先做 0（資安清理）→ 1（驗證＋一致性）→ 2（錯誤處理）**：CP 值最高、風險最低，做完專案就「乾淨又不易壞」。
- 接著 **3（DTO 化）→ 4（效能）→ 5（logging）**：往大公司標準靠攏。
- 最後 **6（AI 建議）→ 7（測試）**：補功能與安全網。
- **每完成一階段就 `git commit` 一次**，出問題方便回退。一次只貼一個階段給 AI。
