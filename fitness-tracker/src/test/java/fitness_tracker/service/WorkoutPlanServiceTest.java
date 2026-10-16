package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
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

    @Mock
    private ExerciseService exerciseService;

    @InjectMocks
    private WorkoutPlanService service;

    private final User user = new User();

    // 這個測試檔全部在測排課邏輯本身（分化/旋轉/去重/重量），不是在測「個人自訂動作誰看得到」
    // （那個由 ExerciseServiceTest 覆蓋），這裡一律當作看得到，才不會每個既有案例都要重新配置
    @BeforeEach
    void stubAllExercisesVisible() {
        lenient().when(exerciseService.visibleTo(any(), any())).thenReturn(true);
    }

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

    // 拉日候選池：主項「硬舉」也在 COMPOUND 清單裡（會被濾掉），剩 1 個複合＋4 個孤立，
    // 池子夠大才看得出旋轉的效果（複合池只有1個會一直不動，剛好拿來驗證「複合先孤立後」的順序沒被打亂）
    private void stubPullDayWithBiggerPool() {
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PULL", "COMPOUND"))
                .thenReturn(List.of(ex("硬舉", "背", "COMPOUND", "PULL"), ex("槓鈴划船", "背", "COMPOUND", "PULL")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PULL", "ISOLATION"))
                .thenReturn(List.of(
                        ex("滑輪下拉", "背", "ISOLATION", "PULL"), ex("坐姿划船", "背", "ISOLATION", "PULL"),
                        ex("二頭彎舉", "手臂", "ISOLATION", "PULL"), ex("面拉", "肩", "ISOLATION", "PULL")
                ));
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

    // ===== 週次 + A/B 配件輪替：composeDay(user, dayDef, week) =====

    @Test
    void composeDayWithWeekIsStableForRepeatedCallsSameWeek() {
        stubPullDayWithBiggerPool();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pullA = DaySplitDef.byMovement("拉 A", List.of("硬舉"), "PULL");

        WorkoutPlanService.DayComposition first = service.composeDay(user, pullA, 3);
        WorkoutPlanService.DayComposition second = service.composeDay(user, pullA, 3);

        assertEquals(first.accessoryPool(), second.accessoryPool(), "同一週重複呼叫應該得到一樣的順序，不能每次隨機");
    }

    @Test
    void composeDayWithWeekRotatesIsolationSubPoolByWeekOffsetExactly() {
        stubPullDayWithBiggerPool();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pullA = DaySplitDef.byMovement("拉 A", List.of("硬舉"), "PULL");

        WorkoutPlanService.DayComposition week1 = service.composeDay(user, pullA, 1);
        WorkoutPlanService.DayComposition week2 = service.composeDay(user, pullA, 2);

        // 硬舉是主項被濾掉，複合池只剩「槓鈴划船」（size=1，永遠轉不動）；
        // 孤立池 4 個，week1 offset=0 不轉，week2 offset=1 往前轉一格
        assertEquals(List.of("槓鈴划船", "滑輪下拉", "坐姿划船", "二頭彎舉", "面拉"), week1.accessoryPool());
        assertEquals(List.of("槓鈴划船", "坐姿划船", "二頭彎舉", "面拉", "滑輪下拉"), week2.accessoryPool());
        assertNotEquals(week1.accessoryPool(), week2.accessoryPool(), "換週配件順序應該不同");
    }

    @Test
    void composeDayWithWeekDiffersBetweenAAndBSameWeekButMainNamesStaySame() {
        stubPullDayWithBiggerPool();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pullA = DaySplitDef.byMovement("拉 A", List.of("硬舉"), "PULL");
        DaySplitDef pullB = DaySplitDef.byMovement("拉 B", List.of("硬舉"), "PULL");

        WorkoutPlanService.DayComposition a = service.composeDay(user, pullA, 1);
        WorkoutPlanService.DayComposition b = service.composeDay(user, pullB, 1);

        // B 天孤立池額外加半圈（4/2=2 格）：offset=0+2=2
        assertEquals(List.of("槓鈴划船", "二頭彎舉", "面拉", "滑輪下拉", "坐姿划船"), b.accessoryPool());
        assertNotEquals(a.accessoryPool(), b.accessoryPool(), "同一週 A 跟 B 配件順序應該不同");
        assertEquals(a.mainNames(), b.mainNames(), "主項不受 A/B 輪替影響，進階追蹤穩定");
        assertEquals(List.of("硬舉"), a.mainNames());
    }

    @Test
    void composeDayWithWeekKeepsCompoundBeforeIsolationOrderingAfterRotation() {
        stubPullDayWithBiggerPool();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        DaySplitDef pullB = DaySplitDef.byMovement("拉 B", List.of("硬舉"), "PULL");

        WorkoutPlanService.DayComposition week4 = service.composeDay(user, pullB, 4);

        assertEquals("槓鈴划船", week4.accessoryPool().get(0), "旋轉後複合動作仍然排在孤立動作前面");
    }

    @Test
    void composeDayWithWeekStillAppliesWeeklyDedupPreference() {
        stubPullDayWithBiggerPool();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of("坐姿划船"));
        DaySplitDef pullA = DaySplitDef.byMovement("拉 A", List.of("硬舉"), "PULL");

        WorkoutPlanService.DayComposition week1 = service.composeDay(user, pullA, 1);

        assertFalse(week1.accessoryPool().contains("坐姿划船"), "本週已練過的配件依然要被排除，輪替不影響週去重");
    }

    @Test
    void currentQueueCompositionWithWeekPassesWeekToEachDay() {
        // 1 天分化只有「全身」（用 bodyPart 篩選，不牽涉 movement stub），
        // 確認帶 week 的多載能正常跑完，回傳的就是 SplitCatalog.forDays(1) 那唯一一天
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());

        List<WorkoutPlanService.DayComposition> queue = service.currentQueueComposition(user, 1, 2);

        assertEquals(1, queue.size());
        assertEquals("全身", queue.get(0).dayName());
    }

    @Test
    void nextCompositionInCycleWithWeekAppliesRotationToTheNextDay() {
        // 從「腿日」的下一個算起會繞回「推日」（PUSH），只需要 stub PUSH
        stubPushDay();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());

        WorkoutPlanService.DayComposition next = service.nextCompositionInCycle(user, "腿日", 3, 5);

        assertEquals("推日", next.dayName());
    }

    // ===== extraOffset：分化天數少時「新增課表」在同一週繞回第二圈，同一天名（無A/B）也要轉出不同結果 =====

    @Test
    void composeDayWithExtraOffsetDiffersFromWithoutEvenAtSameWeekAndSameDayName() {
        stubPullDayWithBiggerPool();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        // 「拉日」沒有 A/B 尾字，模擬 3 天分化這種沒有 A/B 可分的天名
        DaySplitDef pullDay = DaySplitDef.byMovement("拉日", List.of("硬舉"), "PULL");

        WorkoutPlanService.DayComposition firstLap = service.composeDay(user, pullDay, 2, 0);
        WorkoutPlanService.DayComposition secondLap = service.composeDay(user, pullDay, 2, 3);

        assertNotEquals(firstLap.accessoryPool(), secondLap.accessoryPool(),
                "同一週、同一個沒有A/B的天名，光靠 extraOffset 不同也要轉出不同配件順序");
        assertEquals(firstLap.mainNames(), secondLap.mainNames(), "主項不受 extraOffset 影響");
    }

    @Test
    void nextCompositionInCycleWithExtraOffsetMakesSecondLapDifferFromFirstLap() {
        // 模擬使用者把 3 天分化的「新增課表」按到分化繞回第二圈：從「腿日」的下一個算起會繞回「推日」，
        // 這正是螢幕截圖回報的情境——同一週、同一個「推日」，只因為是第二次出現，配件也該不一樣
        stubPushDay();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());

        // extra=0 對應第一圈第一次出現「推日」；extra=3（佇列已經有3張卡，模擬繞完一圈後又加一張）對應第二圈
        WorkoutPlanService.DayComposition firstLapPush = service.nextCompositionInCycle(user, "腿日", 3, 8, 0);
        WorkoutPlanService.DayComposition secondLapPush = service.nextCompositionInCycle(user, "腿日", 3, 8, 3);

        assertEquals("推日", firstLapPush.dayName());
        assertEquals("推日", secondLapPush.dayName());
        assertNotEquals(firstLapPush.accessoryPool(), secondLapPush.accessoryPool(),
                "同一週繞第二圈遇到同一個天名，配件不該跟第一圈一模一樣");
    }

    // ===== extraQueueCount：重新登入時把「新增課表」多排出來、已持久化的張數重建回佇列 =====

    private void stubAllThreeMovementsForThreeDaySplit() {
        stubPushDay();
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PULL", "COMPOUND"))
                .thenReturn(List.of(ex("硬舉", "背", "COMPOUND", "PULL")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("PULL", "ISOLATION"))
                .thenReturn(List.of(ex("滑輪下拉", "背", "ISOLATION", "PULL")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("LEGS", "COMPOUND"))
                .thenReturn(List.of(ex("深蹲", "腿", "COMPOUND", "LEGS")));
        when(exerciseRepository.findByMovementAndCategoryOrderByOrderIndexAscNameAsc("LEGS", "ISOLATION"))
                .thenReturn(List.of(ex("腿彎舉", "腿", "ISOLATION", "LEGS")));
    }

    @Test
    void currentQueueCompositionWithExtraQueueCountAppendsPersistedExtraCards() {
        stubAllThreeMovementsForThreeDaySplit();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        when(workoutService.completedBodyPartsThisWeek(user)).thenReturn(Set.of());

        List<WorkoutPlanService.DayComposition> queue = service.currentQueueComposition(user, 3, 8, 2);

        List<String> names = queue.stream().map(WorkoutPlanService.DayComposition::dayName).toList();
        assertEquals(List.of("推日", "拉日", "腿日", "推日", "拉日"), names,
                "3 天基本分化 + 持久化的 2 張延伸卡片，應該接續分化循環（推->拉）");
    }

    @Test
    void currentQueueCompositionWithExtraQueueCountRotatesRepeatedDayNameDifferently() {
        stubAllThreeMovementsForThreeDaySplit();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        when(workoutService.completedBodyPartsThisWeek(user)).thenReturn(Set.of());

        // extraQueueCount=3 剛好繞完一整圈，第 4 張（index 3）又是「推日」，應該跟第 1 張的推日配件不同
        List<WorkoutPlanService.DayComposition> queue = service.currentQueueComposition(user, 3, 8, 3);

        assertEquals(6, queue.size());
        assertEquals("推日", queue.get(0).dayName());
        assertEquals("推日", queue.get(3).dayName());
        assertNotEquals(queue.get(0).accessoryPool(), queue.get(3).accessoryPool(),
                "重建佇列時，繞第二圈的同一天名也要轉出不同配件，不能重建成一模一樣");
    }

    @Test
    void currentQueueCompositionWithZeroExtraQueueCountMatchesThreeArgOverload() {
        stubAllThreeMovementsForThreeDaySplit();
        when(workoutService.exerciseNamesThisWeek(user)).thenReturn(Set.of());
        when(workoutService.completedBodyPartsThisWeek(user)).thenReturn(Set.of());

        List<WorkoutPlanService.DayComposition> withZeroExtra = service.currentQueueComposition(user, 3, 8, 0);
        List<WorkoutPlanService.DayComposition> withoutExtraParam = service.currentQueueComposition(user, 3, 8);

        assertEquals(withoutExtraParam, withZeroExtra, "extraQueueCount=0 應該跟原本三參數版本結果一致");
    }
}
