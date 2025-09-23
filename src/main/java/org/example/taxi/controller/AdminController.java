package org.example.taxi.controller;

import org.example.taxi.controller.dto.*;
import org.example.taxi.entity.District;
import org.example.taxi.entity.Price;
import org.example.taxi.entity.User;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired private AdminService adminService;
    @Autowired private UserRepository userRepository;

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByPhoneNumber(userDetails.getUsername())
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated admin user not found in database."));
    }

    // --- Operator Management (Admin Exclusive) ---

    @PostMapping("/operators")
    public ResponseEntity<OperatorResponse> createOperator(@Valid @RequestBody OperatorCreationRequest request) {
        logger.info("Admin (User ID: {}) attempting to create new operator with phone: {}.", getAuthenticatedUserId(), request.getPhoneNumber());
        User createdOperator = adminService.createOperator(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new OperatorResponse(
                createdOperator.getId(),
                createdOperator.getPhoneNumber(),
                createdOperator.getFirstName(),
                createdOperator.getLastName(),
                createdOperator.getUserType().name(),
                "Operator created successfully."
        ));
    }

    @GetMapping("/operators")
    public ResponseEntity<Page<OperatorResponse>> getAllOperators(@PageableDefault(size = 10, sort = "id,asc") Pageable pageable) {
        logger.info("Admin (User ID: {}) requesting all operators with pagination: {}.", getAuthenticatedUserId(), pageable);
        return ResponseEntity.ok(adminService.getAllOperators(pageable));
    }

    // --- Pricing Configuration (Admin Exclusive) ---

    @PostMapping("/prices")
    public ResponseEntity<PriceResponse> createOrUpdatePriceConfig(@Valid @RequestBody PriceConfigUpdate request) {
        logger.info("Admin (User ID: {}) setting price config for from {} to {}.", getAuthenticatedUserId(), request.getFromDistrictId(), request.getToDistrictId());
        Price price = adminService.createOrUpdatePriceConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PriceResponse.fromEntity(price));
    }

    @GetMapping("/prices")
    public ResponseEntity<List<PriceResponse>> getAllPriceConfigs() {
        logger.info("Admin (User ID: {}) retrieving all price configurations.", getAuthenticatedUserId());
        List<PriceResponse> prices = adminService.getAllPriceConfigs().stream()
                .map(PriceResponse::fromEntity)
                .collect(Collectors.toList());
        if (prices.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(prices);
    }

    @GetMapping("/prices/{priceId}")
    public ResponseEntity<PriceResponse> getPriceConfigById(@PathVariable Long priceId) {
        logger.info("Admin (User ID: {}) retrieving price configuration with ID: {}.", getAuthenticatedUserId(), priceId);
        return adminService.getPriceConfigById(priceId)
                .map(PriceResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Price configuration not found with ID: " + priceId));
    }

    @DeleteMapping("/prices/{priceId}")
    public ResponseEntity<Void> deletePriceConfig(@PathVariable Long priceId) {
        logger.info("Admin (User ID: {}) deleting price configuration with ID: {}.", getAuthenticatedUserId(), priceId);
        adminService.deletePriceConfig(priceId);
        return ResponseEntity.noContent().build();
    }

    // --- Order Management Endpoints (Admin Exclusive) ---
    // Note: These methods use the general analytics methods from AdminService, but are exposed as ADMIN APIs.

    @GetMapping("/orders")
    public ResponseEntity<Page<DetailedOrderResponse>> getOrders(
            @Valid OrderFilterRequest filter,
            @PageableDefault(size = 10, sort = "createdAt,desc") Pageable pageable) {

        logger.info("Admin (User ID: {}) requesting orders with filter: {} and pagination: {}.", getAuthenticatedUserId(), filter, pageable);
        return ResponseEntity.ok(adminService.getOrders(filter, pageable));
    }

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<DetailedOrderResponse> updateOrderStatusByAdmin(
            @PathVariable Long orderId,
            @RequestParam String newStatus) {
        logger.info("Admin (User ID: {}) manually updating status of order {} to {}.", getAuthenticatedUserId(), orderId, newStatus);
        return ResponseEntity.ok(adminService.updateOrderStatusByAdmin(orderId, newStatus));
    }
}