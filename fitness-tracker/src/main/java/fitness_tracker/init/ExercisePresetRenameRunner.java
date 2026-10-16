package fitness_tracker.init;

import fitness_tracker.entity.Exercise;
import fitness_tracker.repository.ExerciseRepository;
import org.springframework.core.annotation.Order;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

// 把既有資料庫裡舊命名的預設動作，改名成跟 /plan 頁課表一致的口語化名稱（歷史訓練紀錄/PR 都是用那套名字記的，
// 只改 Exercise 表不影響任何使用者資料）。全新安裝的資料庫不會進到這裡（ExerciseDataInitializer 已經用新名字建）。
// 每次啟動只處理還沒改過名的，改完就沒事做（idempotent）。
@Component
@Order(3)
public class ExercisePresetRenameRunner implements CommandLineRunner {

    private static final Map<String, String> RENAMES = new LinkedHashMap<>();
    static {
        RENAMES.put("啞鈴上斜臥推", "上斜啞鈴推");
        RENAMES.put("啞鈴側舉", "側平舉");
        RENAMES.put("坐姿滑輪划船", "坐姿划船");
        RENAMES.put("小腿提升", "小腿舉");
        RENAMES.put("鎚式彎舉", "錘式彎舉");
        RENAMES.put("啞鈴飛鳥", "飛鳥");
        RENAMES.put("腿推機", "腿推");
        RENAMES.put("窄距臥推", "窄握推");
        RENAMES.put("繩索夾胸", "纜繩夾胸");
        RENAMES.put("啞鈴前舉", "前平舉");
        RENAMES.put("過頭三頭伸展", "三頭伸展");
        RENAMES.put("雙槓撐體", "雙槓臂屈伸");
        RENAMES.put("槓鈴肩推", "肩推");
        RENAMES.put("槓鈴彎舉", "二頭彎舉");
    }

    // 舊資料庫裡完全沒有的動作，照現在的種子清單補上
    private static final Map<String, String[]> MISSING = new LinkedHashMap<>();
    static {
        MISSING.put("伏地挺身", new String[]{"胸", "COMPOUND"});
        MISSING.put("聳肩", new String[]{"肩", "ISOLATION"});
        MISSING.put("集中彎舉", new String[]{"手臂", "ISOLATION"});
    }

    private final ExerciseRepository repo;

    public ExercisePresetRenameRunner(ExerciseRepository repo) { this.repo = repo; }

    @Override
    @Transactional
    public void run(String... args) {
        int renamed = 0;
        for (Map.Entry<String, String> e : RENAMES.entrySet()) {
            if (repo.existsByName(e.getValue())) continue; // 已經改過名了
            Exercise existing = repo.findByName(e.getKey()).orElse(null);
            if (existing != null) {
                existing.setName(e.getValue());
                repo.save(existing);
                renamed++;
            }
        }
        if (renamed > 0) {
            System.out.println("[ExercisePresetRenameRunner] 已改名 " + renamed + " 個預設動作");
        }

        int inserted = 0;
        for (Map.Entry<String, String[]> e : MISSING.entrySet()) {
            String name = e.getKey();
            if (repo.existsByName(name)) continue;
            Exercise ex = new Exercise(name, e.getValue()[0], e.getValue()[1]);
            ex.setMovement(ExerciseMovements.movementFor(name));
            repo.save(ex);
            inserted++;
        }

        if (inserted > 0) {
            System.out.println("[ExercisePresetRenameRunner] 已補上 " + inserted + " 個原本缺少的預設動作");
        }
    }
}
