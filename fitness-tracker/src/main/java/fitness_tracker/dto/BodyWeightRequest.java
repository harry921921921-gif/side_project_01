package fitness_tracker.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;

public record BodyWeightRequest(
        @NotNull(message = "recordedDate 為必填")
        @PastOrPresent(message = "recordedDate 不能是未來日期")
        LocalDate recordedDate,
        @PositiveOrZero(message = "weightKg 不能為負")
        Double weightKg,
        String timeOfDay,
        String note
) {}
