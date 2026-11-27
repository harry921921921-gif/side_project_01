package fitness_tracker;

import org.junit.jupiter.api.Test;

import fitness_tracker.support.MySqlIntegrationTest;

// 資料庫由 Testcontainers 自動開的 MySQL 容器提供（見 MySqlIntegrationTest），沒有 Docker 時整個類別會被跳過
@MySqlIntegrationTest
class FitnessTrackerApplicationTests {

	@Test
	void contextLoads() {
	}

}
