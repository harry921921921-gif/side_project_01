package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fitness_tracker.entity.Exercise;
import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.repository.ExerciseRepository;
import fitness_tracker.service.SplitCatalog.DaySplitDef;
import fitness_tracker.service.TrainingPlanService.PhaseType;
import fitness_tracker.service.WorkoutPlanService.GeneratedDayPlan;
import fitness_tracker.service.WorkoutPlanService.PlannedExercise;

@ExtendWith(MockitoExtension.class)
class WorkoutPlanServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private LiftPrService liftPrService;

    @Mock
    private WorkoutService workoutService;

    @InjectMocks
    private WorkoutPlanService service;

    private final User user = new User();

    private static Exercise ex(String name, String bodyPart, String category, String movement) {
        Exercise e = new Exercise(name, bodyPart, category);
        e.setMovement(movement);
        return e;
    }

    private void stubPushDay() {
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PUSH", "COMPOUND"))
                .thenReturn(List.of(ex("臥推", "胸", "COMPOUND", "PUSH"), ex("肩推", "肩", "COMPOUND", "PUSH")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PUSH", "ISOLATION"))
                .thenReturn(List.of(ex("側平舉", "肩", "ISOLATION", "PUSH"), ex("三頭下壓", "手臂", "ISOLATION", "PUSH")));
    }

    @Test
    void pushDayOnlyContainsPushMovementExercises() {
        stubPushDay();
        when(liftPrService.findByUser(user)).thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.NOVICE, PhaseType.ADAPT, false);

        List<String> names = plan.exercises().stream().map(PlannedExercise::name).toList();
        assertTrue(names.contains("臥推"));
        assertTrue(names.contains("肩推"));
        assertFalse(names.contains("硬舉"), "拉的動作不該出現在推日");
        assertFalse(names.contains("深蹲"), "腿的動作不該出現在推日");
    }

    @Test
    void noviceModeUsesFixedBarWeightForMainLifts() {
        stubPushDay();
        when(liftPrService.findByUser(user)).thenReturn(List.of()); // 沒存過 PR
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.NOVICE, PhaseType.ADAPT, false);

        PlannedExercise benchPress = plan.exercises().stream().filter(e -> e.name().equals("臥推")).findFirst().orElseThrow();
        assertEquals(20.0, benchPress.weightKg());
        assertTrue(benchPress.isMain());
    }

    @Test
    void veteranModeComputesWeightFromOneRepMax() {
        stubPushDay();
        LiftPr pr = new LiftPr();
        pr.setExerciseName("臥推");
        pr.setOneRepMax(100.0);
        when(liftPrService.findByUser(user)).thenReturn(List.of(pr));
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        // adapt 期 workPct = (0.62+0.68)/2 = 0.65 -> 100*0.65=65，剛好落在 2.5 的倍數上
        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.VETERAN, PhaseType.ADAPT, false);

        PlannedExercise benchPress = plan.exercises().stream().filter(e -> e.name().equals("臥推")).findFirst().orElseThrow();
        assertEquals(65.0, benchPress.weightKg());
    }

    @Test
    void veteranModeFallsBackToBarWeightWhenNoPrSavedYet() {
        stubPushDay();
        when(liftPrService.findByUser(user)).thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.VETERAN, PhaseType.ADAPT, false);

        PlannedExercise benchPress = plan.exercises().stream().filter(e -> e.name().equals("臥推")).findFirst().orElseThrow();
        assertEquals(20.0, benchPress.weightKg());
        assertTrue(benchPress.note().contains("尚未輸入"));
    }

    @Test
    void accessoryWeightPrefersSavedLiftPrOverPhaseDefault() {
        stubPushDay();
        LiftPr savedAcc = new LiftPr();
        savedAcc.setExerciseName("側平舉");
        savedAcc.setWeightKg(11.0);
        when(liftPrService.findByUser(user)).thenReturn(List.of(savedAcc));
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.NOVICE, PhaseType.ADAPT, false);

        PlannedExercise lateralRaise = plan.exercises().stream().filter(e -> e.name().equals("側平舉")).findFirst().orElseThrow();
        assertEquals(11.0, lateralRaise.weightKg());
    }

    @Test
    void weeklyDedupExcludesAlreadyLoggedAccessoriesButKeepsMainLifts() {
        stubPushDay();
        when(liftPrService.findByUser(user)).thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of("側平舉"));
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.NOVICE, PhaseType.ADAPT, false);

        List<String> names = plan.exercises().stream().map(PlannedExercise::name).toList();
        assertFalse(names.contains("側平舉"), "本週已經練過的配件動作應該被排除");
        assertTrue(names.contains("三頭下壓"), "還沒練過的配件動作應該被排進來遞補");
        assertTrue(names.contains("臥推"), "主項不受週去重影響，還是要出現");
    }

    @Test
    void weeklyDedupFallsBackToFullPoolWhenEverythingAlreadyUsed() {
        stubPushDay();
        when(liftPrService.findByUser(user)).thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of("側平舉", "三頭下壓"));
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.NOVICE, PhaseType.ADAPT, false);

        List<String> names = plan.exercises().stream().map(PlannedExercise::name).toList();
        assertTrue(names.contains("側平舉") || names.contains("三頭下壓"),
                "配件動作池都用過時，寧可重複也不要開天窗");
    }

    @Test
    void bodyPartSplitDayOnlyContainsThatBodyPartExercises() {
        when(exerciseRepository.findByBodyPartAndCategoryOrderByOrderIndexAscNameAsc("胸", "COMPOUND"))
                .thenReturn(List.of(ex("臥推", "胸", "COMPOUND", "PUSH"), ex("上斜臥推", "胸", "COMPOUND", "PUSH")));
        when(exerciseRepository.findByBodyPartAndCategoryOrderByOrderIndexAscNameAsc("胸", "ISOLATION"))
                .thenReturn(List.of(ex("飛鳥", "胸", "ISOLATION", "PUSH")));
        when(liftPrService.findByUser(user)).thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef chestDay = DaySplitDef.byBodyPart("胸", List.of("臥推"), "胸");

        GeneratedDayPlan plan = service.planDay(user, chestDay, PlanMode.NOVICE, PhaseType.HYPER, false);

        List<String> names = plan.exercises().stream().map(PlannedExercise::name).toList();
        assertTrue(names.contains("臥推"));
        assertTrue(names.contains("上斜臥推") || names.contains("飛鳥"));
        assertEquals(1, plan.exercises().stream().filter(PlannedExercise::isMain).count());
    }

    @Test
    void deloadUsesLightWeightAndLowSetsRegardlessOfPhase() {
        stubPushDay();
        LiftPr pr = new LiftPr();
        pr.setExerciseName("臥推");
        pr.setOneRepMax(100.0);
        when(liftPrService.findByUser(user)).thenReturn(List.of(pr));
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.VETERAN, PhaseType.STRENGTH, true);

        PlannedExercise benchPress = plan.exercises().stream().filter(e -> e.name().equals("臥推")).findFirst().orElseThrow();
        assertEquals(52.5, benchPress.weightKg()); // 100 * 0.525 = 52.5
        assertEquals(2, benchPress.sets());
        assertEquals(5, benchPress.repsLow());
        assertEquals(6, benchPress.repsHigh());
    }

    @Test
    void timeBudgetCapsNumberOfAccessoriesAroundOneHour() {
        List<Exercise> manyIsolations = List.of(
                ex("側平舉", "肩", "ISOLATION", "PUSH"), ex("三頭下壓", "手臂", "ISOLATION", "PUSH"),
                ex("飛鳥", "胸", "ISOLATION", "PUSH"), ex("前平舉", "肩", "ISOLATION", "PUSH"),
                ex("三頭伸展", "手臂", "ISOLATION", "PUSH"), ex("聳肩", "肩", "ISOLATION", "PUSH"),
                ex("纜繩夾胸", "胸", "ISOLATION", "PUSH"), ex("蝴蝶機夾胸", "胸", "ISOLATION", "PUSH"),
                ex("阿諾德推舉", "肩", "ISOLATION", "PUSH"), ex("繩索側舉", "肩", "ISOLATION", "PUSH")
        );
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PUSH", "COMPOUND"))
                .thenReturn(List.of(ex("臥推", "胸", "COMPOUND", "PUSH")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PUSH", "ISOLATION"))
                .thenReturn(manyIsolations);
        when(liftPrService.findByUser(user)).thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推"), "PUSH");

        GeneratedDayPlan plan = service.planDay(user, pushDay, PlanMode.NOVICE, PhaseType.HYPER, false);

        long accessoryCount = plan.exercises().stream().filter(e -> !e.isMain()).count();
        assertTrue(accessoryCount < manyIsolations.size(), "時間預算應該擋住，不會把全部 10 個配件都排進去");
        assertTrue(plan.estimatedMinutes() <= 70, "總時間應該接近 60 分鐘預算，不會無限往上加");
    }

    @Test
    void currentQueueFiltersOutSplitDaysAlreadyCompletedThisWeek() {
        // 推日已完成、被排除在佇列外，所以不會呼叫 planDay 產生推日課表，這裡不用（也不能）stub PUSH movement
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PULL", "COMPOUND"))
                .thenReturn(List.of(ex("硬舉", "背", "COMPOUND", "PULL")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PULL", "ISOLATION"))
                .thenReturn(List.of());
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("LEGS", "COMPOUND"))
                .thenReturn(List.of(ex("深蹲", "腿", "COMPOUND", "LEGS")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("LEGS", "ISOLATION"))
                .thenReturn(List.of());
        when(liftPrService.findByUser(user)).thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        when(workoutService.completedBodyPartsThisWeek(user)).thenReturn(Set.of("推日"));

        List<GeneratedDayPlan> queue = service.currentQueue(user, 3, PlanMode.NOVICE, PhaseType.ADAPT, false);

        List<String> dayNames = queue.stream().map(GeneratedDayPlan::dayName).toList();
        assertFalse(dayNames.contains("推日"), "已完成的分化天應該從佇列移除");
        assertTrue(dayNames.contains("拉日"));
        assertTrue(dayNames.contains("腿日"));
    }

    @Test
    void nextInCycleWrapsAroundToFirstDayAfterLast() {
        // 從「腿日」的下一個算起，會繞回分化第一天「推日」（PUSH），只需要 stub PUSH
        stubPushDay();
        when(liftPrService.findByUser(user)).thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());

        GeneratedDayPlan next = service.nextInCycle(user, "腿日", 3, PlanMode.NOVICE, PhaseType.ADAPT, false);

        assertEquals("推日", next.dayName());
    }

    // ===== DayComposition：/plan 頁課表卡片即時互動用（只給動作名稱，不算重量）=====

    @Test
    void composeDaySeparatesMainAndAccessoryNamesWithoutComputingWeight() {
        stubPushDay();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        WorkoutPlanService.DayComposition dc = service.composeDay(user, pushDay);

        assertEquals("推日", dc.dayName());
        assertEquals(List.of("臥推", "肩推"), dc.mainNames());
        assertTrue(dc.accessoryPool().contains("側平舉"));
        assertTrue(dc.accessoryPool().contains("三頭下壓"));
        assertFalse(dc.accessoryPool().contains("臥推"), "主項不該又出現在配件池裡");
    }

    @Test
    void composeDayAppliesWeeklyDedupJustLikeFullPlanDay() {
        stubPushDay();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of("側平舉"));
        DaySplitDef pushDay = DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH");

        WorkoutPlanService.DayComposition dc = service.composeDay(user, pushDay);

        assertFalse(dc.accessoryPool().contains("側平舉"), "本週已練過的配件不該出現在候選池");
        assertTrue(dc.accessoryPool().contains("三頭下壓"));
    }

    @Test
    void currentQueueCompositionFiltersOutCompletedDaysThisWeek() {
        // 推日已完成、被排除在佇列外，所以不會呼叫 composeDay 產生推日的組成，這裡不用（也不能）stub PUSH movement
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PULL", "COMPOUND"))
                .thenReturn(List.of(ex("硬舉", "背", "COMPOUND", "PULL")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PULL", "ISOLATION"))
                .thenReturn(List.of());
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("LEGS", "COMPOUND"))
                .thenReturn(List.of(ex("深蹲", "腿", "COMPOUND", "LEGS")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("LEGS", "ISOLATION"))
                .thenReturn(List.of());
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        when(workoutService.completedBodyPartsThisWeek(user)).thenReturn(Set.of("推日"));

        List<WorkoutPlanService.DayComposition> queue = service.currentQueueComposition(user, 3);

        List<String> dayNames = queue.stream().map(WorkoutPlanService.DayComposition::dayName).toList();
        assertFalse(dayNames.contains("推日"));
        assertTrue(dayNames.contains("拉日"));
        assertTrue(dayNames.contains("腿日"));
    }

    @Test
    void nextCompositionInCycleWrapsAroundJustLikeNextInCycle() {
        stubPushDay();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());

        WorkoutPlanService.DayComposition next = service.nextCompositionInCycle(user, "腿日", 3);

        assertEquals("推日", next.dayName());
    }
}
