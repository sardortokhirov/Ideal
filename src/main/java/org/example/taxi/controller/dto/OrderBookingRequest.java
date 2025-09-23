package org.example.taxi.controller.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.annotation.Nullable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderBookingRequest {
    @NotNull(message = "From District ID is required.")
    private Long fromDistrictId;

    @NotNull(message = "To District ID is required.")
    private Long toDistrictId;

    @Nullable
    private String fromLocation;

    @Nullable
    private String toLocation;

    @NotNull(message = "Pickup time is required.")
    @FutureOrPresent(message = "Pickup time must be in the present or future.")
    private LocalDateTime pickupTime;

    @Min(value = 1, message = "Minimum 1 seat must be selected.")
    @Max(value = 4, message = "Maximum 4 seats can be selected.")
    private int seats;

    private boolean premium = false;

    @Nullable
    private List<String> selectedSeats;

    @NotBlank(message = "Luggage type is required.")
    private String luggageType; // WITH_CLIENT, SEND_ALONE

    public void validateDistricts() {
        if (fromDistrictId != null && toDistrictId != null && fromDistrictId.equals(toDistrictId)) {
            throw new IllegalArgumentException("From and To districts cannot be the same.");
        }
    }
}