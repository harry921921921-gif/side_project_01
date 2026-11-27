package fitness_tracker.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 給需要「真的資料庫」的整合測試用的設定。
 *
 * 跑測試時，Testcontainers 會用 Docker 啟動一個跟正式環境同版本（mysql:8.0，見 docker-compose.yml）
 * 的 MySQL 容器；@ServiceConnection 會自動把 spring.datasource.url / username / password
 * 指向這個容器，所以不需要本機先裝 MySQL，也不需要設定 DB_USERNAME / DB_PASSWORD。
 *
 * 這個設定由 Spring 管理生命週期，多個測試類別用到同一組設定時會共用同一個容器（Spring 會快取測試用的
 * ApplicationContext），不會每個類別都重開一次 MySQL。
 *
 * 用法：不用直接用這個類別，在測試類別上加 @MySqlIntegrationTest 就會自動套用。
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainersConfig {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
    }
}
