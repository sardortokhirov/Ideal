package org.example.taxi.controller.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.taxi.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookingResponse {
    private Long orderId;
    private Long fromDistrictId;
    private Long toDistrictId;
    private String fromLocation;
    private String toLocation;
    private LocalDateTime pickupTime;
    private int seats;
    private BigDecimal totalCost;
    private String status; // Still String for DTO, but internally enum
    private boolean premium;
    private List<String> selectedSeats;
    private String luggageType;
    private BigDecimal luggageFee;

    public static OrderBookingResponse fromEntity(OrderEntity order) {
        if (order == null) {
            return null;
        }
        return new OrderBookingResponse(
                order.getId(),
                order.getFromDistrictId(),
                order.getToDistrictId(),
                order.getFromLocation(),
                order.getToLocation(),
                order.getPickupTime(),
                order.getSeats(),
                order.getTotalCost(),
                order.getStatus() != null ? order.getStatus().name() : null, // Convert enum to String
                order.isPremium(),
                order.getSelectedSeats(),
                order.getLuggageType(),
                order.getLuggageFee()
        );
    }
}