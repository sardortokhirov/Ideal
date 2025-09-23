package org.example.taxi.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardSummaryResponse {
    // User Stats
    private long totalUsers;
    private long totalDrivers;
    private long totalClients;
    private long driversPendingApproval;

    // Order Stats
    private long totalOrders;
    private long completedOrders;
    private long canceledOrders;
    private long activeOrders;

    // Financial Overview - App's Perspective (Ideal Taxi's cut from driver's earnings)
    private BigDecimal totalAppEarnings; // Total app fees collected from drivers
    private BigDecimal totalCompanyRevenueFromAppEarnings; // 15% of passenger fee + 100% of luggage fee
    private BigDecimal totalDriverNetEarnings; // Total (client pays - app fee)
    private BigDecimal totalClientSpending; // Total amount clients paid for completed orders
}