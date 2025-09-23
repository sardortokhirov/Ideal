package org.example.taxi.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RideStatsResponse {
    private long totalOrdersCount;
    private long completedOrdersCount;
    private long canceledOrdersCount;
    private long pendingOrdersCount;
    private long acceptedOrdersCount;
    private long enRouteOrdersCount;

    private List<ChartDataPoint> ordersByStatusDistribution; // Replaced Map<String, Long> with List<ChartDataPoint>

    private Map<Long, Long> completedOrdersByDriverId;
    private Map<LocalDate, Long> dailyCompletedOrders;
    private Map<Long, Long> completedOrdersByDistrict;
}