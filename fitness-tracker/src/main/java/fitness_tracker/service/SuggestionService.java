package fitness_tracker.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.enums.CompletionStatus;

@Service
public class SuggestionService {

    public record Suggestion(String level, String exerciseName, String message) {}

    public List<Suggestion> generateSuggestions(List<WorkoutSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        Map<String, List<WorkoutSet>> groupedSets = new LinkedHashMap<>();
        for (WorkoutSession session : sessions) {
            if (session.getSets() == null) {
                continue;
            }
            for (WorkoutSet set : session.getSets()) {
                if (set == null || set.getExerciseName() == null || set.getExerciseName().isBlank()) {
                    continue;
                }
                groupedSets.computeIfAbsent(set.getExerciseName(), key -> new ArrayList<>()).add(set);
            }
        }

        List<Suggestion> suggestions = new ArrayList<>();
        for (Map.Entry<String, List<WorkoutSet>> entry : groupedSets.entrySet()) {
            List<WorkoutSet> sets = entry.getValue();
            if (sets.isEmpty()) {
                continue;
            }

            long highRpeFailures = sets.stream()
                    .filter(set -> set.getRpe() != null && set.getRpe() >= 9.0)
                    .filter(set -> set.getCompletionStatus() == CompletionStatus.FAILED)
                    .count();
            if (highRpeFailures >= 2) {
                suggestions.add(new Suggestion(
                        "WARN",
                        entry.getKey(),
                        entry.getKey() + " 連續兩次高強度失敗，建議下次減量 10%"
                ));
                continue;
            }

            List<WorkoutSet> recentSets = sets.stream().limit(3).toList();
            boolean allComplete = recentSets.stream().allMatch(set -> set.getCompletionStatus() == CompletionStatus.COMPLETE);
            Double averageRpe = recentSets.stream()
                    .filter(set -> set.getRpe() != null)
                    .mapToDouble(WorkoutSet::getRpe)
                    .average()
                    .orElse(0.0);
            if (allComplete && recentSets.size() >= 3 && averageRpe < 8.0) {
                suggestions.add(new Suggestion(
                        "INFO",
                        entry.getKey(),
                        entry.getKey() + " 連續三次順利完成，可嘗試加 2.5kg"
                ));
                continue;
            }

            boolean painSeen = recentSets.stream().anyMatch(set -> set.getCompletionStatus() == CompletionStatus.PAIN);
            long failedCount = recentSets.stream()
                    .filter(set -> set.getCompletionStatus() == CompletionStatus.FAILED)
                    .count();
            if (painSeen || failedCount >= 2) {
                suggestions.add(new Suggestion(
                        "DANGER",
                        entry.getKey(),
                        entry.getKey() + " 最近出現疼痛或多次失敗，建議安排休息或降低強度"
                ));
            }
        }

        return suggestions;
    }
}
