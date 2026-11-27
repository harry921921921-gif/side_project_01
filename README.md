# FitTracker 健身紀錄網站

FitTracker 是一個用 Spring Boot 寫的個人健身紀錄網站：記錄每次訓練、追蹤體重與體態，並依照「週期化訓練」自動排出每天的課表與建議重量。

> 程式碼都在 [`fitness-tracker/`](fitness-tracker/) 資料夾。根目錄的 [`index.html`](index.html) 是整個系統的功能全覽圖，可以直接用瀏覽器打開看。

---

## 目錄

- [功能](#功能)
- [使用技術](#使用技術)
- [專案結構](#專案結構)
- [方法一：在本機執行](#方法一在本機執行)
- [方法二：用 Docker 執行](#方法二用-docker-執行)
- [執行測試](#執行測試)
- [常見問題](#常見問題)

---

## 功能

| 頁面 | 網址 | 做什麼 |
|---|---|---|
| 📊 Dashboard（儀表板） | `/` | 最新體重、本週訓練次數、訓練統計、最近訓練紀錄 |
| 🗓️ 訓練計劃 | `/plan` | 選擇程度（新手／老手）與一週練幾天，系統自動排出今天的課表、本週完整課表、建議重量與組數次數，還有訓練中的計時流程 |
| 🏋️ 訓練紀錄 | `/workout` | 新增／編輯／刪除每次訓練，每個動作記錄重量、組數、次數、RPE（自覺強度）與完成狀態 |
| ⚖️ 體重紀錄 | `/body-weight` | 記錄體重、骨骼肌、體脂率，並畫成趨勢圖 |
| ⚙️ 管理清單 | `/manage` | 管理訓練部位與全站共用的動作庫（**只有管理員 ADMIN 能進**） |
| 🔐 帳號 | `/register`、`/login`、`/forgot` | 註冊、登入、「記住我」、忘記密碼、（可選）Email 驗證 |

訓練計劃背後的邏輯：

- **20 週一輪的週期**：適應期（第 1–6 週）→ 肌肥大期（第 7–14 週）→ 最大力量期（第 15–20 週），跑完自動回到第 1 週。
- **主項重量用 %1RM（一次最大反覆重量的百分比）計算**：1RM 用 Epley 公式估算，重量進位到 2.5 kg。
- **新手模式會自動漸進加重**：上次完成就加重；連續好幾次沒完成，就先退回約 90% 重新累積。
- **配件動作每週輪替**：同一種訓練日在不同週、A/B 日排出的配件不一樣，主項則保持不變。

另外也有一組 REST API（應用程式介面，回傳 JSON 資料），例如 `/api/workouts`、`/api/body-weights`、`/api/exercises`，列表支援 `?page=0&size=20` 分頁。所有 API 都需要先登入。

---

## 使用技術

| 類別 | 技術 |
|---|---|
| 語言 | Java 17 |
| 後端框架 | Spring Boot 3.5（Spring Web、Spring Data JPA、Spring Security、Validation、Mail） |
| 網頁畫面 | Thymeleaf（伺服器端樣板）＋ Bootstrap |
| 資料庫 | MySQL 8 |
| 建置工具 | Maven（專案附 Maven Wrapper `mvnw`，不用另外安裝 Maven） |
| 測試 | JUnit 5、Mockito、Spring Security Test |
| 部署 | Docker、Docker Compose |

> 💡 **比喻**：把這個網站想成一家餐廳。**Controller**（控制器）是服務生，負責接客人的點單（網頁請求）；**Service**（服務層）是廚師，真正處理商業邏輯（算課表、算重量）；**Repository**（資料存取層）是倉庫管理員，負責到 **MySQL**（倉庫）拿食材或存東西。這種分工叫「三層架構」，每層只做自己的事，改其中一層比較不會影響到別層。

---

## 專案結構

```
side_project_01/
├── index.html                    ← 功能全覽圖（用瀏覽器打開）
└── fitness-tracker/
    ├── pom.xml                   ← Maven 設定：用了哪些套件
    ├── mvnw / mvnw.cmd           ← Maven Wrapper（Mac/Linux 用 mvnw，Windows 用 mvnw.cmd）
    ├── Dockerfile                ← 把程式包成 Docker image
    ├── docker-compose.yml        ← 一次啟動「網站 + MySQL」兩個容器
    ├── .env.example              ← Docker 用的機密設定範本
    ├── scripts/                  ← 一次性的資料遷移 SQL、課表輪替演算法模擬
    └── src/
        ├── main/java/fitness_tracker/
        │   ├── controller/       ← 服務生：接網頁與 API 請求
        │   ├── service/          ← 廚師：商業邏輯（課表、重量計算、統計）
        │   ├── repository/       ← 倉庫管理員：存取資料庫
        │   ├── entity/           ← 資料表長什麼樣子
        │   ├── dto/              ← API 輸入/輸出的資料格式
        │   ├── config/           ← 安全性（登入、權限）設定
        │   ├── exception/        ← 統一的錯誤處理
        │   └── init/             ← 啟動時自動建立預設部位與動作
        ├── main/resources/
        │   ├── templates/        ← Thymeleaf 網頁樣板
        │   ├── static/           ← CSS、JavaScript
        │   └── application*.properties ← 設定檔
        └── test/                 ← 自動化測試
```

---

## 方法一：在本機執行

適合開發、改程式時用。

### 1. 先安裝

- **JDK 17**（Java 開發工具包），例如 [Eclipse Temurin 17](https://adoptium.net/)
- **MySQL 8**

### 2. 建立資料庫

登入 MySQL 後執行（資料表會在網站啟動時自動建立，這裡只需要建一個空的資料庫）：

```sql
CREATE DATABASE fitness_tracker CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
```

### 3. 設定機密值

網站需要三個設定值，**不要寫進會被 git 追蹤的檔案**：

| 名稱 | 說明 |
|---|---|
| `DB_USERNAME` | MySQL 帳號，例如 `root` |
| `DB_PASSWORD` | MySQL 密碼 |
| `REMEMBER_ME_KEY` | 「記住我」功能用的密鑰，隨便打一長串亂碼即可 |
| `DB_HOST`（選填） | MySQL 位址，預設 `localhost` |

二選一：

**做法 A：設成環境變數**（只在目前這個終端機視窗有效）

```powershell
# Windows PowerShell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的密碼"
$env:REMEMBER_ME_KEY="一長串隨機字"
```

```bash
# Mac / Linux
export DB_USERNAME=root
export DB_PASSWORD=你的密碼
export REMEMBER_ME_KEY=一長串隨機字
```

**做法 B：寫在 `fitness-tracker/src/main/resources/application-local.properties`**（這個檔案已經在 `.gitignore` 裡，不會被上傳）

```properties
DB_USERNAME=root
DB_PASSWORD=你的密碼
REMEMBER_ME_KEY=一長串隨機字
```

> 用 VS Code 的「執行」按鈕啟動時，`.vscode/launch.json` 會讀取專案根目錄的 `.env` 檔，也可以把上面三行寫在那裡（同樣已被 `.gitignore` 忽略）。

### 4. 啟動

```bash
cd fitness-tracker

# Windows
mvnw.cmd spring-boot:run

# Mac / Linux（如果出現 Permission denied，改用 sh mvnw spring-boot:run）
./mvnw spring-boot:run
```

看到 `Started FitnessTrackerApplication` 就成功了，打開瀏覽器到 <http://localhost:8080>，先註冊帳號再登入。

---

## 方法二：用 Docker 執行

適合「不想在電腦上裝 MySQL，只想把網站跑起來」。Docker Compose 會同時開兩個容器：網站本身＋一個 MySQL。

> 💡 **比喻**：Docker 容器像「便當盒」，把程式和它需要的環境一起裝好，到哪台電腦打開都是同一個味道。Docker Compose 則是「一次幫你拿好兩個便當（網站＋資料庫）並擺在一起」。

### 1. 先安裝

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- **JDK 17**（目前的 Dockerfile 需要先在本機打包出 jar 檔）

### 2. 準備 `.env`

```bash
cd fitness-tracker
cp .env.example .env        # Windows PowerShell：copy .env.example .env
```

打開 `.env`，把 `DB_PASSWORD`、`REMEMBER_ME_KEY` 換成你自己的值。

### 3. 打包並啟動

```bash
./mvnw clean package -DskipTests    # Windows：mvnw.cmd clean package -DskipTests
docker compose up --build
```

打開 <http://localhost:8080>。

- 停止：在終端機按 `Ctrl + C`，或執行 `docker compose down`
- 資料存在 Docker volume（資料卷）`fittracker_mysql_data`，容器刪掉重建資料還在；如果真的想清空，執行 `docker compose down -v`（⚠️ 會刪掉所有資料）
- 改了程式碼之後，要重新跑一次 `mvnw clean package -DskipTests` 再 `docker compose up --build`

---

## 執行測試

```bash
cd fitness-tracker
./mvnw test        # Windows：mvnw.cmd test
```

> ⚠️ 目前有一部分測試（`FitnessTrackerApplicationTests`、`WorkoutPlanServiceIntegrationTest`）會連真正的 MySQL，沒有開 MySQL 或沒設好上面的帳密時，這幾個測試會失敗；其餘測試不需要資料庫。

---

## 常見問題

**怎麼讓自己變成管理員（ADMIN），才能進 `/manage`？**
目前沒有畫面可以設定，要直接改資料庫。先註冊帳號，然後在 MySQL 執行：

```sql
UPDATE users SET role = 'ADMIN' WHERE email = '你的信箱';
```

登出再登入就會生效。

**註冊後要收驗證信嗎？**
預設不用（`app.verification.enabled=false`），註冊完就能登入。如果把它設成 `true` 但沒有設定寄信伺服器（SMTP），驗證連結會直接印在終端機（只在非正式環境）。

**忘記密碼的信會寄到哪？**
同上：沒設定 SMTP 時，重設密碼連結會印在終端機；要真的寄信，需要另外設定 `spring.mail.*`。

**啟動時出現 `Public Key Retrieval is not allowed` 或連不上資料庫？**
先確認 MySQL 有開、帳密正確、資料庫 `fitness_tracker` 已建立。設定檔已經處理 MySQL 8 的 Public Key Retrieval 問題。

**Docker 啟動時說 3306 port 被占用？**
代表你電腦上已經有一個 MySQL 在跑。先把本機的 MySQL 關掉，或把 `docker-compose.yml` 裡 mysql 的 `"3306:3306"` 改成 `"3307:3306"`。

**正式上線要注意什麼？**
用環境變數 `SPRING_PROFILES_ACTIVE=prod` 啟用 `application-prod.properties`（較少 log、cookie 只走 HTTPS），並放在 HTTPS 反向代理後面。注意：目前 `docker-compose.yml` 用的是 `local` profile。
