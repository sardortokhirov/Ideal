package org.example.taxi.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GoalCalculatorRequest {
    @NotNull(message = "Target revenue is required.")
    @DecimalMin(value = "0.0", inclusive = false, message = "Target revenue must be positive.")
    private BigDecimal targetRevenue;

    // Optional additional parameters for driver calculations if needed
    // @Nullable private Long currentActiveDrivers;
    // @Nullable private Long averageRidesPerDriver;
}