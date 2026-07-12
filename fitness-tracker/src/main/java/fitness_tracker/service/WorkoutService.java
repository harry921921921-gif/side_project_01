package fitness_tracker.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fitness_tracker.entity.Exercise;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.enums.CompletionStatus;
import fitness_tracker.exception.ResourceNotFoundException;
import fitness_tracker.repository.BodyPartRepository;
import fitness_tracker.repository.WorkoutSessionRepository;

@Service
public class WorkoutService {

    private final WorkoutSessionRepository repository;
    private final ExerciseService exerciseService;
    private final BodyPartRepository bodyPartRepository;

    public WorkoutService(WorkoutSessionRepository repository,
                          ExerciseService exerciseService,
                          BodyPartRepository bodyPartRepository) {
        this.repository = repository;
        this.exerciseService = exerciseService;
        this.bodyPartRepository = bodyPartRepository;
    }

    public Optional<WorkoutSession> findById(long id) {
        return repository.findById(id);
    }

    public List<WorkoutSession> findAll() {
        return repository.findAllByOrderByWorkoutDateDesc();
    }

    public List<WorkoutSession> findRecent(int limit) {
        List<WorkoutSession> all = findAll();
        return all.subList(0, Math.min(limit, all.size()));
    }

    public List<WorkoutSession> findRecentWithinDays(int days) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(days - 1);
        return findAll().stream()
                .filter(s -> !s.getWorkoutDate().isBefore(cutoff) && !s.getWorkoutDate().isAfter(today))
                .toList();
    }

    public long countThisWeek() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        return repository.countByWorkoutDateGreaterThanEqual(monday);
    }

    @Transactional
    public void save(WorkoutSession session,
                     List<String> exerciseNames,
                     List<Double> weightKgs,
                     List<Integer> sets,
                     List<Integer> reps,
                     List<Integer> restSeconds,
                     List<Double> rpes,
                     List<String> completionStatuses,
                     List<Integer> actualRepsList,
                     List<Double> actualWeights,
                     List<String> notesList) {

        validateBodyPart(session.getBodyPart());

        for (int i = 0; i < exerciseNames.size(); i++) {
            String name = exerciseNames.get(i);
            if (name != null && !name.trim().isEmpty()) {
                WorkoutSet workoutSet = new WorkoutSet();
                workoutSet.setExerciseName(name.trim());
                workoutSet.setWeightKg(safeGet(weightKgs, i));
                workoutSet.setSets(safeGet(sets, i));
                workoutSet.setReps(safeGet(reps, i));
                workoutSet.setRestSeconds(safeGet(restSeconds, i));
                workoutSet.setRpe(safeGet(rpes, i));
                workoutSet.setCompletionStatus(parseCompletionStatus(safeGet(completionStatuses, i)));
                workoutSet.setActualReps(safeGet(actualRepsList, i));
                workoutSet.setActualWeight(safeGet(actualWeights, i));
                workoutSet.setNotes(safeGet(notesList, i));
                workoutSet.setSession(session);
                session.getSets().add(workoutSet);
            }
        }
        repository.save(session);
    }

    @Transactional
    public void update(Long id,
                       LocalDate workoutDate,
                       String bodyPart,
                       String note,
                       List<String> exerciseNames,
                       List<Double> weightKgs,
                       List<Integer> sets,
                       List<Integer> reps,
                       List<Integer> restSeconds,
                       List<Double> rpes,
                       List<String> completionStatuses,
                       List<Integer> actualRepsList,
                       List<Double> actualWeights,
                       List<String> notesList) {

        WorkoutSession existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("找不到 id=" + id + " 的訓練紀錄"));
        validateBodyPart(bodyPart);
        existing.setWorkoutDate(workoutDate);
        existing.setBodyPart(bodyPart);
        existing.setNote(note);
        existing.getSets().clear();

        if (exerciseNames != null) {
            for (int i = 0; i < exerciseNames.size(); i++) {
                String name = exerciseNames.get(i);
                if (name != null && !name.trim().isEmpty()) {
                    WorkoutSet ws = new WorkoutSet();
                    ws.setExerciseName(name.trim());
                    ws.setWeightKg(safeGet(weightKgs, i));
                    ws.setSets(safeGet(sets, i));
                    ws.setReps(safeGet(reps, i));
                    ws.setRestSeconds(safeGet(restSeconds, i));
                    ws.setRpe(safeGet(rpes, i));
                    ws.setCompletionStatus(parseCompletionStatus(safeGet(completionStatuses, i)));
                    ws.setActualReps(safeGet(actualRepsList, i));
                    ws.setActualWeight(safeGet(actualWeights, i));
                    ws.setNotes(safeGet(notesList, i));
                    ws.setSession(existing);
                    existing.getSets().add(ws);
                }
            }
        }
        repository.save(existing);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("找不到 id=" + id + " 的訓練紀錄");
        }
        repository.deleteById(id);
    }

    private <T> T safeGet(List<T> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private void validateBodyPart(String bodyPart) {
        if (bodyPart == null || bodyPart.isBlank()) {
            throw new IllegalArgumentException("bodyPart 為必填");
        }
        boolean exists = bodyPartRepository.findByName(bodyPart.trim()).isPresent();
        if (!exists) {
            throw new IllegalArgumentException("bodyPart 必須存在於 BodyPart 清單中");
        }
    }

    private CompletionStatus parseCompletionStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CompletionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("completionStatus 不合法");
        }
    }

    // ── 訓練統計（首頁 Dashboard 用）──────────────────────
    public record RecentSessionCompletion(LocalDate date, String bodyPart, Integer completionPct) {}

    public record BodyPartVolume(String bodyPart, double volume) {}

    public record DashboardStats(
            long weeklyCount,
            List<BodyPartVolume> weeklyVolumeByBodyPart,
            Double avgRpe,
            String lastSessionSummary,
            List<RecentSessionCompletion> recentCompletions
    ) {}

    public DashboardStats computeDashboardStats() {
        long weeklyCount = countThisWeek();

        // 動作名稱 → 分類（COMPOUND / ISOLATION），只計算經典複合式動作（健力三項、引體向上等）的訓練量
        Map<String, String> categoryByExerciseName = exerciseService.findAll().stream()
                .collect(Collectors.toMap(Exercise::getName, Exercise::getCategory, (a, b) -> a));

        Map<String, Double> volumeByBodyPart = new LinkedHashMap<>();
        List<Double> rpes = new ArrayList<>();
        for (WorkoutSession s : findRecentWithinDays(7)) {
            for (WorkoutSet set : s.getSets()) {
                boolean isCompound = "COMPOUND".equalsIgnoreCase(categoryByExerciseName.get(set.getExerciseName()));
                if (isCompound) {
                    Double weight = set.getActualWeight() != null ? set.getActualWeight() : set.getWeightKg();
                    Integer repCount = set.getActualReps() != null ? set.getActualReps() : set.getReps();
                    Integer setCount = set.getSets();
                    if (weight != null && repCount != null && setCount != null) {
                        String bodyPart = s.getBodyPart() != null ? s.getBodyPart() : "未分類";
                        volumeByBodyPart.merge(bodyPart, weight * repCount * setCount, Double::sum);
                    }
                }
                if (set.getRpe() != null) {
                    rpes.add(set.getRpe());
                }
            }
        }
        List<BodyPartVolume> weeklyVolumeByBodyPart = volumeByBodyPart.entrySet().stream()
                .map(e -> new BodyPartVolume(e.getKey(), e.getValue()))
                .toList();
        Double avgRpe = rpes.isEmpty() ? null
                : rpes.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        List<WorkoutSession> all = findAll(); // 已依日期降冪排序
        String lastSummary = "尚無訓練紀錄";
        if (!all.isEmpty()) {
            WorkoutSession last = all.get(0);
            String exSummary = last.getSets().stream()
                    .map(set -> set.getExerciseName()
                            + (set.getWeightKg() != null ? " " + set.getWeightKg() + "kg" : " 徒手")
                            + "×" + (set.getSets() != null ? set.getSets() : "-")
                            + "組×" + (set.getReps() != null ? set.getReps() : "-") + "次")
                    .collect(Collectors.joining("、"));
            lastSummary = last.getWorkoutDate() + " "
                    + (last.getBodyPart() != null ? last.getBodyPart() : "")
                    + "：" + (exSummary.isEmpty() ? "無動作紀錄" : exSummary);
        }

        List<RecentSessionCompletion> completions = new ArrayList<>();
        for (WorkoutSession s : all.stream().limit(3).toList()) {
            List<WorkoutSet> withStatus = s.getSets().stream()
                    .filter(x -> x.getCompletionStatus() != null)
                    .toList();
            Integer pct = null;
            if (!withStatus.isEmpty()) {
                long completeCount = withStatus.stream()
                        .filter(x -> x.getCompletionStatus() == CompletionStatus.COMPLETE)
                        .count();
                pct = (int) Math.round(completeCount * 100.0 / withStatus.size());
            }
            completions.add(new RecentSessionCompletion(s.getWorkoutDate(), s.getBodyPart(), pct));
        }

        return new DashboardStats(weeklyCount, weeklyVolumeByBodyPart, avgRpe, lastSummary, completions);
    }
}
