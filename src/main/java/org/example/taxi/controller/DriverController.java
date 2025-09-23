package org.example.taxi.controller;

import org.example.taxi.controller.dto.DriverProfileRequest;
import org.example.taxi.controller.dto.DriverProfileResponse;
import org.example.taxi.controller.dto.OrderStatusUpdateRequest;
import org.example.taxi.entity.Driver;
import org.example.taxi.entity.OrderEntity;
import org.example.taxi.entity.OrderEntity.OrderStatus; // Import the enum
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.DriverService;
import org.example.taxi.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/driver")
public class DriverController {

    private static final Logger logger = LoggerFactory.getLogger(DriverController.class);

    @Autowired private DriverService driverService;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderService orderService;


    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByPhoneNumber(userDetails.getUsername())
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
    }

    @GetMapping("/profile")
    public ResponseEntity<DriverProfileResponse> getProfile() {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Fetching profile for authenticated driver (User ID: {}).", authenticatedUserId);
        return ResponseEntity.ok(DriverProfileResponse.fromEntity(driverService.getDriverProfile(authenticatedUserId)));
    }

    @PutMapping("/profile")
    public ResponseEntity<DriverProfileResponse> updateProfile(@Valid @RequestBody DriverProfileRequest request) {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Updating profile (text/district) for authenticated driver (User ID: {}).", authenticatedUserId);
        return ResponseEntity.ok(DriverProfileResponse.fromEntity(driverService.updateDriverProfile(authenticatedUserId, request)));
    }

    @PostMapping("/submit-for-approval")
    public ResponseEntity<DriverProfileResponse> submitProfileForApproval() {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Driver (User ID: {}) submitting profile for approval.", authenticatedUserId);
        return ResponseEntity.ok(DriverProfileResponse.fromEntity(driverService.submitProfileForApproval(authenticatedUserId)));
    }

    @GetMapping("/orders/feed")
    public ResponseEntity<List<OrderEntity>> getOrdersFeed(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam int maxSeats) {

        Long authenticatedUserId = getAuthenticatedUserId();
        Driver driver = driverService.getDriverProfile(authenticatedUserId);
        if (driver.getDistrict() == null || driver.getDistrict().getRegion() == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Driver's district or region not set. Please complete profile.");
        }
        Long driverDistrictId = driver.getDistrict().getId();
        Long driverRegionId = driver.getDistrict().getRegion().getId();

        logger.info("Fetching order feed for driver (User ID: {}) in District ID {} (Region ID {}), from {} to {}.", authenticatedUserId, driverDistrictId, driverRegionId, start, end);
        List<OrderEntity> orders = driverService.getAvailableOrders(authenticatedUserId, driverDistrictId, driverRegionId, start, end, maxSeats);
        if (orders.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<OrderEntity> acceptOrder(@PathVariable Long orderId) {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Driver (User ID: {}) attempting to accept order {}.", authenticatedUserId, orderId);
        return ResponseEntity.ok(driverService.acceptOrder(authenticatedUserId, orderId));
    }

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<OrderEntity> updateOrderStatus(@PathVariable Long orderId, @Valid @RequestBody OrderStatusUpdateRequest request) {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Driver (User ID: {}) attempting to update status of order {} to {}.", authenticatedUserId, orderId, request.getNewStatus().name());
        return ResponseEntity.ok(driverService.updateOrderStatus(authenticatedUserId, orderId, request.getNewStatus().name())); // Pass enum directly
    }

    @GetMapping("/history")
    public ResponseEntity<List<OrderEntity>> getDriverHistory(@RequestParam Optional<OrderStatus> status) {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Fetching driver (User ID: {}) history with status filter: {}.", authenticatedUserId, status.map(Enum::name).orElse("N/A"));
        List<OrderEntity> history = driverService.getDriverRideHistory(authenticatedUserId, status);
        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(history);
    }

    @GetMapping("/active-order")
    public ResponseEntity<List<OrderEntity>> getDriverActiveOrders() { // CRITICAL FIX: Changed return type to List
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Fetching active orders for driver (User ID: {}).", authenticatedUserId);
        List<OrderEntity> activeOrders = driverService.getDriverActiveOrders(authenticatedUserId); // Call method returning List
        if (activeOrders.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(activeOrders);
    }
}