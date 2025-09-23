package org.example.taxi.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DriverPerformanceResponse {
    private long totalApprovedDrivers;
    private long totalPendingDrivers;
    private long totalRejectedDrivers;

    private List<DriverOverview> topRatedDrivers;
    private List<DriverOverview> mostRidesDrivers;

    private List<ChartDataPoint> averageRatingByDistrict; // Changed from Map to List<ChartDataPoint>
    private List<ChartDataPoint> totalRidesByDistrict;    // Changed from Map to List<ChartDataPoint>
    private List<ChartDataPoint> totalEarningsByDriver;   // Changed from Map to List<ChartDataPoint>

    @Data
    @Builder
    public static class DriverOverview {
        private Long driverId;
        private String phoneNumber;
        private String firstName;
        private String lastName;
        private double ratings;
        private int rideCount;
        private BigDecimal walletBalance;
        private String approvalStatus;
        private String districtName;
    }
}