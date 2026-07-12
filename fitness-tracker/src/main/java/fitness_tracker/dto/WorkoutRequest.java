package fitness_tracker.dto;

import java.time.LocalDate;
import java.util.List;

import fitness_tracker.enums.CompletionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;

public record WorkoutRequest(
        @NotNull(message = "workoutDate 為必填")
        @PastOrPresent(message = "workoutDate 不能是未來日期")
        LocalDate workoutDate,
        @NotBlank(message = "bodyPart 為必填")
        String bodyPart,
        String note,
        @Valid
        List<ExerciseDto> exercises
) {
    public record ExerciseDto(
            String exerciseName,
            @PositiveOrZero(message = "weightKg 不能為負")
            Double weightKg,
            @PositiveOrZero(message = "sets 不能為負")
            Integer sets,
            @PositiveOrZero(message = "reps 不能為負")
            Integer reps,
            @DecimalMin(value = "0.0", message = "rpe 不能小於 0")
            @DecimalMax(value = "10.0", message = "rpe 不能大於 10")
            Double rpe,
            CompletionStatus completionStatus,
            @PositiveOrZero(message = "actualReps 不能為負")
            Integer actualReps,
            @PositiveOrZero(message = "actualWeight 不能為負")
            Double actualWeight,
            String notes
    ) {}
}
