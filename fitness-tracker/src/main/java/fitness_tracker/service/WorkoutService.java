package fitness_tracker.service;

import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class WorkoutService {

    private final WorkoutSessionRepository repository;

    public WorkoutService(WorkoutSessionRepository repository) {
        this.repository = repository;
    }

    // 查單筆訓練紀錄
    public Optional<WorkoutSession> findById(long id) {
        return repository.findById(id);
    }

    // 查全部訓練紀錄
    public List<WorkoutSession> findAll() {
        return repository.findAllByOrderByWorkoutDateDesc();
    }

    // 取最近 N 筆（給首頁 Dashboard 用）
    public List<WorkoutSession> findRecent(int limit) {
        List<WorkoutSession> all = findAll();
        return all.subList(0, Math.min(limit, all.size()));
    }

    // 計算本週訓練次數（從本週一算起）
    public long countThisWeek() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        return repository.countByWorkoutDateGreaterThanEqual(monday);
    }

    /**
     * 儲存一次訓練（包含多個動作）
     * 使用 CascadeType.ALL，存 Session 時會自動存底下所有 Set
     */
    public void save(WorkoutSession session,
                     List<String> exerciseNames,
                     List<Double> weightKgs,
                     List<Integer> sets,
                     List<Integer> reps) {

        for (int i = 0; i < exerciseNames.size(); i++) {
            String name = exerciseNames.get(i);
            if (name != null && !name.trim().isEmpty()) {
                WorkoutSet workoutSet = new WorkoutSet();
                workoutSet.setExerciseName(name.trim());
                workoutSet.setWeightKg(weightKgs != null && i < weightKgs.size() ? weightKgs.get(i) : null);
                workoutSet.setSets(sets != null && i < sets.size() ? sets.get(i) : null);
                workoutSet.setReps(reps != null && i < reps.size() ? reps.get(i) : null);
                workoutSet.setSession(session);         // 設定外鍵關聯
                session.getSets().add(workoutSet);      // 加入 Session 的 sets 清單
            }
        }
        repository.save(session); // Cascade 會自動儲存所有 WorkoutSet
    }

    // 刪除整筆訓練（Cascade 會連帶刪除底下的 Set）
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
