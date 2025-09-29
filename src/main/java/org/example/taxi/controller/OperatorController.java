package org.example.taxi.controller;

import org.example.taxi.controller.dto.DriverCreationRequest;
import org.example.taxi.controller.dto.DriverProfileResponse;
import org.example.taxi.controller.dto.OperatorOrderCreationRequest;
import org.example.taxi.controller.dto.OrderStatusUpdateRequest;
import org.example.taxi.entity.Driver;
import org.example.taxi.entity.OrderEntity;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.OperatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/operator")
public class OperatorController {

    private static final Logger logger = LoggerFactory.getLogger(OperatorController.class);

    @Autowired
    private OperatorService operatorService;
    @Autowired
    private UserRepository userRepository;

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated.");
        }
        String phoneNumber = authentication.getName(); // JWT sets phoneNumber as the principal
        return userRepository.findByPhoneNumber(phoneNumber)
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated operator user not found in database."));
    }

    // --- Driver Approval & Management ---

    @GetMapping("/drivers")
    public ResponseEntity<Page<DriverProfileResponse>> getAllDrivers(@PageableDefault(size = 10) Pageable pageable) {
        logger.info("Operator (User ID: {}) requesting all drivers (page: {}, size: {}).", getAuthenticatedUserId(), pageable.getPageNumber(), pageable.getPageSize());
        Page<DriverProfileResponse> driversPage = operatorService.getAllDrivers(pageable)
                .map(DriverProfileResponse::fromEntity);
        return ResponseEntity.ok(driversPage);
    }

    @GetMapping("/drivers/pending")
    public ResponseEntity<List<DriverProfileResponse>> getPendingDrivers() {
        logger.info("Operator (User ID: {}) requesting list of pending drivers.", getAuthenticatedUserId());
        List<DriverProfileResponse> pendingDrivers = operatorService.getPendingDriverApprovals().stream()
                .map(DriverProfileResponse::fromEntity)
                .collect(Collectors.toList());

        if (pendingDrivers.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(pendingDrivers);
    }

    @PutMapping("/drivers/{driverId}/approve")
    public ResponseEntity<DriverProfileResponse> approveDriver(@PathVariable Long driverId) {
        logger.info("Operator (User ID: {}) approving driver {}.", getAuthenticatedUserId(), driverId);
        return ResponseEntity.ok(DriverProfileResponse.fromEntity(operatorService.approveDriver(driverId)));
    }

    @PutMapping("/drivers/{driverId}/reject")
    public ResponseEntity<DriverProfileResponse> rejectDriver(@PathVariable Long driverId) {
        logger.info("Operator (User ID: {}) rejecting driver {}.", getAuthenticatedUserId(), driverId);
        return ResponseEntity.ok(DriverProfileResponse.fromEntity(operatorService.rejectDriver(driverId)));
    }

    @PostMapping(value = "/drivers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DriverProfileResponse> createDriver(@Valid @ModelAttribute DriverCreationRequest request) {
        logger.info("Operator (User ID: {}) attempting to create new driver with phone: {}.", getAuthenticatedUserId(), request.getPhoneNumber());
        Driver createdDriver = operatorService.createDriver(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DriverProfileResponse.fromEntity(createdDriver));
    }

    // --- Order Management ---

    @PostMapping("/orders")
    public ResponseEntity<OrderEntity> createOrder(@Valid @RequestBody OperatorOrderCreationRequest request) {
        logger.info("Operator (User ID: {}) creating new order for client phone: {}.", getAuthenticatedUserId(), request.getClientPhoneNumber());
        OrderEntity createdOrder = operatorService.createOrderByOperator(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
    }

    @GetMapping("/orders/active")
    public ResponseEntity<List<OrderEntity>> getActiveOrders() {
        logger.info("Operator (User ID: {}) requesting active orders list.", getAuthenticatedUserId());
        List<OrderEntity> activeOrders = operatorService.getOperatorActiveOrders();
        if (activeOrders.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(activeOrders);
    }

    @GetMapping("/orders/stuck")
    public ResponseEntity<List<OrderEntity>> getStuckOrders(@RequestParam(defaultValue = "7") int hoursAgo) {
        logger.info("Operator (User ID: {}) requesting stuck orders older than {} hours.", getAuthenticatedUserId(), hoursAgo);
        List<OrderEntity> stuckOrders = operatorService.getStuckOrders(hoursAgo);
        if (stuckOrders.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(stuckOrders);
    }

    @PutMapping("/orders/{orderId}/assign/{driverId}")
    public ResponseEntity<OrderEntity> manualAssignOrder(@PathVariable Long orderId, @PathVariable Long driverId) {
        logger.info("Operator (User ID: {}) manually assigning order {} to driver {}.", getAuthenticatedUserId(), orderId, driverId);
        OrderEntity assignedOrder = operatorService.manualAssignOrder(orderId, driverId);
        return ResponseEntity.ok(assignedOrder);
    }

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<OrderEntity> operatorUpdateOrderStatus(@PathVariable Long orderId, @Valid @RequestBody OrderStatusUpdateRequest request) {
        logger.info("Operator (User ID: {}) manually updating status of order {} to {}.", getAuthenticatedUserId(), orderId, request.getNewStatus());
        OrderEntity updatedOrder = operatorService.operatorUpdateOrderStatus(orderId, request.getNewStatus());
        return ResponseEntity.ok(updatedOrder);
    }
}