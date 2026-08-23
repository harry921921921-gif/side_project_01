package fitness_tracker.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fitness_tracker.dto.WorkoutRequest;
import fitness_tracker.entity.Exercise;
import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.enums.CompletionStatus;
import fitness_tracker.exception.ResourceNotFoundException;
import fitness_tracker.repository.BodyPartRepository;
import fitness_tracker.repository.WorkoutSessionRepository;
import fitness_tracker.repository.WorkoutSetRepository;

@Service
public class WorkoutService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutService.class);

    // 這幾個主項課表頁會用 1RM 百分比算重量，不能被單次記錄的次極限重量覆蓋回 PR
    private static final Set<String> MAIN_LIFT_NAMES = Set.of("深蹲", "臥推", "硬舉", "肩推");

    private final WorkoutSessionRepository repository;
    private final WorkoutSetRepository workoutSetRepository;
    private final ExerciseService exerciseService;
    private final BodyPartRepository bodyPartRepository;
    private final LiftPrService liftPrService;

    public WorkoutService(WorkoutSessionRepository repository,
                          WorkoutSetRepository workoutSetRepository,
                          ExerciseService exerciseService,
                          BodyPartRepository bodyPartRepository,
                          LiftPrService liftPrService) {
        this.repository = repository;
        this.workoutSetRepository = workoutSetRepository;
        this.exerciseService = exerciseService;
        this.bodyPartRepository = bodyPartRepository;
        this.liftPrService = liftPrService;
    }

    // ── 舊版（未過濾使用者）：保留給既有呼叫端/測試相容，正式流程請一律用帶 User 的版本 ──
    @Transactional(readOnly = true)
    public List<WorkoutSession> findAll() {
        return repository.findAllByOrderByWorkoutDateDesc();
    }

    @Transactional(readOnly = true)
    public Page<WorkoutSession> findPage(Pageable pageable) {
        return repository.findAllByOrderByWorkoutDateDesc(pageable);
    }

    public List<WorkoutSession> findRecent(int limit) {
        List<WorkoutSession> all = findAll();
        return all.subList(0, Math.min(limit, all.size()));
    }

    @Transactional(readOnly = true)
    public List<WorkoutSession> findRecentWithinDays(int days) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(days - 1);
        return repository.findByWorkoutDateBetweenOrderByWorkoutDateDesc(cutoff, today);
    }

    @Transactional(readOnly = true)
    public long countThisWeek() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        return repository.countByWorkoutDateGreaterThanEqual(monday);
    }

    // ── 使用者過濾版：controller 一律用這組 ──
    @Transactional(readOnly = true)
    public Optional<WorkoutSession> findById(long id, User user) {
        return repository.findByIdAndUser(id, user);
    }

    @Transactional(readOnly = true)
    public List<WorkoutSession> findAll(User user) {
        return repository.findAllByUserOrderByWorkoutDateDesc(user);
    }

    @Transactional(readOnly = true)
    public Page<WorkoutSession> findPage(Pageable pageable, User user) {
        return repository.findAllByUserOrderByWorkoutDateDesc(user, pageable);
    }

    public List<WorkoutSession> findRecent(int limit, User user) {
        List<WorkoutSession> all = findAll(user);
        return all.subList(0, Math.min(limit, all.size()));
    }

    @Transactional(readOnly = true)
    public List<WorkoutSession> findRecentWithinDays(int days, User user) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(days - 1);
        return repository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateDesc(user, cutoff, today);
    }

    @Transactional(readOnly = true)
    public long countThisWeek(User user) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        return repository.countByUserAndWorkoutDateGreaterThanEqual(user, monday);
    }

    // 本週（週一到週日）已經記錄過的 bodyPart 集合，給 /plan 頁的「本週完整課表」拿掉已完成的卡片用
    @Transactional(readOnly = true)
    public Set<String> completedBodyPartsThisWeek(User user) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        return repository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateDesc(user, monday, sunday).stream()
                .map(WorkoutSession::getBodyPart)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // 新手模式主項重量要接續「上一次真的完成」的那次往上疊加，不能每次都停在空槓——查每個主項
    // 最近一次 completionStatus=COMPLETE 的紀錄；失敗/中途放棄/疼痛那幾筆不算，維持原重量不貿然加重
    @Transactional(readOnly = true)
    public Map<String, WorkoutSet> lastCompletedMainLifts(User user) {
        List<WorkoutSet> sets = workoutSetRepository
                .findBySession_UserAndExerciseNameInAndCompletionStatusOrderBySession_WorkoutDateDescIdDesc(
                        user, MAIN_LIFT_NAMES, CompletionStatus.COMPLETE);
        Map<String, WorkoutSet> latest = new LinkedHashMap<>();
        for (WorkoutSet ws : sets) {
            latest.putIfAbsent(ws.getExerciseName(), ws);
        }
        return latest;
    }

    // 連續卡關次數的門檻：從最新一筆往回數，連續幾次不是 COMPLETE 就視為卡關
    private static final int STALL_THRESHOLD = 3;

    public record MainLiftProgress(double lastCompletedWeightKg, boolean stalled) {}

    // 新手模式主項重量進階的完整版本：帶「是否卡關」——只會一直 +2.5/+5kg 疊加、完全不管
    // 使用者其實一直練失敗的話，線性進步拉長時間會算出不合理的天文數字。這裡額外查每個主項
    // 最近幾筆紀錄（不分完成狀態），如果從最新的往回數連續 N 次都不是 COMPLETE，
    // 代表使用者已經卡在這個重量了，下次不該再往上加，而是先降回約 90% 讓對方重新累積信心。
    //
    // 使用者也可能在課表頁編輯彈窗手動指定過某個主項的重量（LiftPr.saveManual）——這個手動值
    // 要接管漸進起點，直到使用者又真的完成一次新的訓練紀錄為止：比較「手動覆寫的存檔時間」
    // 跟「最近一次真實完成紀錄的訓練日期」誰比較新，較新的那個當作這次的基準重量。
    @Transactional(readOnly = true)
    public Map<String, MainLiftProgress> mainLiftProgress(User user) {
        Map<String, WorkoutSet> lastCompleted = lastCompletedMainLifts(user);
        Map<String, MainLiftProgress> result = new LinkedHashMap<>();
        Set<String> names = new LinkedHashSet<>(MAIN_LIFT_NAMES);
        for (Map.Entry<String, WorkoutSet> entry : lastCompleted.entrySet()) {
            names.add(entry.getKey());
        }
        for (String name : names) {
            WorkoutSet last = lastCompleted.get(name);
            Optional<LiftPr> override = liftPrService.findOverride(user, name);
            boolean overrideWins = override.isPresent() && (last == null
                    || !override.get().getUpdatedAt().toLocalDate().isBefore(last.getSession().getWorkoutDate()));
            if (overrideWins) {
                result.put(name, new MainLiftProgress(override.get().getWeightKg(), false));
            } else if (last != null) {
                double weight = last.getActualWeight() != null ? last.getActualWeight() : last.getWeightKg();
                result.put(name, new MainLiftProgress(weight, isStalled(user, name)));
            }
        }
        return result;
    }

    private boolean isStalled(User user, String exerciseName) {
        List<WorkoutSet> recent = workoutSetRepository
                .findTop10BySession_UserAndExerciseNameOrderBySession_WorkoutDateDescIdDesc(user, exerciseName);
        int consecutiveFails = 0;
        for (WorkoutSet ws : recent) {
            if (ws.getCompletionStatus() == CompletionStatus.COMPLETE) break;
            consecutiveFails++;
        }
        return consecutiveFails >= STALL_THRESHOLD;
    }

    // 本週已經練過的動作名稱集合，給 WorkoutPlanService 排配件動作時「一週去重」用
    @Transactional(readOnly = true)
    public Set<String> exerciseNamesThisWeek(User user) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        return repository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateDesc(user, monday, sunday).stream()
                .flatMap(s -> s.getSets().stream())
                .map(WorkoutSet::getExerciseName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // exercises 是一個動作一筆的結構化資料（跟 REST API 的 WorkoutRequest.ExerciseDto 共用同一個
    // 形狀），不是 8 個要靠 index 對齊的平行 List——不然兩個同型別的參數順序寫反，編譯器完全看不出來，
    // 會安靜把重量存成組數。MVC 表單控制器負責把送出來的平行欄位 zip 成這個 list 再呼叫這裡。
    @Transactional
    public void save(WorkoutSession session, List<WorkoutRequest.ExerciseDto> exercises) {
        validateBodyPart(session.getBodyPart());
        validateExerciseCount(exercises);

        for (WorkoutRequest.ExerciseDto ex : exercises) {
            String name = ex.exerciseName();
            if (name != null && !name.trim().isEmpty()) {
                WorkoutSet workoutSet = toWorkoutSet(ex, name);
                workoutSet.setSession(session);
                validateSet(workoutSet);
                session.getSets().add(workoutSet);
                recordAccessoryPr(session.getUser(), workoutSet);
            }
        }
        log.info("Creating workout session for bodyPart={} with {} exercise(s)", session.getBodyPart(), session.getSets().size());
        repository.save(session);
        log.info("Created workout session id={}", session.getId());
    }

    @Transactional
    public void save(WorkoutSession session, List<WorkoutRequest.ExerciseDto> exercises, User user) {
        session.setUser(user);
        save(session, exercises);
    }

    @Transactional
    public void update(Long id, LocalDate workoutDate, String bodyPart, String note,
                       List<WorkoutRequest.ExerciseDto> exercises, User user) {
        WorkoutSession existing = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("找不到 id=" + id + " 的訓練紀錄"));
        applyUpdate(existing, workoutDate, bodyPart, note, exercises);
        log.info("Updating workout session id={}", id);
        repository.save(existing);
        log.info("Updated workout session id={}", id);
    }

    private void applyUpdate(WorkoutSession existing, LocalDate workoutDate, String bodyPart, String note,
                             List<WorkoutRequest.ExerciseDto> exercises) {
        validateBodyPart(bodyPart);
        validateExerciseCount(exercises);
        existing.setWorkoutDate(workoutDate);
        existing.setBodyPart(bodyPart);
        existing.setNote(note);
        existing.getSets().clear();

        if (exercises != null) {
            for (WorkoutRequest.ExerciseDto ex : exercises) {
                String name = ex.exerciseName();
                if (name != null && !name.trim().isEmpty()) {
                    WorkoutSet ws = toWorkoutSet(ex, name);
                    ws.setSession(existing);
                    validateSet(ws);
                    existing.getSets().add(ws);
                    recordAccessoryPr(existing.getUser(), ws);
                }
            }
        }
    }

    private WorkoutSet toWorkoutSet(WorkoutRequest.ExerciseDto ex, String trimmedName) {
        WorkoutSet ws = new WorkoutSet();
        ws.setExerciseName(trimmedName.trim());
        ws.setWeightKg(ex.weightKg());
        ws.setSets(ex.sets());
        ws.setReps(ex.reps());
        ws.setRestSeconds(ex.restSeconds());
        ws.setRpe(ex.rpe());
        ws.setCompletionStatus(ex.completionStatus());
        ws.setActualReps(ex.actualReps());
        ws.setActualWeight(ex.actualWeight());
        return ws;
    }

    // 小動作沒有 1RM 公式可以算重量，訓練紀錄裡填過一次重量就記住，下次課表頁同一個動作會自動帶入。
    // 組數/休息秒數要跟重量一起記，不能只記重量：這是使用者這個週期實際做的內容，跟在 /plan 編輯
    // 彈窗手動存的覆寫（LiftPrService.saveManual）本來就該是同一個記憶，不該是兩條各記一半的資料
    private void recordAccessoryPr(User user, WorkoutSet set) {
        if (user == null || MAIN_LIFT_NAMES.contains(set.getExerciseName())) return;
        Double weight = set.getActualWeight() != null ? set.getActualWeight() : set.getWeightKg();
        if (weight == null || weight <= 0) return;
        Integer reps = set.getActualReps() != null ? set.getActualReps() : set.getReps();
        Integer sets = set.getSets();
        Integer restSeconds = set.getRestSeconds();
        liftPrService.saveManual(user, set.getExerciseName(), weight,
                sets != null ? sets : 3, reps != null ? reps : 8, restSeconds != null ? restSeconds : 90);
    }

    public void delete(Long id, User user) {
        WorkoutSession existing = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("找不到 id=" + id + " 的訓練紀錄"));
        log.info("Deleting workout session id={}", id);
        repository.delete(existing);
        log.info("Deleted workout session id={}", id);
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

    // 正常訓練一天不可能記錄到這種數量的動作——擋住異常大量的請求（不管是打錯還是刻意塞爆），
    // 不讓它一路跑到每個動作都各查一次資料庫、寫一次 DB 的地步。REST API 那邊 WorkoutRequest 已經
    // 用 @Size 擋過一次，但 MVC 表單控制器是自己 zip 平行參數，不會經過 Bean Validation，
    // 這裡是兩條路徑共用的最後一道防線
    private static final int MAX_EXERCISES_PER_SESSION = 50;

    private void validateExerciseCount(List<WorkoutRequest.ExerciseDto> exercises) {
        if (exercises != null && exercises.size() > MAX_EXERCISES_PER_SESSION) {
            throw new IllegalArgumentException("單次訓練最多只能記錄 " + MAX_EXERCISES_PER_SESSION + " 個動作");
        }
    }

    // 重量/組數/次數/RPE 技術上都是合法數字，但負數、0、誇張大的數字（例如打錯多打了幾個 0）
    // 完全不合理，不擋下來的話會悄悄污染歷史紀錄、PR 估算、還有課表頁後續帶入的重量建議
    private void validateSet(WorkoutSet set) {
        String name = set.getExerciseName();
        if (set.getWeightKg() != null && (set.getWeightKg() < 0 || set.getWeightKg() > 500)) {
            throw new IllegalArgumentException("「" + name + "」的重量必須介於 0～500 公斤之間");
        }
        if (set.getActualWeight() != null && (set.getActualWeight() < 0 || set.getActualWeight() > 500)) {
            throw new IllegalArgumentException("「" + name + "」的實際重量必須介於 0～500 公斤之間");
        }
        if (set.getSets() != null && (set.getSets() < 1 || set.getSets() > 20)) {
            throw new IllegalArgumentException("「" + name + "」的組數必須介於 1～20 之間");
        }
        if (set.getReps() != null && (set.getReps() < 1 || set.getReps() > 50)) {
            throw new IllegalArgumentException("「" + name + "」的次數必須介於 1～50 之間");
        }
        if (set.getActualReps() != null && (set.getActualReps() < 0 || set.getActualReps() > 50)) {
            throw new IllegalArgumentException("「" + name + "」的實際完成次數必須介於 0～50 之間");
        }
        if (set.getRestSeconds() != null && (set.getRestSeconds() < 0 || set.getRestSeconds() > 1800)) {
            throw new IllegalArgumentException("「" + name + "」的休息秒數必須介於 0～1800 秒之間");
        }
        if (set.getRpe() != null && (set.getRpe() < 1 || set.getRpe() > 10)) {
            throw new IllegalArgumentException("「" + name + "」的 RPE 必須介於 1～10 之間");
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
        return buildDashboardStats(countThisWeek(), findRecentWithinDays(7), findAll());
    }

    public DashboardStats computeDashboardStats(User user) {
        return buildDashboardStats(countThisWeek(user), findRecentWithinDays(7, user), findAll(user));
    }

    private DashboardStats buildDashboardStats(long weeklyCount, List<WorkoutSession> recentWeekSessions, List<WorkoutSession> all) {
        // 動作名稱 → 分類（COMPOUND / ISOLATION），只計算經典複合式動作（健力三項、引體向上等）的訓練量
        Map<String, String> categoryByExerciseName = exerciseService.findAll().stream()
                .collect(Collectors.toMap(Exercise::getName, Exercise::getCategory, (a, b) -> a));

        Map<String, Double> volumeByBodyPart = new LinkedHashMap<>();
        List<Double> rpes = new ArrayList<>();
        for (WorkoutSession s : recentWeekSessions) {
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

        // all 已依日期降冪排序
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
