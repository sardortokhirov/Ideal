package org.example.taxi.controller;

import org.example.taxi.controller.dto.*;
import org.example.taxi.entity.District;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.AdminService; // Analytics logic resides in AdminService
import org.example.taxi.service.MarketControlService; // Specific ETAMIN market control
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/analytics") // Common base path for shared analytics
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    @Autowired private AdminService adminService; // Calls AdminService for general analytics
    @Autowired private MarketControlService marketControlService; // Calls MarketControlService for ETAMIN-specific analytics
    @Autowired private UserRepository userRepository; // For authentication helper

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByPhoneNumber(userDetails.getUsername())
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
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
    // These methods are now in MarketControlService for ETAMIN, but AdminService also has similar methods.
    // We should decide if these are truly identical and call one from the other or keep separate.
    // For now, these AnalyticsController endpoints will call AdminService's methods.
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