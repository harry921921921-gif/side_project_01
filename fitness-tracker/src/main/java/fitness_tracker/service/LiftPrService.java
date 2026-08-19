package fitness_tracker.service;

import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.repository.LiftPrRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LiftPrService {

    private static final Logger log = LoggerFactory.getLogger(LiftPrService.class);

    private final LiftPrRepository repo;

    public LiftPrService(LiftPrRepository repo) { this.repo = repo; }

    public List<LiftPr> findByUser(User user) { return repo.findByUser(user); }

    public Optional<LiftPr> findOverride(User user, String exerciseName) {
        return repo.findByUserAndExerciseName(user, exerciseName);
    }

    // 存/更新某動作的 PR；1RM 用 Epley 估算
    @Transactional
    public void save(User user, String exerciseName, double weight, int reps) {
        LiftPr pr = repo.findByUserAndExerciseName(user, exerciseName).orElseGet(LiftPr::new);
        pr.setUser(user);
        pr.setExerciseName(exerciseName);
        pr.setWeightKg(weight);
        pr.setReps(reps);
        int rc = Math.min(Math.max(reps, 1), 10);
        double orm = (reps == 1) ? weight : weight * (1 + rc / 30.0);
        pr.setOneRepMax(Math.round(orm * 10) / 10.0);
        log.info("Saving PR for userId={}: exercise={}, weight={}, reps={}", user.getId(), exerciseName, weight, reps);
        repo.save(pr);
    }

    // 使用者在課表頁編輯彈窗手動指定某動作的重量/組數/次數/休息——跟 save() 不同的是這裡
    // 不排除主要動作（深蹲/臥推/硬舉/肩推），因為手動覆寫本來就是要接管漸進式加重的起點，
    // 而不是「完成訓練後自動記錄配件重量」那條路徑（那條路徑仍走 save()，維持原本的排除規則）
    @Transactional
    public void saveManual(User user, String exerciseName, double weightKg, int sets, int reps, int restSeconds) {
        LiftPr pr = repo.findByUserAndExerciseName(user, exerciseName).orElseGet(LiftPr::new);
        pr.setUser(user);
        pr.setExerciseName(exerciseName);
        pr.setWeightKg(weightKg);
        pr.setReps(reps);
        pr.setSets(sets);
        pr.setRestSeconds(restSeconds);
        int rc = Math.min(Math.max(reps, 1), 10);
        double orm = (reps == 1) ? weightKg : weightKg * (1 + rc / 30.0);
        pr.setOneRepMax(Math.round(orm * 10) / 10.0);
        log.info("Saving manual override for userId={}: exercise={}, weight={}, sets={}, reps={}, restSeconds={}",
                user.getId(), exerciseName, weightKg, sets, reps, restSeconds);
        repo.save(pr);
    }
}
