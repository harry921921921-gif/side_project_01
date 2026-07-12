package fitness_tracker.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BodyWeightResponse(
        Long id,
        LocalDate recordedDate,
        Double weightKg,
        String timeOfDay,
        String note,
        LocalDateTime createdAt
) {}
