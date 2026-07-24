package fitness_tracker.init;

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.entity.User;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.repository.BodyWeightRepository;
import fitness_tracker.repository.UserRepository;
import fitness_tracker.repository.WorkoutSessionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 一次性遷移：把 Stage 2 上線前留下的 user_id = NULL 舊資料指派給第一位註冊的使用者。
// 預設關閉，只有在 application.properties/環境變數設定
// app.migration.assign-legacy-data-to-first-user=true 時才會在啟動時執行一次。
// 資料遷移完成、確認沒有孤兒紀錄後，建議把這個 flag 關掉（或直接刪除這個 class）。
@Component
@Order(10)
@ConditionalOnProperty(name = "app.migration.assign-legacy-data-to-first-user", havingValue = "true")
public class LegacyDataOwnerMigrationRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BodyWeightRepository bodyWeightRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    public LegacyDataOwnerMigrationRunner(UserRepository userRepository,
                                          BodyWeightRepository bodyWeightRepository,
                                          WorkoutSessionRepository workoutSessionRepository) {
        this.userRepository = userRepository;
        this.bodyWeightRepository = bodyWeightRepository;
        this.workoutSessionRepository = workoutSessionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        User owner = userRepository.findFirstByOrderByIdAsc().orElse(null);
        if (owner == null) {
            System.out.println("[LegacyDataOwnerMigrationRunner] 目前沒有任何使用者，略過遷移");
            return;
        }

        List<BodyWeight> orphanWeights = bodyWeightRepository.findByUserIsNull();
        orphanWeights.forEach(w -> w.setUser(owner));
        bodyWeightRepository.saveAll(orphanWeights);

        List<WorkoutSession> orphanSessions = workoutSessionRepository.findByUserIsNull();
        orphanSessions.forEach(s -> s.setUser(owner));
        workoutSessionRepository.saveAll(orphanSessions);

        System.out.println("[LegacyDataOwnerMigrationRunner] 已將 " + orphanWeights.size()
                + " 筆體重紀錄、" + orphanSessions.size() + " 筆訓練紀錄指派給使用者 id="
                + owner.getId() + " (" + owner.getEmail() + ")");
    }
}
