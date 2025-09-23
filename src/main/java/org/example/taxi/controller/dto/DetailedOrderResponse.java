package org.example.taxi.controller.dto;

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
public class DetailedOrderResponse {
    private Long id;
    private Long userId;
    private String clientPhoneNumber;
    private String clientFirstName;
    private String clientLastName;

    private Long driverId;
    private String driverPhoneNumber;
    private String driverFirstName;
    private String driverLastName;

    private int seats;
    private boolean premium;
    private List<String> selectedSeats;
    private String luggageType;
    private BigDecimal luggageFee;

    private Long fromDistrictId;
    private String fromDistrictName;
    private Long toDistrictId;
    private String toDistrictName;
    private String fromLocation;
    private String toLocation;

    private LocalDateTime pickupTime;
    private BigDecimal totalCost;
    private String status; // String representation of OrderStatus
    private LocalDateTime createdAt;
}