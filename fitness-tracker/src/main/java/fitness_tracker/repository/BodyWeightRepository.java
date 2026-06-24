package fitness_tracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;//這個是 Spring Data JPA 的核心接口，提供了基本的 CRUD 操作和查詢方法

import fitness_tracker.entity.BodyWeight;

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
