package fitness_tracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;//這個是 Spring Data JPA 的核心接口，提供了基本的 CRUD 操作和查詢方法

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.entity.User;

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

    // ── 舊版（未過濾使用者）：保留給既有呼叫端/測試相容，正式流程請一律用 ByUser 版本 ──
    List<BodyWeight> findAllByOrderByRecordedDateDescCreatedAtDesc();

    Page<BodyWeight> findAllByOrderByRecordedDateDescCreatedAtDesc(Pageable pageable);

    // ── 使用者過濾版 ──
    List<BodyWeight> findAllByUserOrderByRecordedDateDescCreatedAtDesc(User user);

    Page<BodyWeight> findAllByUserOrderByRecordedDateDescCreatedAtDesc(User user, Pageable pageable);

    Optional<BodyWeight> findTopByUserOrderByRecordedDateDescCreatedAtDesc(User user);

    Optional<BodyWeight> findByIdAndUser(Long id, User user);

    // 舊資料遷移用：撈出還沒有擁有者的紀錄
    List<BodyWeight> findByUserIsNull();
}
