package org.example.taxi.controller;

import org.example.taxi.controller.dto.ChartDataPoint;
import org.example.taxi.controller.dto.GoalCalculatorResponse;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.MarketControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/etamin/market-control") // Base path for ETAMIN market control
public class MarketControlController {

    private static final Logger logger = LoggerFactory.getLogger(MarketControlController.class);

    @Autowired private MarketControlService marketControlService;
    @Autowired private UserRepository userRepository;

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByPhoneNumber(userDetails.getUsername())
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
    }

    // --- Market Control Specific Analytics (ETAMIN) ---

    @GetMapping("/daily-active-drivers") // Corrected path
    public ResponseEntity<List<ChartDataPoint>> getDailyActiveDrivers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        logger.info("ETAMIN (User ID: {}) requesting daily active drivers from {} to {}.", getAuthenticatedUserId(), start, end);
        return ResponseEntity.ok(marketControlService.getDailyActiveDrivers(start, end));
    }

    @GetMapping("/daily-new-clients-by-source") // Corrected path
    public ResponseEntity<List<Map<String, Object>>> getDailyNewClientsBySource(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        logger.info("ETAMIN (User ID: {}) requesting daily new clients by source from {} to {}.", getAuthenticatedUserId(), start, end);
        return ResponseEntity.ok(marketControlService.getDailyNewClientsBySource(start, end));
    }

    @GetMapping("/clients-by-district") // Corrected path
    public ResponseEntity<List<ChartDataPoint>> getClientsByDistrictDistribution() {
        logger.info("ETAMIN (User ID: {}) requesting clients by district distribution.", getAuthenticatedUserId());
        return ResponseEntity.ok(marketControlService.getClientsByDistrictDistribution());
    }

    @GetMapping("/order-status-distribution") // Corrected path
    public ResponseEntity<List<ChartDataPoint>> getOrderStatusDistribution() {
        logger.info("ETAMIN (User ID: {}) requesting order status distribution.", getAuthenticatedUserId());
        return ResponseEntity.ok(marketControlService.getOrderStatusDistribution());
    }

    @GetMapping("/daily-new-clients") // Corrected path
    public ResponseEntity<List<ChartDataPoint>> getDailyNewClients(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        logger.info("ETAMIN (User ID: {}) requesting daily new clients from {} to {}.", getAuthenticatedUserId(), start, end);
        return ResponseEntity.ok(marketControlService.getDailyNewClients(start, end));
    }

    @GetMapping("/clients-by-order-source") // Corrected path
    public ResponseEntity<List<ChartDataPoint>> getClientsByOrderSource(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        logger.info("ETAMIN (User ID: {}) requesting clients by order source from {} to {}.", getAuthenticatedUserId(), start, end);
        return ResponseEntity.ok(marketControlService.getClientsByOrderSource(start, end));
    }

    @GetMapping("/clients-by-order-source-all-time") // Corrected path
    public ResponseEntity<List<ChartDataPoint>> getClientsByOrderSourceAllTime() {
        logger.info("ETAMIN (User ID: {}) requesting clients by order source (all time).", getAuthenticatedUserId());
        return ResponseEntity.ok(marketControlService.getClientsByOrderSourceAllTime());
    }
}