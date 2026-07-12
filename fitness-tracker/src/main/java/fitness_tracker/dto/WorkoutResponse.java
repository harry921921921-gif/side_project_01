package fitness_tracker.dto;

import java.time.LocalDate;
import java.util.List;

public record WorkoutResponse(
        Long id,
        LocalDate workoutDate,
        String bodyPart,
        String note,
        List<WorkoutSetResponse> sets
) {
    public record WorkoutSetResponse(
            Long id,
            String exerciseName,
            Double weightKg,
            Integer sets,
            Integer reps,
            Integer restSeconds,
            Double rpe,
            String completionStatus,
            Integer actualReps,
            Double actualWeight,
            String notes
    ) {}
}
