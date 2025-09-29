package org.example.taxi.service;

import org.example.taxi.controller.dto.ClientProfileRequest;
import org.example.taxi.controller.dto.OrderBookingRequest;
import org.example.taxi.entity.Client;
import org.example.taxi.entity.OrderEntity;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.example.taxi.repository.ClientRepository;
import org.example.taxi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ClientService {

    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);

    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderService orderService;

    private Client getClientByAuthenticatedUserId(Long authenticatedUserId) {
        return clientRepository.findByUser_Id(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client profile not found for authenticated user."));
    }

    @Transactional(readOnly = true)
    public Client getClientProfile(Long authenticatedUserId) {
        logger.debug("Fetching client profile for user ID: {}", authenticatedUserId);
        return getClientByAuthenticatedUserId(authenticatedUserId);
    }

    @Transactional
    public Client updateClientProfile(Long authenticatedUserId, ClientProfileRequest request) {
        Client client = getClientByAuthenticatedUserId(authenticatedUserId);
        logger.info("Updating profile (text-only) for client {} (User ID: {}).", client.getId(), authenticatedUserId);

        Optional.ofNullable(request.getFirstName()).ifPresent(client::setFirstName);
        Optional.ofNullable(request.getLastName()).ifPresent(client::setLastName);
        Optional.ofNullable(request.getPreferences()).ifPresent(client::setPreferences);

        return clientRepository.save(client);
    }

    @Transactional
    public Client updateClientProfilePictureUrl(Long authenticatedUserId, String fileUrl) {
        Client client = getClientByAuthenticatedUserId(authenticatedUserId);
        logger.info("Updating profile picture URL for client {} (User ID: {}).", client.getId(), authenticatedUserId);
        client.setProfilePictureUrl(fileUrl);
        return clientRepository.save(client);
    }

    @Transactional
    public OrderEntity bookRide(Long authenticatedUserId, OrderBookingRequest request) {
        request.validateDistricts();

        // Validate LUGGAGE order requirements
        if (request.getOrderType() == OrderEntity.OrderType.LUGGAGE) {
            if (request.getLuggageContactInfo() == null || request.getLuggageContactInfo().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LUGGAGE orders must include contact information.");
            }
            if (request.getSeats() != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LUGGAGE orders must have zero seats.");
            }
            if (request.getSelectedSeats() != null && !request.getSelectedSeats().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LUGGAGE orders cannot have selected seats.");
            }
        } else {
            if (request.getSeats() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Non-LUGGAGE orders must specify a positive number of seats.");
            }
            if (request.getLuggageContactInfo() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Non-LUGGAGE orders cannot include contact information.");
            }
        }

        OrderEntity newOrder = new OrderEntity();
        newOrder.setFromDistrictId(request.getFromDistrictId());
        newOrder.setToDistrictId(request.getToDistrictId());
        newOrder.setFromLocation(request.getFromLocation());
        newOrder.setToLocation(request.getToLocation());
        newOrder.setPickupTime(request.getPickupTime());
        newOrder.setSeats(request.getSeats());
        newOrder.setOrderType(request.getOrderType());
        newOrder.setSelectedSeats(request.getSelectedSeats());
        newOrder.setLuggageContactInfo(request.getLuggageContactInfo());
        newOrder.setExtraInfo(request.getExtraInfo());

        logger.info("Client (User ID: {}) attempting to book a ride from District {} ({}) to District {} ({}).",
                authenticatedUserId, request.getFromDistrictId(), request.getFromLocation(),
                request.getToDistrictId(), request.getToLocation());
        return orderService.createOrder(newOrder, authenticatedUserId);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getClientRideHistory(Long authenticatedUserId, Optional<OrderStatus> status) {
        logger.info("Fetching ride history for client (User ID: {}) with status filter: {}.", authenticatedUserId, status.map(Enum::name).orElse("N/A"));
        return orderService.getClientOrderHistory(authenticatedUserId, status);
    }

    @Transactional(readOnly = true)
    public Optional<OrderEntity> getClientActiveOrder(Long authenticatedUserId) {
        logger.info("Fetching active order for client (User ID: {}).", authenticatedUserId);
        return orderService.getClientActiveOrder(authenticatedUserId);
    }

    @Transactional(readOnly = true)
    public OrderEntity getClientOrderDetails(Long authenticatedUserId, Long orderId) {
        logger.info("Client (User ID: {}) requesting details for order {}.", authenticatedUserId, orderId);
        return orderService.getOrderDetails(orderId, authenticatedUserId, false);
    }
}