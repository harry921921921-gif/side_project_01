package fitness_tracker.dto;

public record ExerciseResponse(
        Long id,
        String name,
        String bodyPart,
        String category,
        boolean preset,
        Integer orderIndex
) {}
