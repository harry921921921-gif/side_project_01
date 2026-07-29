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

    private static final Set<String> MAIN_LIFT_NAMES = Set.of("深蹲", "臥推", "硬舉", "肩推");

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

    public GeneratedDayPlan planDay(User user, DaySplitDef dayDef, PlanMode mode, PhaseType phaseType, boolean deload) {
        Map<String, LiftPr> prByName = liftPrService.findByUser(user).stream()
                .collect(Collectors.toMap(LiftPr::getExerciseName, Function.identity(), (a, b) -> a));
        Set<String> usedThisWeek = workoutService.exerciseNamesThisWeek(user);

        double workPct = deload ? DELOAD_WORK_PCT : (phase(phaseType).pctLo + phase(phaseType).pctHi) / 2.0;
        int mainSets = deload ? DELOAD_SETS : phase(phaseType).sets;
        int restSeconds = deload ? DELOAD_REST_SECONDS : phase(phaseType).restSeconds();
        int[] mainReps = deload ? new int[]{DELOAD_REPS_LOW, DELOAD_REPS_HIGH} : mainRepsRange(phaseType);

        List<String> mainNames = dayDef.mainLifts();
        List<PlannedExercise> mainItems = new ArrayList<>();
        double minutesSoFar = WARMUP_MIN;
        for (String name : mainNames) {
            double weight = mainWeight(mode, name, prByName, workPct);
            String note = mode == PlanMode.NOVICE ? "空槓起步"
                    : (prByName.containsKey(name) ? Math.round(workPct * 1000) / 10.0 + "% 1RM" : "尚未輸入 PR，先用空槓");
            mainItems.add(new PlannedExercise(name, true, weight, mainSets, mainReps[0], mainReps[1], restSeconds, note));
            minutesSoFar += mainSets * (SET_WORK_SECONDS + restSeconds) / 60.0;
        }

        List<Exercise> compounds = candidatesFor(dayDef, "COMPOUND");
        List<Exercise> isolations = candidatesFor(dayDef, "ISOLATION");
        List<Exercise> accessoryPool = new ArrayList<>();
        compounds.stream().filter(e -> !mainNames.contains(e.getName())).forEach(accessoryPool::add);
        isolations.stream().filter(e -> !mainNames.contains(e.getName())).forEach(accessoryPool::add);

        List<Exercise> preferred = accessoryPool.stream().filter(e -> !usedThisWeek.contains(e.getName())).toList();
        List<Exercise> pool = preferred.isEmpty() ? accessoryPool : preferred;

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
        List<DaySplitDef> baseSplit = SplitCatalog.forDays(daysPerWeek);
        Set<String> completed = workoutService.completedBodyPartsThisWeek(user);
        List<DaySplitDef> remaining = baseSplit.stream().filter(d -> !completed.contains(d.name())).toList();
        if (remaining.isEmpty()) remaining = List.of(baseSplit.get(0));
        return remaining.stream().map(d -> planDay(user, d, mode, phaseType, deload)).toList();
    }

    // 佇列最後一張課表練完/被移除後，接續分化循環排下一張（跟原本前端 addNextCourse 一致）
    public GeneratedDayPlan nextInCycle(User user, String lastDayName, int daysPerWeek, PlanMode mode, PhaseType phaseType, boolean deload) {
        List<DaySplitDef> baseSplit = SplitCatalog.forDays(daysPerWeek);
        int idx = -1;
        for (int i = 0; i < baseSplit.size(); i++) {
            if (baseSplit.get(i).name().equals(lastDayName)) { idx = i; break; }
        }
        if (idx < 0) idx = baseSplit.size() - 1;
        DaySplitDef next = baseSplit.get((idx + 1) % baseSplit.size());
        return planDay(user, next, mode, phaseType, deload);
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
