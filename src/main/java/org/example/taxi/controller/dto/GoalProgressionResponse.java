package org.example.taxi.controller.dto;

import lombok.Builder;
import lombok.Data;
import org.example.taxi.entity.Goal;

import java.math.BigDecimal;
import java.time.YearMonth;

@Data
@Builder
public class GoalProgressionResponse {
    private GoalResponse goal;
    private Long actualNewClients;
    private Long actualNewDrivers;
    private BigDecimal actualCompanyRevenue;

    private BigDecimal clientProgressPercent; // (actual/target) * 100
    private BigDecimal driverProgressPercent;
    private BigDecimal revenueProgressPercent;

    private String clientStatus; // e.g., "On Track", "Behind", "Exceeded"
    private String driverStatus;
    private String revenueStatus;

    private Long remainingClientsNeeded; // If behind
    private Long remainingDriversNeeded;
    private BigDecimal remainingRevenueNeeded;

    private boolean isAchieved;
    private boolean isActiveMonth; // Indicates if the goal is for the current month
}