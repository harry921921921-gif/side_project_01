# ── FitTracker Dockerfile ──
# 用法：先在本機跑 ./mvnw clean package -DskipTests 產生 jar，
# 再用 docker build 把這個 jar 包成容器 image。
# （進階版可以做「多階段建置」把編譯也包進 Docker，這裡先用最簡單的版本）

FROM eclipse-temurin:17-jre

WORKDIR /app

# target/ 底下只會有一個 jar（-DskipTests 產生的那個），用萬用字元避免每次改版本號都要改這裡
COPY target/fitness-tracker-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
