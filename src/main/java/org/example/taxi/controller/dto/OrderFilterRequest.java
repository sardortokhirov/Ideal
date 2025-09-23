package org.example.taxi.controller.dto;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.springframework.format.annotation.DateTimeFormat; // For swagger/request param binding

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderFilterRequest {
    @Nullable private List<OrderStatus> statuses;
    @Nullable private Long clientId;
    @Nullable private Long driverId;
    @Nullable private Long fromDistrictId;
    @Nullable private Long toDistrictId;

    @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime pickupTimeStart;
    @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime pickupTimeEnd;

    @Nullable private String clientPhoneNumber;
    @Nullable private String driverPhoneNumber;
}