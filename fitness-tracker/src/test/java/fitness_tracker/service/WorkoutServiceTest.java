package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import fitness_tracker.dto.WorkoutRequest;
import fitness_tracker.entity.BodyPart;
import fitness_tracker.entity.Exercise;
import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.enums.CompletionStatus;
import fitness_tracker.repository.BodyPartRepository;
import fitness_tracker.repository.TrainingPlanRepository;
import fitness_tracker.repository.WorkoutSessionRepository;
import fitness_tracker.repository.WorkoutSetRepository;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutSessionRepository repository;

    @Mock
    private WorkoutSetRepository workoutSetRepository;

    @Mock
    private ExerciseService exerciseService;

    @Mock
    private BodyPartRepository bodyPartRepository;

    @Mock
    private LiftPrService liftPrService;

    @Mock
    private TrainingPlanRepository trainingPlanRepository;

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
                List.of(
                        new WorkoutRequest.ExerciseDto("深蹲", 100.0, 3, 5, null, 8.5, CompletionStatus.COMPLETE, 5, 100.0),
                        new WorkoutRequest.ExerciseDto("   ", 90.0, 2, 8, null, 7.0, CompletionStatus.FAILED, 8, 90.0),
                        new WorkoutRequest.ExerciseDto("臥推", 80.0, 3, 10, null, 9.0, CompletionStatus.COMPLETE, 10, 80.0)
                )
        );

        assertEquals(2, session.getSets().size());
        assertEquals("深蹲", session.getSets().get(0).getExerciseName());
        assertEquals(100.0, session.getSets().get(0).getWeightKg());
        assertEquals(3, session.getSets().get(0).getSets());
        assertEquals(CompletionStatus.COMPLETE, session.getSets().get(0).getCompletionStatus());
        assertEquals("臥推", session.getSets().get(1).getExerciseName());
        verify(repository).save(session);
    }

    // 兩條寫入路徑（REST 的 WorkoutRequest.@Size 只擋得到 REST；MVC 表單控制器自己 zip 平行參數，
    // 不會經過 Bean Validation）都要靠 WorkoutService 這道共用防線擋住異常大量的動作，見
    // WorkoutService.validateExerciseCount
    @Test
    void saveRejectsMoreThanFiftyExercises() {
        BodyPart bodyPart = new BodyPart();
        bodyPart.setName("胸");
        when(bodyPartRepository.findByName("胸")).thenReturn(Optional.of(bodyPart));

        WorkoutSession session = new WorkoutSession();
        session.setBodyPart("胸");

        List<WorkoutRequest.ExerciseDto> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(new WorkoutRequest.ExerciseDto("動作" + i, 10.0, 1, 1, null, null, null, null, null));
        }

        assertThrows(IllegalArgumentException.class, () -> service.save(session, tooMany));
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

        User user = new User();
        when(repository.findByIdAndUser(7L, user)).thenReturn(Optional.of(existing));

        service.update(
                7L,
                LocalDate.of(2026, 7, 2),
                "胸",
                "更新",
                List.of(new WorkoutRequest.ExerciseDto("新動作", 60.0, 4, 8, null, 7.5, CompletionStatus.COMPLETE, 8, 60.0)),
                user
        );

        assertEquals(1, existing.getSets().size());
        assertEquals("新動作", existing.getSets().get(0).getExerciseName());
        assertTrue(existing.getSets().stream().noneMatch(set -> "舊動作".equals(set.getExerciseName())));
        assertEquals("更新", existing.getNote());
        verify(repository).save(existing);
    }

    @Test
    void saveRecordsAccessoryPrButSkipsMainLifts() {
        BodyPart bodyPart = new BodyPart();
        bodyPart.setName("推日");
        when(bodyPartRepository.findByName("推日")).thenReturn(Optional.of(bodyPart));

        User user = new User();
        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setBodyPart("推日");

        service.save(
                session,
                List.of(
                        new WorkoutRequest.ExerciseDto("臥推", 80.0, 4, 5, null, null, null, null, null),
                        new WorkoutRequest.ExerciseDto("三頭下壓", 15.0, 3, 12, null, null, null, null, null)
                )
        );

        verify(liftPrService, org.mockito.Mockito.never()).saveManual(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq("臥推"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        // 組數(3)/次數(12) 來自 ExerciseDto 本身；沒填休息秒數則落回預設 90 秒。
        // 使用者還沒建過 TrainingPlan（沒去過 /plan），落回預設的「肌耐力期」("adapt")
        verify(liftPrService).saveManual(user, "三頭下壓", "adapt", 15.0, 3, 12, 90);
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

    @Test
    void completedBodyPartsThisWeekReturnsDistinctNonNullBodyParts() {
        User user = new User();
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        WorkoutSession pushDay = new WorkoutSession();
        pushDay.setBodyPart("推日");
        WorkoutSession pushDayAgain = new WorkoutSession();
        pushDayAgain.setBodyPart("推日");
        WorkoutSession noBodyPart = new WorkoutSession();
        noBodyPart.setBodyPart(null);

        when(repository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateDesc(user, monday, sunday))
                .thenReturn(List.of(pushDay, pushDayAgain, noBodyPart));

        Set<String> completed = service.completedBodyPartsThisWeek(user);

        assertEquals(Set.of("推日"), completed);
    }

    @Test
    void mainLiftProgressUsesManualOverrideWhenNewerThanLastCompletedSet() {
        User user = new User();
        WorkoutSession session = new WorkoutSession();
        session.setWorkoutDate(LocalDate.now().minusDays(10));
        WorkoutSet completed = new WorkoutSet();
        completed.setExerciseName("臥推");
        completed.setWeightKg(70.0);
        completed.setCompletionStatus(CompletionStatus.COMPLETE);
        completed.setSession(session);
        when(workoutSetRepository.findBySession_UserAndExerciseNameInAndCompletionStatusOrderBySession_WorkoutDateDescIdDesc(
                eq(user), any(), eq(CompletionStatus.COMPLETE))).thenReturn(List.of(completed));

        LiftPr override = new LiftPr();
        override.setExerciseName("臥推");
        override.setWeightKg(80.0);
        ReflectionTestUtils.setField(override, "updatedAt", LocalDateTime.now());
        when(liftPrService.findOverride(user, "臥推")).thenReturn(Optional.of(override));
        when(liftPrService.findOverride(eq(user), org.mockito.ArgumentMatchers.argThat(n -> !"臥推".equals(n))))
                .thenReturn(Optional.empty());

        WorkoutService.MainLiftProgress progress = service.mainLiftProgress(user).get("臥推");

        assertEquals(80.0, progress.lastCompletedWeightKg());
        assertFalse(progress.stalled());
    }

    @Test
    void mainLiftProgressPrefersRealHistoryWhenNewerThanManualOverride() {
        User user = new User();
        WorkoutSession session = new WorkoutSession();
        session.setWorkoutDate(LocalDate.now());
        WorkoutSet completed = new WorkoutSet();
        completed.setExerciseName("臥推");
        completed.setWeightKg(70.0);
        completed.setCompletionStatus(CompletionStatus.COMPLETE);
        completed.setSession(session);
        when(workoutSetRepository.findBySession_UserAndExerciseNameInAndCompletionStatusOrderBySession_WorkoutDateDescIdDesc(
                eq(user), any(), eq(CompletionStatus.COMPLETE))).thenReturn(List.of(completed));
        when(workoutSetRepository.findTop10BySession_UserAndExerciseNameOrderBySession_WorkoutDateDescIdDesc(user, "臥推"))
                .thenReturn(List.of(completed));

        LiftPr override = new LiftPr();
        override.setExerciseName("臥推");
        override.setWeightKg(80.0);
        ReflectionTestUtils.setField(override, "updatedAt", LocalDateTime.now().minusDays(10));
        when(liftPrService.findOverride(user, "臥推")).thenReturn(Optional.of(override));
        when(liftPrService.findOverride(eq(user), org.mockito.ArgumentMatchers.argThat(n -> !"臥推".equals(n))))
                .thenReturn(Optional.empty());

        WorkoutService.MainLiftProgress progress = service.mainLiftProgress(user).get("臥推");

        assertEquals(70.0, progress.lastCompletedWeightKg());
        assertFalse(progress.stalled());
    }

    private Exercise newExercise(String name, String category) {
        Exercise exercise = new Exercise();
        exercise.setName(name);
        exercise.setCategory(category);
        return exercise;
    }
}
