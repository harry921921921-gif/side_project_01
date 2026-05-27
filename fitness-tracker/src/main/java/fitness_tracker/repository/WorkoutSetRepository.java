package fitness_tracker.repository;

import fitness_tracker.entity.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    // 查詢某次訓練（session）底下的所有動作
    List<WorkoutSet> findBySessionId(Long sessionId);
}
