package fitness_tracker.repository;

import fitness_tracker.entity.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    // 查全部，依訓練日期降冪排序
    List<WorkoutSession> findAllByOrderByWorkoutDateDesc();

    // 計算「大於等於某日期」的訓練次數（用來算本週訓練數）
    // Spring 把方法名稱解析成：COUNT WHERE workout_date >= startDate
    long countByWorkoutDateGreaterThanEqual(LocalDate startDate);
}
