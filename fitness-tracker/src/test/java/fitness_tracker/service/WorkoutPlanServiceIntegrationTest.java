package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.repository.UserRepository;
import fitness_tracker.service.SplitCatalog.DaySplitDef;
import fitness_tracker.service.TrainingPlanService.PhaseType;
import fitness_tracker.service.WorkoutPlanService.GeneratedDayPlan;
import fitness_tracker.service.WorkoutPlanService.PlannedExercise;

// 真的接資料庫的整合測試（不 mock ExerciseRepository）：證明 WorkoutPlanService 對著實際重新命名過的
// Exercise 表也能選出正確、乾淨（不跨分化污染）的課表。@Transactional 讓寫入的測試帳號在測試結束後自動回滾，
// 不會留垃圾資料在資料庫。
@SpringBootTest
@Transactional
class WorkoutPlanServiceIntegrationTest {

    @Autowired
    private WorkoutPlanService workoutPlanService;

    @Autowired
    private UserRepository userRepository;

    private User newTestUser() {
        User u = new User();
        u.setEmail("integration-test-" + System.nanoTime() + "@example.com");
        u.setPasswordHash("x");
        u.setDisplayName("Integration Test");
        return userRepository.save(u);
    }

    @Test
    void pushDayPlanUsesRealExerciseTableAndStaysWithinPushMovement() {
        User user = newTestUser();
        DaySplitDef pushDay = SplitCatalog.forDays(3).get(0); // 推日

        GeneratedDayPlan plan = workoutPlanService.planDay(user, pushDay, PlanMode.NOVICE, PhaseType.ADAPT, false);

        assertEquals("推日", plan.dayName());
        assertFalse(plan.exercises().isEmpty());
        List<String> names = plan.exercises().stream().map(PlannedExercise::name).toList();
        assertTrue(names.contains("臥推"));
        assertTrue(names.contains("肩推"));
        assertFalse(names.contains("硬舉"), "拉的動作不該混進推日");
        assertFalse(names.contains("深蹲"), "腿的動作不該混進推日");
        assertTrue(plan.exercises().stream().allMatch(e -> e.weightKg() > 0));
    }

    @Test
    void bodyPartSplitDayOnlyPullsFromItsOwnBodyPart() {
        User user = newTestUser();
        DaySplitDef chestDay = SplitCatalog.forDays(5).get(0); // 胸

        GeneratedDayPlan plan = workoutPlanService.planDay(user, chestDay, PlanMode.NOVICE, PhaseType.HYPER, false);

        assertEquals("胸", plan.dayName());
        List<String> names = plan.exercises().stream().map(PlannedExercise::name).toList();
        assertTrue(names.contains("臥推"));
        assertFalse(names.contains("側平舉"), "肩的配件不該混進純胸日");
        assertFalse(names.contains("二頭彎舉"), "手臂的配件不該混進純胸日");
    }

    @Test
    void currentQueueForFreshUserReturnsAllThreeDaysInOrder() {
        User user = newTestUser();

        List<GeneratedDayPlan> queue = workoutPlanService.currentQueue(user, 3, PlanMode.NOVICE, PhaseType.ADAPT, false);

        assertEquals(3, queue.size());
        assertEquals("推日", queue.get(0).dayName());
        assertEquals("拉日", queue.get(1).dayName());
        assertEquals("腿日", queue.get(2).dayName());
    }

    // ===== DayComposition：/plan 頁前端現在真的打這幾支拿課表組成 =====

    @Test
    void currentQueueCompositionMatchesRealExerciseTableForThreeDaySplit() {
        User user = newTestUser();

        List<WorkoutPlanService.DayComposition> queue = workoutPlanService.currentQueueComposition(user, 3);

        assertEquals(3, queue.size());
        assertEquals("推日", queue.get(0).dayName());
        assertEquals(List.of("臥推", "肩推"), queue.get(0).mainNames());
        assertTrue(queue.get(0).accessoryPool().contains("側平舉"));
        assertFalse(queue.get(0).accessoryPool().contains("硬舉"), "拉的動作不該混進推日的配件池");
    }

    @Test
    void nextCompositionInCycleAdvancesToNextRealSplitDay() {
        User user = newTestUser();

        WorkoutPlanService.DayComposition next = workoutPlanService.nextCompositionInCycle(user, "腿日", 3);

        assertEquals("推日", next.dayName());
        assertTrue(next.mainNames().contains("臥推"));
    }

    // ===== 週次 + A/B 配件輪替：對著真的 Exercise 表驗證「拉 A」跟「拉 B」不再長一樣 =====

    private DaySplitDef sixDaySplitDay(String name) {
        return SplitCatalog.forDays(6).stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void sixDayPullADiffersFromPullBInSameWeekButMainNamesStaySame() {
        User user = newTestUser();
        DaySplitDef pullA = sixDaySplitDay("拉 A");
        DaySplitDef pullB = sixDaySplitDay("拉 B");

        WorkoutPlanService.DayComposition a = workoutPlanService.composeDay(user, pullA, 1);
        WorkoutPlanService.DayComposition b = workoutPlanService.composeDay(user, pullB, 1);

        assertNotEquals(a.accessoryPool(), b.accessoryPool(), "真實資料庫下，同一週的拉A/拉B配件也應該不一樣");
        assertEquals(a.mainNames(), b.mainNames(), "主項（進階追蹤依據）不受 A/B 輪替影響");
    }

    @Test
    void sixDayPullADiffersAcrossWeeksButMainNamesStaySame() {
        User user = newTestUser();
        DaySplitDef pullA = sixDaySplitDay("拉 A");

        WorkoutPlanService.DayComposition week1 = workoutPlanService.composeDay(user, pullA, 1);
        WorkoutPlanService.DayComposition week3 = workoutPlanService.composeDay(user, pullA, 3);

        assertNotEquals(week1.accessoryPool(), week3.accessoryPool(), "真實資料庫下，換週配件也應該不一樣");
        assertEquals(week1.mainNames(), week3.mainNames());
    }
}
