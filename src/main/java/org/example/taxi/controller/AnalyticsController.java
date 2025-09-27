package org.example.taxi.controller;

import org.example.taxi.controller.dto.*;
import org.example.taxi.entity.District;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.AdminService;
import org.example.taxi.service.MarketControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    @Autowired private AdminService adminService;
    @Autowired private MarketControlService marketControlService;
    @Autowired private UserRepository userRepository;

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "User not authenticated.");
        }
        String phoneNumber = authentication.getName(); // JWT sets phoneNumber as the principal
        return userRepository.findByPhoneNumber(phoneNumber)
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authenticated user not found in database."));
    }

    // --- General Dashboard Summary (Shared) ---
    @GetMapping("/dashboard-summary")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        logger.info("User (ID: {}) requesting dashboard summary.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getDashboardSummary());
    }

    // --- Revenue Analytics (Shared) ---
    @GetMapping("/daily-app-earnings")
    public ResponseEntity<List<ChartDataPoint>> getDailyAppEarnings(@RequestParam Optional<Integer> days) {
        logger.info("User (ID: {}) requesting daily app earnings for last {} days.", getAuthenticatedUserId(), days.orElse(30));
        return ResponseEntity.ok(adminService.getDailyAppEarnings(days));
    }

    @GetMapping("/daily-company-revenue")
    public ResponseEntity<List<ChartDataPoint>> getDailyCompanyRevenue(@RequestParam Optional<Integer> days) {
        logger.info("User (ID: {}) requesting daily company revenue for last {} days.", getAuthenticatedUserId(), days.orElse(30));
        return ResponseEntity.ok(adminService.getDailyCompanyRevenue(days));
    }

    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportResponse> getRevenueReport() {
        logger.info("User (ID: {}) requesting revenue report.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getRevenueReport());
    }

    // --- Ride Analytics (Shared) ---
    @GetMapping("/order-status-distribution")
    public ResponseEntity<List<ChartDataPoint>> getOrderStatusDistribution() {
        logger.info("User (ID: {}) requesting order status distribution.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getOrderStatusDistribution());
    }

    @GetMapping("/stats/rides")
    public ResponseEntity<RideStatsResponse> getRideStatistics() {
        logger.info("User (ID: {}) requesting ride statistics.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getRideStatistics());
    }

    // --- User Analytics (Shared) ---
    @GetMapping("/daily-new-users")
    public ResponseEntity<List<ChartDataPoint>> getDailyNewUsers(@RequestParam Optional<Integer> days) {
        logger.info("User (ID: {}) requesting daily new users for last {} days.", getAuthenticatedUserId(), days.orElse(30));
        return ResponseEntity.ok(adminService.getDailyNewUsers(days));
    }

    @GetMapping("/daily-new-clients")
    public ResponseEntity<List<ChartDataPoint>> getDailyNewClients(@RequestParam Optional<Integer> days) {
        logger.info("User (ID: {}) requesting daily new clients for last {} days.", getAuthenticatedUserId(), days.orElse(30));
        return ResponseEntity.ok(adminService.getDailyNewClients(days));
    }

    @GetMapping("/daily-new-drivers")
    public ResponseEntity<List<ChartDataPoint>> getDailyNewDrivers(@RequestParam Optional<Integer> days) {
        logger.info("User (ID: {}) requesting daily new drivers for last {} days.", getAuthenticatedUserId(), days.orElse(30));
        return ResponseEntity.ok(adminService.getDailyNewDrivers(days));
    }

    @GetMapping("/stats/users")
    public ResponseEntity<UserStatsResponse> getUserStatistics() {
        logger.info("User (ID: {}) requesting user statistics.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getUserStatistics());
    }

    // --- Driver Performance Analytics (Shared) ---
    @GetMapping("/drivers/performance")
    public ResponseEntity<DriverPerformanceResponse> getDriverPerformance() {
        logger.info("User (ID: {}) requesting driver performance report.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getDriverPerformance());
    }

    // --- District Distribution Analytics (Shared) ---
    @GetMapping("/users-by-district")
    public ResponseEntity<List<ChartDataPoint>> getUsersByDistrictDistribution() {
        logger.info("User (ID: {}) requesting users by district distribution.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getUsersByDistrictDistribution());
    }

    @GetMapping("/clients-by-district")
    public ResponseEntity<List<ChartDataPoint>> getClientsByDistrictDistribution() {
        logger.info("User (ID: {}) requesting clients by district distribution.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getClientsByDistrictDistribution());
    }

    @GetMapping("/drivers-by-district")
    public ResponseEntity<List<ChartDataPoint>> getDriversByDistrictDistribution() {
        logger.info("User (ID: {}) requesting drivers by district distribution.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getDriversByDistrictDistribution());
    }

    // --- Utility Endpoints (Shared) ---
    @GetMapping("/districts")
    public ResponseEntity<List<District>> getAllDistricts() {
        logger.info("User (ID: {}) requesting all districts.", getAuthenticatedUserId());
        return ResponseEntity.ok(adminService.getAllDistricts());
    }
}