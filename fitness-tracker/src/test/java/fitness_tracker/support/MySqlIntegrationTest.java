package fitness_tracker.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 「需要真的 MySQL」的整合測試，在類別上加這一個標註就好。
 *
 * 它等於同時加上：
 * - @SpringBootTest：把整個 Spring 應用程式啟動起來
 * - @Import(MySqlTestcontainersConfig.class)：資料庫改連 Testcontainers 自動開的 MySQL 容器
 * - @Testcontainers(disabledWithoutDocker = true)：電腦上沒有 Docker 時，這些測試會被「跳過」，不會「失敗」
 *
 * REMEMBER_ME_KEY 是正式環境用環境變數注入的機密（見 .env.example），測試只需要隨便一個值讓程式能啟動，
 * 所以這裡直接給一個測試專用、沒有任何安全意義的假值。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "REMEMBER_ME_KEY=test-only-remember-me-key")
@Import(MySqlTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface MySqlIntegrationTest {
}
