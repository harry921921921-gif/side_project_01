# Prompt: 為現有 FitTracker 專案加入 AI 建議欄位與訓練統計

你是一位熟悉 Java 17、Spring Boot 3、Spring Data JPA、Thymeleaf、MySQL 的全端工程師。請基於我目前這個專案直接實作下一階段功能，並且保持與現有架構相容。

## 你的任務

請在現有專案中加入以下功能：

1. 訓練紀錄欄位擴充
2. 簡易訓練統計
3. 基礎 AI 建議規則
4. 前端顯示與使用者互動

## 專案背景

目前專案已經有：
- Spring Boot MVC + Thymeleaf
- MySQL
- JPA Entity：WorkoutSession、WorkoutSet、BodyWeight、Exercise
- 訓練頁面與體重頁面
- Dashboard 首頁

請不要重構整個專案，請在現有結構上逐步擴充。

## 你要實作的內容

### 1. 訓練紀錄欄位擴充

請為 WorkoutSet 新增以下欄位：
- rpe: Double（例如 6.0 ~ 10.0）
- completionStatus: String（例如 COMPLETE / FAILED / DROPPED / PAIN）
- actualReps: Integer
- actualWeight: Double
- notes: String

請確保：
- 這些欄位能透過 JPA 自動建立到資料庫
- 目前的新增/編輯/更新流程能支援這些欄位
- API / 表單也要能接收這些欄位

### 2. 訓練統計功能

請在首頁 Dashboard 或訓練頁面加入統計：
- 本週訓練次數
- 最近 7 天訓練量（可用 weight × reps × sets 計算）
- 平均 RPE
- 最近一次訓練內容摘要
- 最近 3 次訓練的完成率或趨勢

請使用目前已有的 service / controller / template 結構來實作，不要引入新框架。

### 3. 基礎 AI 建議規則

請實作一個簡單規則引擎，根據最近訓練歷史提供建議：

規則 A：
- 如果某個動作連續 2 次高 RPE（例如 >= 9）且完成狀態為 FAILED，建議「減量」

規則 B：
- 如果某個動作最近 3 次都成功完成，且平均 RPE 不高，建議「微幅增加重量」

規則 C：
- 如果某次訓練有 PAIN 或多次 FAILED，建議「休息或降低強度」

請把建議結果以簡單文字顯示在首頁，例如：
- 「建議本週減少 10% 強度」
- 「連續三次完成，建議增加 2.5 kg」

這一步請先用規則邏輯，不要實作複雜機器學習。

### 4. 前端顯示

請在現有 Thymeleaf 頁面中加入一個簡潔的顯示區塊：
- AI 建議卡片
- 統計卡片
- 最近訓練摘要

可以放在首頁 Dashboard 或訓練頁面，選一個即可。

## 技術限制

請遵守以下限制：
- 不要引入完整使用者系統
- 不要引入 JWT / OAuth2
- 不要重寫整個專案
- 保持目前 MVC / Thymeleaf / JPA 的結構
- 優先採用最小改動、最穩定的實作方式

## 建議修改檔案

你可以優先修改以下檔案：
- src/main/java/fitness_tracker/entity/WorkoutSet.java
- src/main/java/fitness_tracker/entity/WorkoutSession.java
- src/main/java/fitness_tracker/service/WorkoutService.java
- src/main/java/fitness_tracker/controller/WorkoutController.java
- src/main/java/fitness_tracker/controller/WorkoutApiController.java
- src/main/java/fitness_tracker/dto/WorkoutRequest.java
- src/main/resources/templates/workout/index.html
- src/main/resources/templates/index.html
- src/main/resources/application.properties（如需調整時）

## 輸入/輸出格式

請讓前端與 API 都能支援這樣的資料格式：

```json
{
  "workoutDate": "2026-07-10",
  "bodyPart": "腿",
  "note": "今天狀態不錯",
  "exercises": [
    {
      "exerciseName": "深蹲",
      "weightKg": 80,
      "sets": 3,
      "reps": 8,
      "rpe": 8.5,
      "completionStatus": "COMPLETE",
      "actualReps": 8,
      "actualWeight": 80,
      "notes": "完成"
    }
  ]
}
```

## 驗收標準

完成後請確認：
- 可以新增訓練時同時存入 RPE / 狀態 / 實際次數與重量
- 可以在首頁看到簡單統計
- 可以看到 AI 建議訊息
- 目前的訓練頁面仍可正常運作
- 專案可以正常啟動且不出現編譯錯誤

## 輸出格式

請以實作完成的方式回覆，不要只給建議。請包含：
- 你修改了哪些檔案
- 你實作了哪些功能
- 你如何驗證專案可正常啟動
- 如果有未完成項目，也要明確說明
