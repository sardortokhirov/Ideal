package org.example.taxi.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PriceConfigUpdate {
    @NotNull(message = "From District ID is required.")
    private Long fromDistrictId;

    @NotNull(message = "To District ID is required.")
    private Long toDistrictId;

    @NotNull(message = "Base price per seat is required.")
    @DecimalMin(value = "0.0", inclusive = false, message = "Base price per seat must be positive.")
    private BigDecimal basePricePerSeat;

    @NotNull(message = "Premium price per seat is required.")
    @DecimalMin(value = "0.0", inclusive = false, message = "Premium price per seat must be positive.")
    private BigDecimal premiumPricePerSeat;

    @NotNull(message = "Front seat extra fee is required.")
    @DecimalMin(value = "0.0", inclusive = true, message = "Front seat extra fee cannot be negative.")
    private BigDecimal frontSeatExtraFee;

    @NotNull(message = "Other seat extra fee is required.")
    @DecimalMin(value = "0.0", inclusive = true, message = "Other seat extra fee cannot be negative.")
    private BigDecimal otherSeatExtraFee;

    @NotNull(message = "Send alone luggage fee is required.")
    @DecimalMin(value = "0.0", inclusive = true, message = "Send alone luggage fee cannot be negative.")
    private BigDecimal sendAloneLuggageFee;
}