package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.repository.TrainingPlanRepository;
import fitness_tracker.service.TrainingPlanService.Adherence;
import fitness_tracker.service.TrainingPlanService.DayPlan;

@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {

    @Mock
    private TrainingPlanRepository repo;

    @Mock
    private WorkoutService workoutService;

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
}
