package fitness_tracker.dto;

import jakarta.validation.constraints.NotBlank;

public record ExerciseRequest(
        @NotBlank(message = "name 為必填")
        String name,
        @NotBlank(message = "bodyPart 為必填")
        String bodyPart,
        String category
) {}
