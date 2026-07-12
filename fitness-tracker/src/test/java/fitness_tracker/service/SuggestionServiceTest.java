package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.enums.CompletionStatus;

class SuggestionServiceTest {

    private final SuggestionService service = new SuggestionService();

    @Test
    void shouldCreateWarningForRepeatedHighIntensityFailures() {
        WorkoutSession session1 = createSession(LocalDate.of(2026, 7, 10), "深蹲", 9.5, CompletionStatus.FAILED);
        WorkoutSession session2 = createSession(LocalDate.of(2026, 7, 11), "深蹲", 9.2, CompletionStatus.FAILED);

        List<SuggestionService.Suggestion> suggestions = service.generateSuggestions(List.of(session1, session2));

        assertEquals(1, suggestions.size());
        assertEquals("WARN", suggestions.get(0).level());
        assertTrue(suggestions.get(0).message().contains("減量 10%"));
    }

    @Test
    void shouldCreateInfoForConsistentSuccessfulWork() {
        WorkoutSession s1 = createSession(LocalDate.of(2026, 7, 8), "臥推", 7.0, CompletionStatus.COMPLETE);
        WorkoutSession s2 = createSession(LocalDate.of(2026, 7, 9), "臥推", 6.5, CompletionStatus.COMPLETE);
        WorkoutSession s3 = createSession(LocalDate.of(2026, 7, 10), "臥推", 7.5, CompletionStatus.COMPLETE);

        List<SuggestionService.Suggestion> suggestions = service.generateSuggestions(List.of(s1, s2, s3));

        assertEquals(1, suggestions.size());
        assertEquals("INFO", suggestions.get(0).level());
        assertTrue(suggestions.get(0).message().contains("加 2.5kg"));
    }

    @Test
    void shouldCreateDangerForPainOrRepeatedFailures() {
        WorkoutSession session = createSession(LocalDate.of(2026, 7, 12), "硬舉", 8.0, CompletionStatus.PAIN);

        List<SuggestionService.Suggestion> suggestions = service.generateSuggestions(List.of(session));

        assertEquals(1, suggestions.size());
        assertEquals("DANGER", suggestions.get(0).level());
        assertTrue(suggestions.get(0).message().contains("休息"));
    }

    @Test
    void shouldReturnEmptyListWhenNoRuleMatches() {
        WorkoutSession session = createSession(LocalDate.of(2026, 7, 12), "肩推", 6.0, CompletionStatus.COMPLETE);

        List<SuggestionService.Suggestion> suggestions = service.generateSuggestions(List.of(session));

        assertTrue(suggestions.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenSessionsIsEmpty() {
        List<SuggestionService.Suggestion> suggestions = service.generateSuggestions(List.of());

        assertTrue(suggestions.isEmpty());
    }

    private WorkoutSession createSession(LocalDate date, String exerciseName, double rpe, CompletionStatus status) {
        WorkoutSession session = new WorkoutSession();
        session.setWorkoutDate(date);
        session.setBodyPart("胸");

        WorkoutSet set = new WorkoutSet();
        set.setExerciseName(exerciseName);
        set.setRpe(rpe);
        set.setCompletionStatus(status);
        set.setSession(session);
        session.getSets().add(set);
        return session;
    }
}
