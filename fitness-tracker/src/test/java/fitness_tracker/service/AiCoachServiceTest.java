package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import fitness_tracker.entity.CoachAdvice;
import fitness_tracker.entity.User;
import fitness_tracker.repository.CoachAdviceRepository;
import fitness_tracker.service.SnapshotService.CoachSnapshot;
import fitness_tracker.service.TrainingPlanService.DayPlan;

@ExtendWith(MockitoExtension.class)
class AiCoachServiceTest {

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private SnapshotService snapshotService;

    @Mock
    private CoachAdviceRepository repo;

    @InjectMocks
    private AiCoachService service;

    private final User user = new User();

    @Test
    void noChatModelAvailableUsesRuleBasedFallback() {
        DayPlan today = new DayPlan(true, "推日", List.of("臥推"), "肌肥大", 3);
        DayPlan tomorrow = new DayPlan(false, "休息日", List.of(), "肌肥大", 3);
        CoachSnapshot snap = new CoachSnapshot(3, 3, today, tomorrow, 7.5, false);
        when(snapshotService.build(user)).thenReturn(snap);
        when(repo.findByUserAndGeneratedDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(chatModelProvider.getIfAvailable()).thenReturn(null);
        when(repo.save(any(CoachAdvice.class))).thenAnswer(inv -> inv.getArgument(0));

        CoachAdvice advice = service.getTodayAdvice(user);

        assertEquals("CRUSHING", advice.getStatus());
        assertTrue(advice.getTodayLine().contains("推日"));
        verify(repo).save(any(CoachAdvice.class));
    }

    @Test
    void cautionSnapshotForcesRestAdviceRegardlessOfCompletion() {
        DayPlan today = new DayPlan(true, "腿日", List.of("深蹲"), "肌肥大", 3);
        DayPlan tomorrow = new DayPlan(false, "休息日", List.of(), "肌肥大", 3);
        CoachSnapshot snap = new CoachSnapshot(3, 3, today, tomorrow, 9.0, true);
        when(snapshotService.build(user)).thenReturn(snap);
        when(repo.findByUserAndGeneratedDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(chatModelProvider.getIfAvailable()).thenReturn(null);
        when(repo.save(any(CoachAdvice.class))).thenAnswer(inv -> inv.getArgument(0));

        CoachAdvice advice = service.getTodayAdvice(user);

        assertEquals("CAUTION", advice.getStatus());
        assertTrue(advice.getTodayLine().contains("休息"));
    }

    @Test
    void sameDayAndUnchangedSnapshotReturnsCachedAdviceWithoutRegenerating() {
        DayPlan today = new DayPlan(false, "休息日", List.of(), "肌肥大", 3);
        DayPlan tomorrow = new DayPlan(true, "推日", List.of("臥推"), "肌肥大", 3);
        CoachSnapshot snap = new CoachSnapshot(3, 1, today, tomorrow, 7.0, false);
        when(snapshotService.build(user)).thenReturn(snap);

        CoachAdvice cached = new CoachAdvice();
        cached.setSnapshotHash(snap.hash());
        when(repo.findByUserAndGeneratedDate(eq(user), any(LocalDate.class))).thenReturn(Optional.of(cached));

        CoachAdvice result = service.getTodayAdvice(user);

        assertSame(cached, result);
        verify(repo, never()).save(any());
        verify(chatModelProvider, never()).getIfAvailable();
    }
}
