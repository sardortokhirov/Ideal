package org.example.taxi.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.YearMonth;

@Data
public class GoalRequest {
    @NotNull(message = "Month is required.")
    @DateTimeFormat(pattern = "yyyy-MM") // Format for MonthPicker
    private YearMonth month;

    @NotNull(message = "Target new clients is required.")
    @Min(value = 0, message = "Target new clients cannot be negative.")
    private Long targetNewClients;

    @NotNull(message = "Target new drivers is required.")
    @Min(value = 0, message = "Target new drivers cannot be negative.")
    private Long targetNewDrivers;

    @NotNull(message = "Target company revenue is required.")
    @DecimalMin(value = "0.0", inclusive = true, message = "Target company revenue cannot be negative.")
    private BigDecimal targetCompanyRevenue;
}