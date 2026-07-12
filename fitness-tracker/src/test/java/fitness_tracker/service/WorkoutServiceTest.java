package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fitness_tracker.entity.BodyPart;
import fitness_tracker.entity.Exercise;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.enums.CompletionStatus;
import fitness_tracker.repository.BodyPartRepository;
import fitness_tracker.repository.WorkoutSessionRepository;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutSessionRepository repository;

    @Mock
    private ExerciseService exerciseService;

    @Mock
    private BodyPartRepository bodyPartRepository;

    @InjectMocks
    private WorkoutService service;

    @Test
    void saveBuildsWorkoutSetsAndSkipsBlankExerciseNames() {
        BodyPart bodyPart = new BodyPart();
        bodyPart.setName("胸");
        when(bodyPartRepository.findByName("胸")).thenReturn(Optional.of(bodyPart));

        WorkoutSession session = new WorkoutSession();
        session.setBodyPart("胸");

        service.save(
                session,
                List.of("深蹲", "   ", "臥推"),
                List.of(100.0, 90.0, 80.0),
                List.of(3, 2, 3),
                List.of(5, 8, 10),
                null,
                List.of(8.5, 7.0, 9.0),
                List.of("COMPLETE", "FAILED", "COMPLETE"),
                List.of(5, 8, 10),
                List.of(100.0, 90.0, 80.0),
                List.of("主計畫", "", "補充")
        );

        assertEquals(2, session.getSets().size());
        assertEquals("深蹲", session.getSets().get(0).getExerciseName());
        assertEquals(100.0, session.getSets().get(0).getWeightKg());
        assertEquals(3, session.getSets().get(0).getSets());
        assertEquals(CompletionStatus.COMPLETE, session.getSets().get(0).getCompletionStatus());
        assertEquals("主計畫", session.getSets().get(0).getNotes());
        assertEquals("臥推", session.getSets().get(1).getExerciseName());
        verify(repository).save(session);
    }

    @Test
    void updateReplacesExistingSetsWithNewOnes() {
        BodyPart bodyPart = new BodyPart();
        bodyPart.setName("胸");
        when(bodyPartRepository.findByName("胸")).thenReturn(Optional.of(bodyPart));

        WorkoutSession existing = new WorkoutSession();
        existing.setId(7L);
        existing.setBodyPart("胸");
        existing.setWorkoutDate(LocalDate.of(2026, 7, 1));
        existing.setNote("舊資料");
        WorkoutSet oldSet = new WorkoutSet();
        oldSet.setExerciseName("舊動作");
        existing.getSets().add(oldSet);

        when(repository.findById(7L)).thenReturn(Optional.of(existing));

        service.update(
                7L,
                LocalDate.of(2026, 7, 2),
                "胸",
                "更新",
                List.of("新動作"),
                List.of(60.0),
                List.of(4),
                List.of(8),
                null,
                List.of(7.5),
                List.of("COMPLETE"),
                List.of(8),
                List.of(60.0),
                List.of("新筆記")
        );

        assertEquals(1, existing.getSets().size());
        assertEquals("新動作", existing.getSets().get(0).getExerciseName());
        assertTrue(existing.getSets().stream().noneMatch(set -> "舊動作".equals(set.getExerciseName())));
        assertEquals("更新", existing.getNote());
        verify(repository).save(existing);
    }

    @Test
    void computeDashboardStatsAggregatesRpeVolumeAndCompletionRate() {
        LocalDate today = LocalDate.now();
        WorkoutSession recent = new WorkoutSession();
        recent.setWorkoutDate(today.minusDays(2));
        recent.setBodyPart("胸");
        WorkoutSet compoundSet = new WorkoutSet();
        compoundSet.setExerciseName("深蹲");
        compoundSet.setActualWeight(100.0);
        compoundSet.setActualReps(5);
        compoundSet.setSets(3);
        compoundSet.setRpe(7.0);
        compoundSet.setCompletionStatus(CompletionStatus.COMPLETE);
        recent.getSets().add(compoundSet);

        WorkoutSession older = new WorkoutSession();
        older.setWorkoutDate(today.minusDays(10));
        older.setBodyPart("胸");
        WorkoutSet olderSet = new WorkoutSet();
        olderSet.setExerciseName("深蹲");
        olderSet.setActualWeight(80.0);
        olderSet.setActualReps(4);
        olderSet.setSets(2);
        olderSet.setRpe(8.5);
        olderSet.setCompletionStatus(CompletionStatus.COMPLETE);
        older.getSets().add(olderSet);

        when(repository.countByWorkoutDateGreaterThanEqual(today.with(DayOfWeek.MONDAY))).thenReturn(2L);
        when(repository.findByWorkoutDateBetweenOrderByWorkoutDateDesc(today.minusDays(6), today)).thenReturn(List.of(recent));
        when(repository.findAllByOrderByWorkoutDateDesc()).thenReturn(List.of(recent, older));
        when(exerciseService.findAll()).thenReturn(List.of(newExercise("深蹲", "COMPOUND")));

        WorkoutService.DashboardStats stats = service.computeDashboardStats();

        assertEquals(2L, stats.weeklyCount());
        assertEquals(1, stats.weeklyVolumeByBodyPart().size());
        assertEquals("胸", stats.weeklyVolumeByBodyPart().get(0).bodyPart());
        assertEquals(1500.0, stats.weeklyVolumeByBodyPart().get(0).volume(), 0.01);
        assertEquals(7.0, stats.avgRpe(), 0.01);
        assertNotNull(stats.recentCompletions());
        assertEquals(100, stats.recentCompletions().get(0).completionPct());
    }

    @Test
    void computeDashboardStatsIgnoresIsolationExercisesForVolume() {
        LocalDate today = LocalDate.now();
        WorkoutSession recent = new WorkoutSession();
        recent.setWorkoutDate(today.minusDays(1));
        recent.setBodyPart("手臂");
        WorkoutSet isolationSet = new WorkoutSet();
        isolationSet.setExerciseName("二頭彎舉");
        isolationSet.setActualWeight(20.0);
        isolationSet.setActualReps(10);
        isolationSet.setSets(3);
        isolationSet.setRpe(7.0);
        recent.getSets().add(isolationSet);

        when(repository.countByWorkoutDateGreaterThanEqual(today.with(DayOfWeek.MONDAY))).thenReturn(1L);
        when(repository.findByWorkoutDateBetweenOrderByWorkoutDateDesc(today.minusDays(6), today)).thenReturn(List.of(recent));
        when(repository.findAllByOrderByWorkoutDateDesc()).thenReturn(List.of(recent));
        when(exerciseService.findAll()).thenReturn(List.of(newExercise("二頭彎舉", "ISOLATION")));

        WorkoutService.DashboardStats stats = service.computeDashboardStats();

        assertTrue(stats.weeklyVolumeByBodyPart().isEmpty());
        assertEquals(7.0, stats.avgRpe(), 0.01);
    }

    @Test
    void computeDashboardStatsReturnsNullAvgRpeWhenNoRpeRecorded() {
        LocalDate today = LocalDate.now();
        WorkoutSession recent = new WorkoutSession();
        recent.setWorkoutDate(today.minusDays(1));
        recent.setBodyPart("胸");
        WorkoutSet set = new WorkoutSet();
        set.setExerciseName("深蹲");
        set.setActualWeight(100.0);
        set.setActualReps(5);
        set.setSets(3);
        recent.getSets().add(set);

        when(repository.countByWorkoutDateGreaterThanEqual(today.with(DayOfWeek.MONDAY))).thenReturn(1L);
        when(repository.findByWorkoutDateBetweenOrderByWorkoutDateDesc(today.minusDays(6), today)).thenReturn(List.of(recent));
        when(repository.findAllByOrderByWorkoutDateDesc()).thenReturn(List.of(recent));
        when(exerciseService.findAll()).thenReturn(List.of(newExercise("深蹲", "COMPOUND")));

        WorkoutService.DashboardStats stats = service.computeDashboardStats();

        assertEquals(null, stats.avgRpe());
        assertEquals(1500.0, stats.weeklyVolumeByBodyPart().get(0).volume(), 0.01);
    }

    @Test
    void findRecentWithinDaysUsesSevenDayBoundary() {
        LocalDate today = LocalDate.now();
        service.findRecentWithinDays(7);
        verify(repository).findByWorkoutDateBetweenOrderByWorkoutDateDesc(today.minusDays(6), today);
    }

    @Test
    void countThisWeekUsesMondayBoundary() {
        LocalDate expectedMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        when(repository.countByWorkoutDateGreaterThanEqual(expectedMonday)).thenReturn(3L);

        assertEquals(3L, service.countThisWeek());
        verify(repository).countByWorkoutDateGreaterThanEqual(expectedMonday);
    }

    private Exercise newExercise(String name, String category) {
        Exercise exercise = new Exercise();
        exercise.setName(name);
        exercise.setCategory(category);
        return exercise;
    }
}
