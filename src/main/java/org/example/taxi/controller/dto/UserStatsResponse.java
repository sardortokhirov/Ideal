package org.example.taxi.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class UserStatsResponse {
    private long totalUsersCount;
    private long totalClientsCount;
    private long totalDriversCount;
    private long totalOperatorsCount;
    private long totalAdminsCount;

    private List<ChartDataPoint> dailyNewUsers; // Changed from Map to List<ChartDataPoint>
    private List<ChartDataPoint> dailyNewDrivers; // Changed from Map to List<ChartDataPoint>
    private List<ChartDataPoint> dailyNewClients; // Changed from Map to List<ChartDataPoint>

    private List<ChartDataPoint> usersByDistrictDistribution; // NEW: Users distribution by district
    private List<ChartDataPoint> clientsByDistrictDistribution; // NEW: Clients distribution by district
    private List<ChartDataPoint> driversByDistrictDistribution; // NEW: Drivers distribution by district

    private List<DriverProfileResponse> driversPendingApproval;
    private List<ClientProfileResponse> latestClients;
}