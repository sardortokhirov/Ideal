package org.example.taxi.service;

import org.example.taxi.controller.dto.DriverCreationRequest;
import org.example.taxi.controller.dto.OperatorOrderCreationRequest;
import org.example.taxi.entity.*;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.example.taxi.repository.*;
import org.example.taxi.s3.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OperatorService {

    private static final Logger logger = LoggerFactory.getLogger(OperatorService.class);

    @Autowired private DriverRepository driverRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private DistrictRepository districtRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private S3Service s3Service;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<Driver> getAllDrivers(Pageable pageable) {
        logger.info("Operator requesting all drivers with page: {}, size: {}.", pageable.getPageNumber(), pageable.getPageSize());
        return driverRepository.findAll(pageable);
    }

    @Transactional
    public OrderEntity operatorUpdateOrderStatus(Long orderId, OrderStatus newStatusEnum) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update a completed or canceled order. Current status: " + currentStatus.name());
        }

        order.setStatus(newStatusEnum);
        OrderEntity updatedOrder = orderRepository.save(order);

        if (newStatusEnum == OrderStatus.COMPLETED) {
            orderService.deductAppFee(orderId);
        }
        logger.info("Operator manually updated order {} status to {}. Previously: {}.", orderId, newStatusEnum.name(), currentStatus.name());
        return updatedOrder;
    }

    @Transactional(readOnly = true)
    public List<Driver> getPendingDriverApprovals() {
        return driverRepository.findAll().stream()
                .filter(driver -> driver.getApprovalStatus() == Driver.ApprovalStatus.PENDING)
                .collect(Collectors.toList());
    }

    @Transactional
    public Driver approveDriver(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));

        if (driver.getApprovalStatus() != Driver.ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver " + driver.getId() + " is not in PENDING status for approval. Current status: " + driver.getApprovalStatus());
        }

        driver.setApprovalStatus(Driver.ApprovalStatus.ACCEPTED);
        logger.info("Driver {} approved by operator. Status set to ACCEPTED.", driver.getId());

        return driverRepository.save(driver);
    }

    @Transactional
    public Driver rejectDriver(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));

        if (driver.getApprovalStatus() != Driver.ApprovalStatus.PENDING) {
            logger.warn("Attempt to reject driver {} who is not in PENDING status. Current status: {}", driver.getId(), driver.getApprovalStatus());
        }

        driver.setApprovalStatus(Driver.ApprovalStatus.REJECTED);
        logger.info("Driver {} rejected by operator. Status set to REJECTED.", driver.getId());

        return driverRepository.save(driver);
    }

    @Transactional
    public Driver createDriver(DriverCreationRequest request) {
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this phone number already exists.");
        }
        if (driverRepository.findByCarNumber(request.getCarNumber()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A driver with this car number already exists.");
        }

        District district = districtRepository.findById(request.getDistrictId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid District ID provided: " + request.getDistrictId()));

        String profilePictureUrl;
        String driverLicensePictureUrl;
        String carPictureUrl;
        String passportPictureUrl;

        try {
            String phoneNumberForS3 = request.getPhoneNumber().replaceAll("[^0-9]", "");
            profilePictureUrl = s3Service.uploadFile(request.getProfilePictureFile(), "drivers/" + phoneNumberForS3 + "/profile-pictures");
            driverLicensePictureUrl = s3Service.uploadFile(request.getDriverLicensePictureFile(), "drivers/" + phoneNumberForS3 + "/licenses");
            carPictureUrl = s3Service.uploadFile(request.getCarPictureFile(), "drivers/" + phoneNumberForS3 + "/cars");
            passportPictureUrl = s3Service.uploadFile(request.getPassportPictureFile(), "drivers/" + phoneNumberForS3 + "/passports");
        } catch (IOException e) {
            logger.error("IO error during S3 upload for new driver {}: {}", request.getPhoneNumber(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file for S3 upload.", e);
        } catch (Exception e) {
            logger.error("S3 error during file upload for new driver {}: {}", request.getPhoneNumber(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to upload files to S3: " + e.getMessage(), e);
        }

        User newUser = new User();
        newUser.setPhoneNumber(request.getPhoneNumber());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setUserType(User.UserType.DRIVER);
        newUser = userRepository.save(newUser);

        Driver newDriver = new Driver();
        newDriver.setUser(newUser);
        newDriver.setFirstName(request.getFirstName());
        newDriver.setLastName(request.getLastName());
        newDriver.setProfilePictureUrl(profilePictureUrl);
        newDriver.setDriverLicenseNumber(request.getDriverLicenseNumber());
        newDriver.setDriverLicensePictureUrl(driverLicensePictureUrl);
        newDriver.setCarName(request.getCarName());
        newDriver.setCarNumber(request.getCarNumber());
        newDriver.setCarPictureUrl(carPictureUrl);
        newDriver.setPassportPictureUrl(passportPictureUrl);
        newDriver.setDistrict(district);
        newDriver.setApprovalStatus(Driver.ApprovalStatus.ACCEPTED);

        newDriver = driverRepository.save(newDriver);
        logger.info("New DRIVER user and profile created by operator: userId={}, driverId={}, phoneNumber={}", newUser.getId(), newDriver.getId(), request.getPhoneNumber());

        return newDriver;
    }

    @Transactional
    public OrderEntity createOrderByOperator(OperatorOrderCreationRequest request) {
        Long clientId = null;
        String clientPhoneNumber = request.getClientPhoneNumber();

        Optional<User> existingUserOpt = userRepository.findByPhoneNumber(clientPhoneNumber);

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (existingUser.getUserType() != User.UserType.CLIENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User with phone number " + clientPhoneNumber + " is not a CLIENT. User type: " + existingUser.getUserType());
            }
            Client client = clientRepository.findByUser_Id(existingUser.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client profile not found for user " + existingUser.getPhoneNumber()));
            clientId = client.getUser().getId();
            logger.info("Operator creating order for existing client: {} (User ID: {})", clientPhoneNumber, clientId);
        } else {
            logger.info("Operator creating order for new/guest client: {}", clientPhoneNumber);
            User newClientUser = new User();
            newClientUser.setPhoneNumber(clientPhoneNumber);
            newClientUser.setPassword(passwordEncoder.encode("guest_password_" + LocalDateTime.now().getNano()));
            newClientUser.setFirstName(request.getClientFirstName() != null ? request.getClientFirstName() : "Guest");
            newClientUser.setLastName(request.getClientLastName() != null ? request.getClientLastName() : "User");
            newClientUser.setUserType(User.UserType.CLIENT);
            newClientUser = userRepository.save(newClientUser);

            Client newClient = new Client();
            newClient.setUser(newClientUser);
            newClient.setFirstName(newClientUser.getFirstName());
            newClient.setLastName(newClientUser.getLastName());
            newClient = clientRepository.save(newClient);
            clientId = newClient.getUser().getId();
            logger.info("Created new client (User ID: {}) for order: {}", clientId, clientPhoneNumber);
        }

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

        OrderEntity createdOrder = orderService.createOrder(newOrder, clientId);

        if (request.getDriverId() != null) {
            createdOrder = orderService.manualAssignOrder(createdOrder.getId(), request.getDriverId());
            logger.info("Operator manually assigned newly created order {} to driver {}.", createdOrder.getId(), request.getDriverId());
        }

        return createdOrder;
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getOperatorActiveOrders() {
        List<OrderStatus> activeOrderStatuses = List.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.EN_ROUTE);
        logger.debug("Operator requesting active orders with statuses: {}", activeOrderStatuses);
        return orderService.getAllOrdersByStatus(activeOrderStatuses);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getStuckOrders(int hoursAgo) {
        LocalDateTime timeThreshold = LocalDateTime.now().minusHours(hoursAgo);
        logger.debug("Operator requesting incomplete orders older than {} hours (before {}).", hoursAgo, timeThreshold);
        return orderService.getIncompleteOrdersOlderThan(timeThreshold);
    }

    @Transactional
    public OrderEntity manualAssignOrder(Long orderId, Long driverId) {
        logger.info("Operator service initiating manual assignment of order {} to driver {}.", orderId, driverId);
        return orderService.manualAssignOrder(orderId, driverId);
    }
}