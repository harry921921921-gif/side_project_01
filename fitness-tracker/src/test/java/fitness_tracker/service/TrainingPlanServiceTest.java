package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.repository.TrainingPlanRepository;
import fitness_tracker.service.TrainingPlanService.Adherence;
import fitness_tracker.service.TrainingPlanService.CardOverride;
import fitness_tracker.service.TrainingPlanService.DayPlan;

@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {

    @Mock
    private TrainingPlanRepository repo;

    @Mock
    private WorkoutService workoutService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TrainingPlanService service;

    private final User user = new User();

    @Test
    void getOrCreateForUserCreatesDefaultPlanWhenNoneExists() {
        when(repo.findByUser(user)).thenReturn(Optional.empty());
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan plan = service.getOrCreateForUser(user);

        assertEquals(PlanMode.NOVICE, plan.getMode());
        assertEquals(3, plan.getDaysPerWeek());
        assertEquals("MONDAY,WEDNESDAY,FRIDAY", plan.getTrainingWeekdays());
        verify(repo).save(any(TrainingPlan.class));
    }

    @Test
    void saveOrUpdateClampsDaysPerWeekToOneToSevenRange() {
        when(repo.findByUser(user)).thenReturn(Optional.empty());
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan plan = service.saveOrUpdate(user, PlanMode.VETERAN, 99, "MONDAY", LocalDate.now());

        assertEquals(7, plan.getDaysPerWeek());
    }

    @Test
    void currentWeekCountsWeeksSincePhaseStartInclusive() {
        TrainingPlan plan = new TrainingPlan();
        plan.setPhaseStartDate(LocalDate.of(2026, 7, 1));

        int week = service.currentWeek(plan, LocalDate.of(2026, 7, 15));

        assertEquals(3, week); // 14 days later = 2 full weeks elapsed -> week 3
    }

    // 使用者持續用超過 20 週（一輪跑完），第 21 週要折回當作第 1 週（適應期），
    // 不是永遠停在最大力量期——跟 PhaseCalendarTest 驗證的是同一個折算邏輯，這裡驗證
    // TrainingPlanService 自己的 phaseForWeek 也有正確套用（見 dayPlanFor 會用到這個）
    @Test
    void phaseForWeekWrapsBackToAdaptAfterTwentyWeeks() {
        assertEquals(TrainingPlanService.PhaseType.ADAPT, service.phaseForWeek(21));
        assertEquals(TrainingPlanService.PhaseType.STRENGTH, service.phaseForWeek(35)); // 35 = 20 + 15
    }

    @Test
    void dayPlanForReturnsRestDayWhenWeekdayNotScheduled() {
        TrainingPlan plan = new TrainingPlan();
        plan.setPhaseStartDate(LocalDate.of(2026, 7, 1));
        plan.setDaysPerWeek(3);
        plan.setTrainingWeekdays("MONDAY,WEDNESDAY,FRIDAY");

        DayPlan sunday = service.dayPlanFor(plan, LocalDate.of(2026, 7, 26)); // a Sunday

        assertFalse(sunday.training());
        assertEquals("休息日", sunday.dayName());
    }

    @Test
    void dayPlanForReturnsScheduledSplitOnTrainingDay() {
        TrainingPlan plan = new TrainingPlan();
        plan.setPhaseStartDate(LocalDate.of(2026, 7, 1));
        plan.setDaysPerWeek(3);
        plan.setTrainingWeekdays("MONDAY,WEDNESDAY,FRIDAY");

        DayPlan monday = service.dayPlanFor(plan, LocalDate.of(2026, 7, 27)); // a Monday

        assertTrue(monday.training());
        assertFalse(monday.mainLifts().isEmpty());
    }

    @Test
    void weeklyAdherenceComparesPlannedDaysAgainstCompletedSessions() {
        TrainingPlan plan = new TrainingPlan();
        plan.setDaysPerWeek(4);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(workoutService.countThisWeek(user)).thenReturn(2L);

        Adherence adherence = service.weeklyAdherence(user);

        assertEquals(4, adherence.planned());
        assertEquals(2, adherence.completed());
        assertEquals(2, adherence.missed());
    }

    @Test
    void setCurrentWeekOnlyChangesPhaseStartDateNotOtherFields() {
        TrainingPlan plan = new TrainingPlan();
        plan.setMode(PlanMode.VETERAN);
        plan.setDaysPerWeek(5);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan updated = service.setCurrentWeek(user, 15);

        assertEquals(15, service.currentWeek(updated, LocalDate.now()));
        assertEquals(PlanMode.VETERAN, updated.getMode());
        assertEquals(5, updated.getDaysPerWeek());
    }

    @Test
    void setCurrentWeekClampsToMaxOf104() {
        TrainingPlan plan = new TrainingPlan();
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan updated = service.setCurrentWeek(user, 9999);

        assertEquals(104, service.currentWeek(updated, LocalDate.now()));
    }

    // ===== extraQueueCount：「新增課表」多排出來的張數要跨登入保留，只有換天數/手動校正週次才歸零 =====

    @Test
    void saveOrUpdateKeepsExtraQueueCountWhenDaysPerWeekUnchanged() {
        TrainingPlan plan = new TrainingPlan();
        plan.setDaysPerWeek(3);
        plan.setExtraQueueCount(4);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan updated = service.saveOrUpdate(user, PlanMode.VETERAN, 3, "MONDAY", null);

        assertEquals(4, updated.getExtraQueueCount(), "天數沒變，新增課表排出來的張數應該保留");
    }

    @Test
    void saveOrUpdateResetsExtraQueueCountWhenDaysPerWeekChanges() {
        TrainingPlan plan = new TrainingPlan();
        plan.setDaysPerWeek(3);
        plan.setExtraQueueCount(4);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan updated = service.saveOrUpdate(user, PlanMode.VETERAN, 5, "MONDAY", null);

        assertEquals(0, updated.getExtraQueueCount(), "天數改變後，舊的延伸卡片對新分化沒意義，應該歸零");
    }

    @Test
    void saveOrUpdateForBrandNewPlanStartsExtraQueueCountAtZero() {
        when(repo.findByUser(user)).thenReturn(Optional.empty());
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan plan = service.saveOrUpdate(user, PlanMode.NOVICE, 4, "MONDAY", null);

        assertEquals(0, plan.getExtraQueueCount());
    }

    @Test
    void setCurrentWeekResetsExtraQueueCount() {
        TrainingPlan plan = new TrainingPlan();
        plan.setExtraQueueCount(3);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan updated = service.setCurrentWeek(user, 10);

        assertEquals(0, updated.getExtraQueueCount(), "手動校正週次算主動更改訓練週期，延伸卡片應該歸零");
    }

    @Test
    void incrementExtraQueueCountAddsOneAndPersists() {
        TrainingPlan plan = new TrainingPlan();
        plan.setExtraQueueCount(2);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        int result = service.incrementExtraQueueCount(user);

        assertEquals(3, result);
        assertEquals(3, plan.getExtraQueueCount());
        verify(repo).save(plan);
    }

    // ===== cardOverridesJson 依「一週練幾天」分開存：不同天數分化的天型態名稱完全不同組
    // （3 天是「推日/拉日/腿日」、4 天是「上肢 A/下肢 A/...」），换天數不該讓舊的自訂卡片
    // 變成用不到的孤兒資料，也不該在換回原本天數時把好幾個月前的舊設定悄悄套回來 =====

    @Test
    void getCardOverridesOnlyReturnsEntriesForCurrentDaysPerWeek() {
        TrainingPlan plan = new TrainingPlan();
        plan.setDaysPerWeek(3);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveCardOverride(user, "推日", List.of("臥推"), List.of("三頭下壓"));

        Map<String, CardOverride> overrides = service.getCardOverrides(user);
        assertEquals(1, overrides.size());
        assertEquals(List.of("臥推"), overrides.get("推日").main());
    }

    @Test
    void cardOverrideSavedUnderOneDaysPerWeekDoesNotLeakIntoAnother() {
        TrainingPlan plan = new TrainingPlan();
        plan.setDaysPerWeek(3);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveCardOverride(user, "推日", List.of("臥推"), List.of());
        plan.setDaysPerWeek(4); // 使用者把一週練幾天從 3 天改成 4 天

        assertTrue(service.getCardOverrides(user).isEmpty(), "3 天分化存的自訂不該套用到 4 天分化");
    }

    @Test
    void switchingBackToOriginalDaysPerWeekRestoresItsOwnOverrideUnchanged() {
        TrainingPlan plan = new TrainingPlan();
        plan.setDaysPerWeek(3);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveCardOverride(user, "推日", List.of("臥推"), List.of());
        plan.setDaysPerWeek(4);
        plan.setDaysPerWeek(3); // 之後又切回原本的 3 天

        Map<String, CardOverride> overrides = service.getCardOverrides(user);
        assertEquals(1, overrides.size(), "切回原本天數要拿回自己那份自訂，不是空的也不是別人天數的");
        assertEquals(List.of("臥推"), overrides.get("推日").main());
    }

    @Test
    void resetCardOverrideOnlyRemovesEntryUnderCurrentDaysPerWeek() {
        TrainingPlan plan = new TrainingPlan();
        plan.setDaysPerWeek(3);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));
        when(repo.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveCardOverride(user, "推日", List.of("臥推"), List.of());
        plan.setDaysPerWeek(4);
        service.saveCardOverride(user, "上肢 A", List.of("肩推"), List.of());

        service.resetCardOverride(user, "上肢 A");
        assertTrue(service.getCardOverrides(user).isEmpty());

        plan.setDaysPerWeek(3);
        assertEquals(1, service.getCardOverrides(user).size(), "重設 4 天分化的卡片不該動到 3 天分化那份");
    }

    // ===== assertNotStale：多裝置/多分頁同時編輯課表的最後防線，粒度是整份課表的 updatedAt =====

    @Test
    void assertNotStaleDoesNothingWhenExpectedValueIsBlank() {
        // 沒帶 expectedUpdatedAt（例如很舊的分頁快取、直接呼叫 API）就不擋，
        // 不查資料庫也不拋例外——維持這層保護加入前的行為
        service.assertNotStale(user, null);
        service.assertNotStale(user, "");
    }

    @Test
    void assertNotStalePassesWhenTimestampsMatch() {
        TrainingPlan plan = new TrainingPlan();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 24, 10, 0, 0);
        ReflectionTestUtils.setField(plan, "updatedAt", updatedAt);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));

        service.assertNotStale(user, updatedAt.toString());
    }

    // 這是使用者實際會遇到的情境：手機打開編輯彈窗時記住的 updatedAt，跟電腦後來存檔存進去的
    // 已經不是同一個版本了——按下完成編輯要直接被擋下來，不能悄悄蓋掉電腦那邊剛存的內容
    @Test
    void assertNotStaleThrowsWhenAnotherDeviceSavedInBetween() {
        TrainingPlan plan = new TrainingPlan();
        LocalDateTime staleTimestamp = LocalDateTime.of(2026, 8, 24, 10, 0, 0);
        LocalDateTime newerTimestamp = LocalDateTime.of(2026, 8, 24, 10, 5, 0);
        ReflectionTestUtils.setField(plan, "updatedAt", newerTimestamp);
        when(repo.findByUser(user)).thenReturn(Optional.of(plan));

        assertThrows(IllegalArgumentException.class, () -> service.assertNotStale(user, staleTimestamp.toString()));
    }
}
