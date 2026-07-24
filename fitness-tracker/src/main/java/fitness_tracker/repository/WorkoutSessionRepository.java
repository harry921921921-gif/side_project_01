package fitness_tracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import fitness_tracker.entity.User;
import fitness_tracker.entity.WorkoutSession;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    // ── 舊版（未過濾使用者）：保留給既有呼叫端/測試相容，正式流程請一律用 ByUser 版本 ──
    @EntityGraph(attributePaths = "sets")
    List<WorkoutSession> findAllByOrderByWorkoutDateDesc();

    @EntityGraph(attributePaths = "sets")
    Page<WorkoutSession> findAllByOrderByWorkoutDateDesc(Pageable pageable);

    @EntityGraph(attributePaths = "sets")
    Optional<WorkoutSession> findById(Long id);

    @EntityGraph(attributePaths = "sets")
    List<WorkoutSession> findByWorkoutDateBetweenOrderByWorkoutDateDesc(LocalDate startDate, LocalDate endDate);

    long countByWorkoutDateGreaterThanEqual(LocalDate startDate);

    // ── 使用者過濾版 ──
    @EntityGraph(attributePaths = "sets")
    List<WorkoutSession> findAllByUserOrderByWorkoutDateDesc(User user);

    @EntityGraph(attributePaths = "sets")
    Page<WorkoutSession> findAllByUserOrderByWorkoutDateDesc(User user, Pageable pageable);

    @EntityGraph(attributePaths = "sets")
    Optional<WorkoutSession> findByIdAndUser(Long id, User user);

    @EntityGraph(attributePaths = "sets")
    List<WorkoutSession> findByUserAndWorkoutDateBetweenOrderByWorkoutDateDesc(User user, LocalDate startDate, LocalDate endDate);

    long countByUserAndWorkoutDateGreaterThanEqual(User user, LocalDate startDate);

    // 舊資料遷移用：撈出還沒有擁有者的紀錄
    List<WorkoutSession> findByUserIsNull();
}
