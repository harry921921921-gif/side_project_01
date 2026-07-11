# 單機版強化 Prompt 清單（先把本機版做好）

> 目標：在**不部署上線、不引入使用者系統**的前提下，把 FitTracker 的本機版做穩、做完整。
> 用法：**一次貼一個階段**給 AI（如 Claude Code / Cursor），完成、確認能啟動後再貼下一個。不要一次全貼，避免改動過大難以驗證。
> 建議順序：階段 0 → 1 → 2 → 3 → 4（0 最快、風險最低；4 最花時間）。

---

## 共用前言（每個階段開頭都可以先貼這段）

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
- 每次改動後要能正常編譯、正常啟動（mvn spring-boot:run）

完成後請說明：改了哪些檔案、實作了什麼、如何驗證可正常啟動。
```

---

## 階段 0：清理與收尾（最快，先做）

```
請做以下清理，讓專案乾淨、避免敏感資料外流：

1. 刪除專案裡所有 JVM 崩潰日誌與 replay 檔：
   - fitness-tracker/hs_err_pid*.log
   - 專案根目錄的 hs_err_pid*.log、replay_pid*.log
2. 在 .gitignore 補上以下規則（若尚未存在）：
   hs_err_pid*.log
   replay_pid*.log
   *.log
3. 確認 .env 與 application-local.properties 已被 .gitignore 忽略（目前應該已忽略），
   並用 `git status` 確認這兩個含密碼的檔案不會被追蹤。
4. 檢查是否有其他明顯的暫存 / 垃圾檔可清除，但不要刪除任何原始碼、模板或設定。

完成後列出你刪除與修改的檔案。
```

---

## 階段 1：輸入驗證 + 資料一致性（強烈建議優先）

> 解決兩件事：使用者亂填欄位不會炸掉、`bodyPart` 與 `completionStatus` 不會因打錯字造成統計漏資料。

```
請為訓練與體重的輸入加入驗證，並把兩個「自由字串」欄位改成受控值：

A. completionStatus 改為 enum
- 新增 enum CompletionStatus { COMPLETE, FAILED, DROPPED, PAIN }
- WorkoutSet.completionStatus 由 String 改為此 enum（用 @Enumerated(EnumType.STRING) 存字串）
- Service、DTO、Controller、Thymeleaf 表單的下拉選單都改用這個 enum 的值
- 舊資料相容：若資料庫已有舊字串值，請確保能正確對應（值本來就一致，直接轉即可）

B. bodyPart 一致性
- 表單新增訓練時，bodyPart 一律從既有的 BodyPart 清單「下拉選取」，不要讓使用者自由輸入
- 後端 save/update 時驗證傳入的 bodyPart 必須是 BodyPart 表中存在的名稱，否則回錯誤

C. 加入 Bean Validation
- pom.xml 加入 spring-boot-starter-validation
- 在 WorkoutRequest / BodyWeightRequest 的欄位加上驗證註解：
  - workoutDate：@NotNull，且不可為未來日期（可用 @PastOrPresent）
  - bodyPart：@NotBlank
  - weightKg / actualWeight：非負數（@PositiveOrZero，允許 null）
  - sets / reps / actualReps：非負整數
  - rpe：範圍 0~10（@DecimalMin/@DecimalMax，允許 null）
- 在 API Controller 的 @RequestBody 前加 @Valid

驗收：
- 送出不合法資料（如未來日期、rpe=20、空 bodyPart）時，API 回 400 且有清楚訊息，不會 500
- 網頁表單的 bodyPart 與完成狀態都是下拉選單，無法打錯字
- 現有正常流程仍可新增/編輯/刪除訓練
```

---

## 階段 2：全域錯誤處理（讓錯誤不再噴 500 醜頁）

```
目前 Service 用 orElseThrow()，找不到資料會直接回 500。請加入統一的錯誤處理：

1. 新增自訂例外 ResourceNotFoundException（繼承 RuntimeException）
2. Service 中 findById(...).orElseThrow() 改為丟出 ResourceNotFoundException，附帶清楚訊息
   （例如「找不到 id=5 的訓練紀錄」）
3. 新增 @RestControllerAdvice 全域例外處理器，處理：
   - ResourceNotFoundException → HTTP 404 + JSON { "error": "...", "message": "..." }
   - MethodArgumentNotValidException（驗證失敗）→ HTTP 400 + 欄位錯誤清單
   - 其他未預期例外 → HTTP 500 + 通用訊息（不要把 stack trace 回給前端）
4. 網頁路由（Thymeleaf）部分：新增一個簡單的 error.html，
   讓找不到頁面或伺服器錯誤時顯示友善畫面，而不是預設白頁

驗收：
- GET /api/workouts/99999（不存在）回 404 而非 500
- 送不合法資料回 400 且列出哪個欄位錯
- 瀏覽器遇到錯誤時看到自訂的 error 頁面
```

---

## 階段 3：AI 建議規則引擎（完成 AI_FEATURE_PROMPT 未做的部分）

> 現在 Dashboard 只有統計數字（DashboardStats），還沒有「建議」。這階段補上規則引擎並顯示在首頁。

```
請實作一個「規則型」訓練建議引擎（不要用機器學習），並顯示在首頁 Dashboard。

1. 新增 Service（例如 SuggestionService）與一個結果物件 Suggestion：
   record Suggestion(String level, String exerciseName, String message)
   level 可為 INFO / WARN / DANGER（前端用來決定卡片顏色）

2. 依「每個動作名稱」分組，取該動作最近幾次的紀錄，套用以下規則：
   規則 A（減量）：某動作最近連續 2 次 RPE >= 9 且 completionStatus = FAILED
     → level=WARN，message 例：「深蹲 連續兩次高強度失敗，建議下次減量 10%」
   規則 B（加重）：某動作最近 3 次都 COMPLETE 且平均 RPE < 8
     → level=INFO，message 例：「臥推 連續三次順利完成，可嘗試加 2.5kg」
   規則 C（休息）：最近一次訓練中出現 PAIN，或同一次有 2 次以上 FAILED
     → level=DANGER，message 例：「上次訓練出現疼痛，建議安排休息或降低強度」

3. 在 HomeController 的 home() 把 suggestions 清單放進 model
4. 在 templates/index.html 加入「AI 建議」卡片區塊：
   - 若無建議，顯示「目前沒有特別建議，保持節奏 👍」
   - 有建議時依 level 用不同顏色（Bootstrap alert-info / alert-warning / alert-danger）
   - 沿用現有 Bootstrap 樣式，不要引入新框架

驗收：
- 造幾筆測試資料能分別觸發 A、B、C 三種建議
- 首頁能看到建議卡片，樣式與現有頁面一致
- 沒有訓練資料時不報錯，顯示預設訊息
```

---

## 階段 4：單元測試補強（提高覆蓋率）

> 目前只有 contextLoads() 一個測試。這階段補業務邏輯測試，之後改程式才有安全網。

```
請為核心邏輯補單元測試，使用 JUnit 5 + Mockito（Spring Boot Test 已內建）。

1. WorkoutService 測試（用 Mockito mock Repository，不連真資料庫）：
   - save(...)：能正確組出 WorkoutSet 並過濾掉空的 exerciseName
   - update(...)：會清掉舊 sets 再寫入新的
   - computeDashboardStats()：
     - 平均 RPE 計算正確
     - 只計算 COMPOUND 動作的訓練量
     - 近 3 次完成率百分比計算正確
   - countThisWeek() / findRecentWithinDays() 的日期邊界（剛好 7 天內/外）

2. SuggestionService 測試（若已完成階段 3）：
   - 分別建構能觸發規則 A / B / C 的假資料，驗證產生正確的 Suggestion
   - 沒有符合條件時回空清單

3. Controller 層測試（用 @WebMvcTest + MockMvc，mock Service）：
   - GET /api/workouts 回 200 與 JSON
   - GET /api/workouts/{不存在} 回 404
   - POST /api/workouts 帶不合法資料回 400（需先完成階段 1、2）

驗收：
- mvn test 全部通過
- 至少涵蓋 WorkoutService 的主要方法與三條建議規則
- 說明目前大致覆蓋了哪些邏輯、還有哪些未覆蓋
```

---

## 附註：關於 MySQL（單機版可選）

目前資料庫是 MySQL，需要本機先開好 MySQL 服務才能啟動。若你希望「單機版更容易一鍵執行、不依賴外部 MySQL」，可考慮之後另開一個 profile 用 H2 記憶體資料庫做開發/展示用途——但這會動到設定，屬於選配，**不列入上面四個階段**，等單機功能都穩了再評估。

---

### 建議節奏
先做 **階段 0（清理）→ 階段 1（驗證＋一致性）**，這兩步 CP 值最高、風險最低；接著階段 2（錯誤處理）讓體驗變穩；再做階段 3（AI 建議）補齊功能；最後階段 4（測試）收尾。每完成一階段就 `git commit` 一次，方便回退。
