package org.example.taxi.controller;

import org.example.taxi.controller.dto.ClientProfileRequest;
import org.example.taxi.controller.dto.ClientProfileResponse;
import org.example.taxi.controller.dto.OrderBookingRequest;
import org.example.taxi.controller.dto.OrderBookingResponse;
import org.example.taxi.entity.OrderEntity;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.s3.S3Service;
import org.example.taxi.service.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/client")
public class ClientController {

    private static final Logger logger = LoggerFactory.getLogger(ClientController.class);

    @Autowired private ClientService clientService;
    @Autowired private UserRepository userRepository;
    @Autowired private S3Service s3Service;

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByPhoneNumber(userDetails.getUsername())
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
    }

    private String getAuthenticatedUserPhoneNumber() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication.getPrincipal() instanceof String)) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated.");
    }

    @GetMapping("/profile")
    public ResponseEntity<ClientProfileResponse> getProfile() {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Fetching profile for authenticated client (User ID: {}).", authenticatedUserId);
        return ResponseEntity.ok(ClientProfileResponse.fromEntity(clientService.getClientProfile(authenticatedUserId)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ClientProfileResponse> updateProfile(@Valid @RequestBody ClientProfileRequest request) {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Updating profile (text-only) for authenticated client (User ID: {}).", authenticatedUserId);
        return ResponseEntity.ok(ClientProfileResponse.fromEntity(clientService.updateClientProfile(authenticatedUserId, request)));
    }

    @PostMapping("/uploads/profile-picture")
    public ResponseEntity<?> uploadProfilePicture(@RequestParam("file") MultipartFile file) {
        Long authenticatedUserId = getAuthenticatedUserId();
        String phoneNumber = getAuthenticatedUserPhoneNumber();
        String subDirectory = "clients/" + phoneNumber + "/profile-pictures";

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File cannot be empty."));
        }

        logger.info("Attempting to upload profile picture for client '{}' to S3.", phoneNumber);
        try {
            String fileUrl = s3Service.uploadFile(file, subDirectory);
            clientService.updateClientProfilePictureUrl(authenticatedUserId, fileUrl);

            return ResponseEntity.ok(Map.of("message", "Profile picture uploaded and profile updated successfully", "url", fileUrl));
        } catch (IOException e) {
            logger.error("IO error during file upload for client {}: {}", phoneNumber, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file for upload.", e);
        } catch (Exception e) {
            logger.error("Error during file upload for client {}: {}", phoneNumber, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderBookingResponse> bookRide(@Valid @RequestBody OrderBookingRequest request) {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Client (User ID: {}) attempting to book a ride from District {} ({}) to District {} ({}).",
                authenticatedUserId, request.getFromDistrictId(), request.getFromLocation(),
                request.getToDistrictId(), request.getToLocation());

        OrderEntity createdOrder = clientService.bookRide(authenticatedUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderBookingResponse.fromEntity(createdOrder));
    }

    @GetMapping("/orders/history")
    public ResponseEntity<List<OrderEntity>> getClientHistory(@RequestParam Optional<OrderStatus> status) {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Fetching client (User ID: {}) history with status filter: {}.", authenticatedUserId, status.map(Enum::name).orElse("N/A"));
        List<OrderEntity> history = clientService.getClientRideHistory(authenticatedUserId, status);
        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(history);
    }

    @GetMapping("/orders/active")
    public ResponseEntity<OrderEntity> getClientActiveOrder() {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Fetching active order for client (User ID: {}).", authenticatedUserId);
        return clientService.getClientActiveOrder(authenticatedUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderEntity> getClientOrderDetails(@PathVariable Long orderId) {
        Long authenticatedUserId = getAuthenticatedUserId();
        logger.info("Client (User ID: {}) requesting details for order {}.", authenticatedUserId, orderId);
        return ResponseEntity.ok(clientService.getClientOrderDetails(authenticatedUserId, orderId));
    }
}