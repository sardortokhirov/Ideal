package org.example.taxi.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class GoalCalculatorResponse {
    private BigDecimal targetRevenue;
    private Long clientsNeededForRevenue;
    private Long estimatedDriversNeeded; // To serve those clients

    private String explanation;
    // You can add more metrics here, like average revenue per client, etc.
}