package fitness_tracker.dto;

import java.time.LocalDate;

public record BodyWeightRequest(
        LocalDate recordedDate,
        Double weightKg,
        String timeOfDay,
        String note
) {}
