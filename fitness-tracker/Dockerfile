# ── FitTracker Dockerfile（多階段建置）──
# 用法：直接在這個資料夾跑 docker compose up --build 就好，
# 不需要先在本機手動跑 ./mvnw package——編譯打包也在 Docker 裡面完成。
#
# 「多階段」的意思：
#   第 1 階段（build）用含 JDK + Maven 的環境把原始碼編譯成 jar；
#   第 2 階段（runtime）只拿那個 jar 放進精簡的 JRE image。
#   最後的 image 不會帶著原始碼、JDK、Maven 快取，體積更小、也比較安全。

# ---------- 第 1 階段：編譯打包 ----------
FROM eclipse-temurin:17-jdk AS build

WORKDIR /build

# 先只複製「下載相依套件需要的檔案」，再下載套件。
# Docker 會快取每一層：只要 pom.xml 沒變，下次重建就直接沿用這層，不用再下載一次所有套件。
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# 再複製原始碼並打包（測試需要真的 MySQL，這裡先跳過；測試應該在 CI 或本機跑）
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/fitness-tracker-*.jar app.jar

# ---------- 第 2 階段：執行 ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

# 不用 root 身分執行應用程式：就算程式被攻破，攻擊者拿到的權限也比較小
RUN useradd --system --no-create-home appuser
USER appuser

COPY --from=build /build/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
