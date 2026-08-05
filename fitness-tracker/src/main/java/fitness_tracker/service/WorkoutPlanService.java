package fitness_tracker.service;

import fitness_tracker.entity.Exercise;
import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.repository.ExerciseRepository;
import fitness_tracker.service.SplitCatalog.DaySplitDef;
import fitness_tracker.service.StrengthPrescription.Phase;
import fitness_tracker.service.StrengthPrescription.Prescription;
import fitness_tracker.service.TrainingPlanService.PhaseType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 課表真正「排哪些動作、多重、幾組幾下」的地方：依 SplitCatalog 給的當天篩選條件去 Exercise 表動態選動作，
// 不再像舊版前端那樣把整個動作清單寫死在 JS 陣列裡。主項用 %1RM（StrengthPrescription），配件優先讀使用者
// 存過的重量（LiftPr），一週內同一個配件動作盡量不重複，並依時間預算（約 60 分鐘）決定補幾個配件。
@Service
public class WorkoutPlanService {

    public static final Set<String> MAIN_LIFT_NAMES = Set.of("深蹲", "臥推", "硬舉", "肩推");

    // 新手模式主項起始重量（空槓／輕重量），跟原本前端 MAIN.bar 一致
    private static final Map<String, Integer> MAIN_LIFT_BAR_KG = Map.of(
            "深蹲", 20, "臥推", 20, "硬舉", 40, "肩推", 20
    );

    // 配件動作沒存過重量時的階段預設值（跟原本前端 accWeight 一致）
    private static final Map<String, Integer> ACC_DEFAULT_NOVICE = Map.of(
            "adapt", 10, "hyper", 12, "strength", 15, "deload", 8
    );
    private static final Map<String, Integer> ACC_DEFAULT_VETERAN = Map.of(
            "adapt", 12, "hyper", 20, "strength", 25, "deload", 10
    );

    // 減量週不走 %1RM 反推次數的公式（負荷太輕，公式會反推出不合理的高次數），固定用這組
    private static final double DELOAD_WORK_PCT = 0.525;
    private static final int DELOAD_SETS = 2;
    private static final int DELOAD_REPS_LOW = 5;
    private static final int DELOAD_REPS_HIGH = 6;
    private static final int DELOAD_REST_SECONDS = 60;

    private static final int WARMUP_MIN = 10;
    private static final int SET_WORK_SECONDS = 50;
    private static final int TIME_CAP_MIN = 62;

    private final ExerciseRepository exerciseRepository;
    private final LiftPrService liftPrService;
    private final WorkoutService workoutService;

    public WorkoutPlanService(ExerciseRepository exerciseRepository, LiftPrService liftPrService, WorkoutService workoutService) {
        this.exerciseRepository = exerciseRepository;
        this.liftPrService = liftPrService;
        this.workoutService = workoutService;
    }

    public record PlannedExercise(String name, boolean isMain, double weightKg, int sets,
                                   int repsLow, int repsHigh, int restSeconds, String note) {}

    public record GeneratedDayPlan(String dayName, List<PlannedExercise> exercises, int estimatedMinutes) {}

    // 純「排哪些動作」的結果，不算重量/組數/次數——給前端當即時互動（拖曳、換動作、增減）的資料來源，
    // 重量算法本來就跟前端既有的即時預覽（打 PR 馬上看到建議重量）共用同一套公式，不用為了接後端把那個體驗拿掉
    public record DayComposition(String dayName, List<String> mainNames, List<String> accessoryPool) {}

    public GeneratedDayPlan planDay(User user, DaySplitDef dayDef, PlanMode mode, PhaseType phaseType, boolean deload) {
        Map<String, LiftPr> prByName = prByName(user);
        List<String> mainNames = dayDef.mainLifts();
        List<Exercise> pool = accessoryPoolFor(user, dayDef, mainNames);

        double workPct = deload ? DELOAD_WORK_PCT : (phase(phaseType).pctLo + phase(phaseType).pctHi) / 2.0;
        int mainSets = deload ? DELOAD_SETS : phase(phaseType).sets;
        int restSeconds = deload ? DELOAD_REST_SECONDS : phase(phaseType).restSeconds();
        int[] mainReps = deload ? new int[]{DELOAD_REPS_LOW, DELOAD_REPS_HIGH} : mainRepsRange(phaseType);

        List<PlannedExercise> mainItems = new ArrayList<>();
        double minutesSoFar = WARMUP_MIN;
        for (String name : mainNames) {
            double weight = mainWeight(mode, name, prByName, workPct);
            String note = mode == PlanMode.NOVICE ? "空槓起步"
                    : (prByName.containsKey(name) ? Math.round(workPct * 1000) / 10.0 + "% 1RM" : "尚未輸入 PR，先用空槓");
            mainItems.add(new PlannedExercise(name, true, weight, mainSets, mainReps[0], mainReps[1], restSeconds, note));
            minutesSoFar += mainSets * (SET_WORK_SECONDS + restSeconds) / 60.0;
        }

        int accSets = deload ? 2 : 3;
        int[] accReps = (phaseType == PhaseType.STRENGTH && !deload) ? new int[]{6, 8} : new int[]{10, 15};
        double tpsAcc = SET_WORK_SECONDS + restSeconds;

        List<PlannedExercise> accItems = new ArrayList<>();
        for (Exercise ex : pool) {
            if (minutesSoFar + accSets * tpsAcc / 60.0 > TIME_CAP_MIN) break;
            double weight = accessoryWeight(ex.getName(), mode, phaseType, deload, prByName);
            accItems.add(new PlannedExercise(ex.getName(), false, weight, accSets, accReps[0], accReps[1], restSeconds, "配件 · 感受控制"));
            minutesSoFar += accSets * tpsAcc / 60.0;
        }

        List<PlannedExercise> all = new ArrayList<>(mainItems);
        all.addAll(accItems);
        return new GeneratedDayPlan(dayDef.name(), all, (int) Math.round(minutesSoFar));
    }

    // 本次課表佇列：本週分化裡還沒練過的天，依序排好（跟原本前端 initQueue 一致）
    public List<GeneratedDayPlan> currentQueue(User user, int daysPerWeek, PlanMode mode, PhaseType phaseType, boolean deload) {
        return remainingSplitDays(user, daysPerWeek).stream().map(d -> planDay(user, d, mode, phaseType, deload)).toList();
    }

    // 佇列最後一張課表練完/被移除後，接續分化循環排下一張（跟原本前端 addNextCourse 一致）
    public GeneratedDayPlan nextInCycle(User user, String lastDayName, int daysPerWeek, PlanMode mode, PhaseType phaseType, boolean deload) {
        return planDay(user, nextSplitDay(lastDayName, daysPerWeek), mode, phaseType, deload);
    }

    // 只排動作組成，不算重量——給前端即時互動用，天數以外的任何切換（程度/減量）都不用重打這支
    public DayComposition composeDay(User user, DaySplitDef dayDef) {
        List<String> mainNames = dayDef.mainLifts();
        List<String> accessoryNames = accessoryPoolFor(user, dayDef, mainNames).stream().map(Exercise::getName).toList();
        return new DayComposition(dayDef.name(), mainNames, accessoryNames);
    }

    // 帶週次版本：同一天型態（例如「拉 A」）依「第幾週＋A/B」旋轉配件池，讓週與週、同一週的 A 跟 B 都不會長一樣；
    // 主項完全不受影響（dayDef.mainLifts() 不變），進階追蹤（1RM／PR）需要的主項穩定性不會被打亂
    public DayComposition composeDay(User user, DaySplitDef dayDef, int week) {
        return composeDay(user, dayDef, week, 0);
    }

    // extraOffset：分化天數較少（例如 3 天）時，「新增課表」會在同一週內把整個分化循環繞回第二圈，
    // 此時同一個天名（例如「拉日」，沒有 A/B 可分）在同一週、同一次請求脈絡下光靠 week 轉不出差異，
    // 需要呼叫端（目前是 nextCompositionInCycle）額外帶入「這是第幾張卡片」之類的遞增值，疊加到旋轉量上
    public DayComposition composeDay(User user, DaySplitDef dayDef, int week, int extraOffset) {
        List<String> mainNames = dayDef.mainLifts();
        List<String> accessoryNames = accessoryPoolFor(user, dayDef, mainNames, week, extraOffset).stream().map(Exercise::getName).toList();
        return new DayComposition(dayDef.name(), mainNames, accessoryNames);
    }

    public List<DayComposition> currentQueueComposition(User user, int daysPerWeek) {
        return remainingSplitDays(user, daysPerWeek).stream().map(d -> composeDay(user, d)).toList();
    }

    public List<DayComposition> currentQueueComposition(User user, int daysPerWeek, int week) {
        return remainingSplitDays(user, daysPerWeek).stream().map(d -> composeDay(user, d, week)).toList();
    }

    public DayComposition nextCompositionInCycle(User user, String lastDayName, int daysPerWeek) {
        return composeDay(user, nextSplitDay(lastDayName, daysPerWeek));
    }

    public DayComposition nextCompositionInCycle(User user, String lastDayName, int daysPerWeek, int week) {
        return composeDay(user, nextSplitDay(lastDayName, daysPerWeek), week);
    }

    // extraOffset 由前端傳「目前佇列已經有幾張卡」——每加一張就遞增，確保分化循環繞第二圈時
    // 就算天名（無A/B）、週次都跟第一圈的那張卡一樣，配件也還是會轉到不同位置，不會一模一樣
    public DayComposition nextCompositionInCycle(User user, String lastDayName, int daysPerWeek, int week, int extraOffset) {
        return composeDay(user, nextSplitDay(lastDayName, daysPerWeek), week, extraOffset);
    }

    private List<DaySplitDef> remainingSplitDays(User user, int daysPerWeek) {
        List<DaySplitDef> baseSplit = SplitCatalog.forDays(daysPerWeek);
        Set<String> completed = workoutService.completedBodyPartsThisWeek(user);
        List<DaySplitDef> remaining = baseSplit.stream().filter(d -> !completed.contains(d.name())).toList();
        return remaining.isEmpty() ? List.of(baseSplit.get(0)) : remaining;
    }

    private DaySplitDef nextSplitDay(String lastDayName, int daysPerWeek) {
        List<DaySplitDef> baseSplit = SplitCatalog.forDays(daysPerWeek);
        int idx = -1;
        for (int i = 0; i < baseSplit.size(); i++) {
            if (baseSplit.get(i).name().equals(lastDayName)) { idx = i; break; }
        }
        if (idx < 0) idx = baseSplit.size() - 1;
        return baseSplit.get((idx + 1) % baseSplit.size());
    }

    private Map<String, LiftPr> prByName(User user) {
        return liftPrService.findByUser(user).stream()
                .collect(Collectors.toMap(LiftPr::getExerciseName, Function.identity(), (a, b) -> a));
    }

    // 複合先、孤立後；主項另外處理所以要排除；一週內優先排沒練過的，都練過了才放寬重複（寧可重複也不開天窗）
    // 沒有 week 的版本：固定順序、不旋轉——給 planDay／舊呼叫端用，重量流程完全不動
    private List<Exercise> accessoryPoolFor(User user, DaySplitDef dayDef, List<String> mainNames) {
        Set<String> usedThisWeek = workoutService.exerciseNamesThisWeek(user);
        List<Exercise> compounds = candidatesFor(dayDef, "COMPOUND");
        List<Exercise> isolations = candidatesFor(dayDef, "ISOLATION");
        List<Exercise> accessoryPool = new ArrayList<>();
        compounds.stream().filter(e -> !mainNames.contains(e.getName())).forEach(accessoryPool::add);
        isolations.stream().filter(e -> !mainNames.contains(e.getName())).forEach(accessoryPool::add);

        List<Exercise> preferred = accessoryPool.stream().filter(e -> !usedThisWeek.contains(e.getName())).toList();
        return preferred.isEmpty() ? accessoryPool : preferred;
    }

    // 帶週次版本：複合子池、孤立子池「各自」旋轉再接起來（複合仍在前、孤立仍在後），
    // 旋轉量 = (week-1) + extraOffset + 天名結尾是 B 的話再加半圈（該子池大小的一半）——
    // 同一週同一天型態穩定、換週或換 A/B 就會轉出不同的起點，extraOffset 讓分化循環繞第二圈時
    // 就算天名（無A/B）跟週次都相同也還是會轉出不同結果；主項（mainNames）完全不受影響
    private List<Exercise> accessoryPoolFor(User user, DaySplitDef dayDef, List<String> mainNames, int week, int extraOffset) {
        Set<String> usedThisWeek = workoutService.exerciseNamesThisWeek(user);
        List<Exercise> compounds = candidatesFor(dayDef, "COMPOUND").stream()
                .filter(e -> !mainNames.contains(e.getName())).toList();
        List<Exercise> isolations = candidatesFor(dayDef, "ISOLATION").stream()
                .filter(e -> !mainNames.contains(e.getName())).toList();

        int weekOffset = Math.max(week, 1) - 1 + extraOffset;
        boolean isB = dayDef.name() != null && dayDef.name().endsWith("B");

        List<Exercise> accessoryPool = new ArrayList<>(rotate(compounds, weekOffset, isB));
        accessoryPool.addAll(rotate(isolations, weekOffset, isB));

        List<Exercise> preferred = accessoryPool.stream().filter(e -> !usedThisWeek.contains(e.getName())).toList();
        return preferred.isEmpty() ? accessoryPool : preferred;
    }

    private static <T> List<T> rotate(List<T> list, int weekOffset, boolean addHalfTurnForB) {
        int n = list.size();
        if (n == 0) return list;
        int offset = weekOffset + (addHalfTurnForB ? n / 2 : 0);
        int shift = ((offset % n) + n) % n;
        if (shift == 0) return list;
        List<T> rotated = new ArrayList<>(n);
        for (int i = 0; i < n; i++) rotated.add(list.get((i + shift) % n));
        return rotated;
    }

    private List<Exercise> candidatesFor(DaySplitDef dayDef, String category) {
        List<Exercise> result = new ArrayList<>();
        if (!dayDef.movements().isEmpty()) {
            for (String movement : dayDef.movements()) {
                result.addAll(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc(movement, category));
            }
        } else {
            for (String bodyPart : dayDef.bodyParts()) {
                result.addAll(exerciseRepository.findByBodyPartAndCategoryOrderByOrderIndexAscNameAsc(bodyPart, category));
            }
        }
        return result;
    }

    private double mainWeight(PlanMode mode, String liftName, Map<String, LiftPr> prByName, double workPct) {
        int barKg = MAIN_LIFT_BAR_KG.getOrDefault(liftName, 20);
        if (mode == PlanMode.NOVICE) return barKg;
        LiftPr pr = prByName.get(liftName);
        if (pr == null || pr.getOneRepMax() == null) return barKg;
        return roundToPlate(pr.getOneRepMax() * workPct);
    }

    private double accessoryWeight(String name, PlanMode mode, PhaseType phaseType, boolean deload, Map<String, LiftPr> prByName) {
        LiftPr pr = prByName.get(name);
        if (pr != null && pr.getWeightKg() != null && pr.getWeightKg() > 0) return pr.getWeightKg();
        String key = deload ? "deload" : phaseKey(phaseType);
        Map<String, Integer> table = mode == PlanMode.NOVICE ? ACC_DEFAULT_NOVICE : ACC_DEFAULT_VETERAN;
        return table.get(key);
    }

    private static double roundToPlate(double weight) {
        return Math.round(weight / 2.5) * 2.5;
    }

    private static String phaseKey(PhaseType phaseType) {
        return switch (phaseType) {
            case ADAPT -> "adapt";
            case HYPER -> "hyper";
            case STRENGTH -> "strength";
        };
    }

    private static Phase phase(PhaseType phaseType) {
        return switch (phaseType) {
            case ADAPT -> Phase.ANATOMICAL_ADAPTATION;
            case HYPER -> Phase.HYPERTROPHY;
            case STRENGTH -> Phase.MAX_STRENGTH;
        };
    }

    // 主項次數區間沿用 StrengthPrescription 的 %1RM↔RIR 反推公式（用 100 當佔位 1RM，只取次數區間，重量另外算）
    private static int[] mainRepsRange(PhaseType phaseType) {
        Prescription rx = StrengthPrescription.prescribe(100.0, phase(phaseType));
        return new int[]{rx.repsLow(), rx.repsHigh()};
    }
}
