package org.example.taxi.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.taxi.entity.OrderEntity.OrderStatus; // Import enum

@Data
public class OrderStatusUpdateRequest {
    @NotNull(message = "New status is required.") // Change to NotNull for enum
    private OrderStatus newStatus; // Changed from String to OrderStatus enum
}