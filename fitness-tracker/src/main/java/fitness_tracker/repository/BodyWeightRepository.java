package fitness_tracker.repository;

import fitness_tracker.entity.BodyWeight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JpaRepository<BodyWeight, Long>：
 *   第一個泛型 = 操作的 Entity class
 *   第二個泛型 = 主鍵的型別（id 是 Long）
 *
 * Spring Data JPA 的「方法命名規則」：
 *   findAllByOrderByRecordedDateDesc → 查全部，依 recordedDate 降冪排序（最新的在前）
 *   不需要寫任何 SQL！Spring 會自動解析方法名稱並產生查詢
 */
public interface BodyWeightRepository extends JpaRepository<BodyWeight, Long> {

    List<BodyWeight> findAllByOrderByRecordedDateDescCreatedAtDesc();
}
