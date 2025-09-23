package org.example.taxi.controller.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OperatorOrderCreationRequest {
    @NotBlank(message = "Client phone number is required.")
    @Pattern(regexp = "^\\+?\\d{10,15}$", message = "Invalid client phone number format.")
    private String clientPhoneNumber;

    @Nullable private String clientFirstName;
    @Nullable private String clientLastName;

    @Min(value = 1, message = "Minimum 1 seat must be selected.")
    @Max(value = 4, message = "Maximum 4 seats can be selected.")
    private int seats;

    private boolean premium = false;

    @Nullable private List<String> selectedSeats;

    @NotBlank(message = "Luggage type is required.")
    private String luggageType; // WITH_CLIENT, SEND_ALONE

    @NotNull(message = "From District ID is required.")
    private Long fromDistrictId;

    @NotNull(message = "To District ID is required.")
    private Long toDistrictId;

    @Nullable // Optional String locations for operator orders
    private String fromLocation;

    @Nullable // Optional String locations for operator orders
    private String toLocation;

    @NotNull(message = "Pickup time is required.")
    @FutureOrPresent(message = "Pickup time must be in the present or future.")
    private LocalDateTime pickupTime;

    @Nullable private Long driverId;
}