package fitness_tracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.repository.LiftPrRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LiftPrService {

    private static final Logger log = LoggerFactory.getLogger(LiftPrService.class);

    private final LiftPrRepository repo;
    private final ObjectMapper objectMapper;

    public LiftPrService(LiftPrRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    // 依週期階段分開存的手動覆寫：這個動作在「這個階段」下的重量/組數/次數/休息
    public record PhaseOverride(Double weightKg, Integer sets, Integer reps, Integer restSeconds) {}

    public List<LiftPr> findByUser(User user) { return repo.findByUser(user); }

    public Optional<LiftPr> findOverride(User user, String exerciseName) {
        return repo.findByUserAndExerciseName(user, exerciseName);
    }

    // 這個動作依階段分開存的手動覆寫，給 /plan 頁組裝 phasePrs 用
    public Map<String, PhaseOverride> phaseOverridesOf(LiftPr pr) {
        return parsePhaseOverrides(pr.getPhaseOverridesJson());
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

    // 使用者在課表頁編輯彈窗手動指定某動作的重量/組數/次數/休息（或完成一次真實訓練自動記錄配件），
    // 跟 save() 不同的是這裡不排除主要動作（深蹲/臥推/硬舉/肩推）。
    //
    // phase 是存這筆的當下屬於哪個週期階段（"adapt"/"hyper"/"strength"）：在最大力量期存的 5×5
    // 不能在肌耐力期也顯示 5×5，所以組數/次數/休息／配件重量要分階段各存一份（phaseOverridesJson）。
    // 但頂層 weightKg/updatedAt 仍然照存不分階段——那個是給新手模式主項重量的漸進起點用
    // （WorkoutService.mainLiftProgress），漸進式加重本來就該跨階段連續累加，不能因為換階段就重置。
    @Transactional
    public void saveManual(User user, String exerciseName, String phase, double weightKg, int sets, int reps, int restSeconds) {
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

        if (phase != null && !phase.isBlank()) {
            Map<String, PhaseOverride> phaseOverrides = new LinkedHashMap<>(parsePhaseOverrides(pr.getPhaseOverridesJson()));
            phaseOverrides.put(phase, new PhaseOverride(weightKg, sets, reps, restSeconds));
            pr.setPhaseOverridesJson(writePhaseOverrides(phaseOverrides));
        }

        log.info("Saving manual override for userId={}: exercise={}, phase={}, weight={}, sets={}, reps={}, restSeconds={}",
                user.getId(), exerciseName, phase, weightKg, sets, reps, restSeconds);
        repo.save(pr);
    }

    private Map<String, PhaseOverride> parsePhaseOverrides(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, PhaseOverride>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse phase overrides, ignoring: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String writePhaseOverrides(Map<String, PhaseOverride> phaseOverrides) {
        try {
            return objectMapper.writeValueAsString(phaseOverrides);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize phase overrides", ex);
        }
    }
}
