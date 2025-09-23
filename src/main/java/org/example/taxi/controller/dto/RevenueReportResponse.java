package org.example.taxi.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RevenueReportResponse {
    private BigDecimal totalAppEarningsAllTime; // Total app fees collected
    private BigDecimal totalCompanyRevenueAllTime; // Your company's 15% / 100% cut
    private BigDecimal totalDriverNetEarningsAllTime; // Total driver earnings after app fee
    private BigDecimal totalClientSpendingAllTime; // Total client spending

    private Map<LocalDate, BigDecimal> dailyAppEarnings;
    private Map<YearMonth, BigDecimal> monthlyAppEarnings;

    private Map<LocalDate, BigDecimal> dailyCompanyRevenue; // Your company's daily revenue
    private Map<YearMonth, BigDecimal> monthlyCompanyRevenue; // Your company's monthly revenue

    private Map<Long, BigDecimal> appEarningsByDistrict;
    private Map<Long, BigDecimal> companyRevenueByDistrict; // Your company's revenue by district
    private Map<Long, BigDecimal> appEarningsByRegion;
    private Map<Long, BigDecimal> companyRevenueByRegion; // Your company's revenue by region

    private List<ChartDataPoint> ordersByDistrictDistribution; // Total orders count by district
    private List<ChartDataPoint> ordersByRegionDistribution;   // Total orders count by region
}