package fitness_tracker.dto;

import java.time.LocalDate;
import java.util.List;

public record WorkoutRequest(
        LocalDate workoutDate,
        String bodyPart,
        String note,
        List<ExerciseDto> exercises
) {
    public record ExerciseDto(
            String exerciseName,
            Double weightKg,
            Integer sets,
            Integer reps
    ) {}
}
