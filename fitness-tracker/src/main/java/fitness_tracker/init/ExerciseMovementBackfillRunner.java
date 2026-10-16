package fitness_tracker.init;

import fitness_tracker.entity.Exercise;
import fitness_tracker.repository.ExerciseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 回填：把現有動作(movement=NULL)依名稱補上 PUSH/PULL/LEGS/CORE。
// 每次啟動只處理還沒標的，標完就沒事做（idempotent），不用手動開關。
@Component
@Order(4)
public class ExerciseMovementBackfillRunner implements CommandLineRunner {

    private final ExerciseRepository repo;

    public ExerciseMovementBackfillRunner(ExerciseRepository repo) { this.repo = repo; }

    @Override
    @Transactional
    public void run(String... args) {
        List<Exercise> missing = repo.findByMovementIsNull();
        if (missing.isEmpty()) return;
        int n = 0;
        for (Exercise e : missing) {
            String m = ExerciseMovements.movementFor(e.getName());
            if (m != null) { e.setMovement(m); n++; }
        }
        repo.saveAll(missing);
        System.out.println("[ExerciseMovementBackfillRunner] 已回填 " + n + " 個動作的 movement");
    }
}
