package org.example.taxi.controller.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.annotation.Nullable;
import lombok.Data;
import org.example.taxi.entity.OrderEntity;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderBookingRequest {
    @Min(value = 0, message = "Seats cannot be negative.")
    @Max(value = 4, message = "Maximum 4 seats can be selected.")
    private int seats;

    @NotNull(message = "Order type is required.")
    private OrderEntity.OrderType orderType;

    @Nullable private List<String> selectedSeats;

    @Nullable private String luggageContactInfo;

    @Nullable private String extraInfo;

    @NotNull(message = "From District ID is required.")
    private Long fromDistrictId;

    @NotNull(message = "To District ID is required.")
    private Long toDistrictId;

    @Nullable private String fromLocation;

    @Nullable private String toLocation;

    @NotNull(message = "Pickup time is required.")
    @FutureOrPresent(message = "Pickup time must be in the present or future.")
    private LocalDateTime pickupTime;

    public void validateDistricts() {
        if (fromDistrictId.equals(toDistrictId)) {
            throw new IllegalArgumentException("From and To districts cannot be the same.");
        }
    }
}