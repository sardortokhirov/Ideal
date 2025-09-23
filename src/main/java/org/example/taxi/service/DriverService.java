package org.example.taxi.service;

import org.example.taxi.controller.dto.DriverProfileRequest;
import org.example.taxi.entity.District;
import org.example.taxi.entity.Driver;
import org.example.taxi.entity.OrderEntity;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.example.taxi.repository.DistrictRepository;
import org.example.taxi.repository.DriverRepository;
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
public class DriverService {

    private static final Logger logger = LoggerFactory.getLogger(DriverService.class);

    @Autowired private DriverRepository driverRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DistrictRepository districtRepository;
    @Autowired private OrderService orderService;

    private Driver getDriverByAuthenticatedUserId(Long authenticatedUserId) {
        return driverRepository.findByUser_Id(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver profile not found for authenticated user."));
    }

    @Transactional(readOnly = true)
    public Driver getDriverProfile(Long authenticatedUserId) {
        logger.debug("Fetching driver profile for user ID: {}", authenticatedUserId);
        return getDriverByAuthenticatedUserId(authenticatedUserId);
    }

    @Transactional
    public Driver updateDriverProfile(Long authenticatedUserId, DriverProfileRequest request) {
        Driver driver = getDriverByAuthenticatedUserId(authenticatedUserId);
        logger.info("Updating profile (text/district) for driver {} (User ID: {}).", driver.getId(), authenticatedUserId);

        Optional.ofNullable(request.getFirstName()).ifPresent(driver::setFirstName);
        Optional.ofNullable(request.getLastName()).ifPresent(driver::setLastName);
        Optional.ofNullable(request.getDriverLicenseNumber()).ifPresent(driver::setDriverLicenseNumber);
        Optional.ofNullable(request.getCarName()).ifPresent(driver::setCarName);
        Optional.ofNullable(request.getCarNumber()).ifPresent(driver::setCarNumber);

        Optional.ofNullable(request.getDistrictId()).ifPresent(districtId -> {
            District district = districtRepository.findById(districtId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid District ID provided: " + districtId));
            driver.setDistrict(district);
            logger.debug("Driver {} (User ID: {}) district updated to ID: {}", driver.getId(), authenticatedUserId, districtId);
        });

        boolean isComplete = checkDriverProfileCompleteness(driver);

        if (isComplete && (driver.getApprovalStatus() == Driver.ApprovalStatus.NONE ||
                driver.getApprovalStatus() == Driver.ApprovalStatus.REJECTED)) {
            driver.setApprovalStatus(Driver.ApprovalStatus.PENDING);
            logger.info("Driver {} (User ID: {}) profile is now complete and submitted for operator approval. Status set to PENDING.", driver.getId(), authenticatedUserId);
        } else if (!isComplete && driver.getApprovalStatus() == Driver.ApprovalStatus.PENDING) {
            logger.warn("Driver {} (User ID: {}) profile was PENDING but is now incomplete. Status remains PENDING, but requires attention.", driver.getId(), authenticatedUserId);
        } else {
            logger.debug("Driver {} (User ID: {}) profile updated. Completeness: {}, Approval Status: {}", driver.getId(), authenticatedUserId, isComplete, driver.getApprovalStatus());
        }

        return driverRepository.save(driver);
    }

    @Transactional
    public Driver updateDriverFileUrl(Long authenticatedUserId, String fileType, String fileUrl) {
        Driver driver = getDriverByAuthenticatedUserId(authenticatedUserId);
        logger.info("Updating {} URL for driver {} (User ID: {}).", fileType, driver.getId(), authenticatedUserId);

        switch (fileType) {
            case "profilePicture":
                driver.setProfilePictureUrl(fileUrl);
                break;
            case "driverLicensePicture":
                driver.setDriverLicensePictureUrl(fileUrl);
                break;
            case "carPicture":
                driver.setCarPictureUrl(fileUrl);
                break;
            case "passportPicture":
                driver.setPassportPictureUrl(fileUrl);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file type for URL update: " + fileType);
        }

        boolean isComplete = checkDriverProfileCompleteness(driver);
        if (isComplete && (driver.getApprovalStatus() == Driver.ApprovalStatus.NONE ||
                driver.getApprovalStatus() == Driver.ApprovalStatus.REJECTED)) {
            driver.setApprovalStatus(Driver.ApprovalStatus.PENDING);
            logger.info("Driver {} (User ID: {}) profile is now complete after {} update and submitted for approval. Status set to PENDING.", driver.getId(), authenticatedUserId, fileType);
        } else {
            logger.debug("Driver {} (User ID: {}) file URL updated. Completeness: {}, Approval Status: {}", driver.getId(), authenticatedUserId, isComplete, driver.getApprovalStatus());
        }

        return driverRepository.save(driver);
    }

    @Transactional
    public Driver submitProfileForApproval(Long authenticatedUserId) {
        Driver driver = getDriverByAuthenticatedUserId(authenticatedUserId);

        if (!checkDriverProfileCompleteness(driver)) {
            logger.warn("Driver {} (User ID: {}) attempted to submit profile for approval but profile is incomplete.", driver.getId(), authenticatedUserId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver profile is incomplete. Please fill all required fields before submitting for approval.");
        }

        if (driver.getApprovalStatus() == Driver.ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver profile is already PENDING operator review.");
        }

        if (driver.getApprovalStatus() == Driver.ApprovalStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver profile is already APPROVED.");
        }

        driver.setApprovalStatus(Driver.ApprovalStatus.PENDING);
        logger.info("Driver {} (User ID: {}) explicitly submitted profile for approval. Status set to PENDING.", driver.getId(), authenticatedUserId);

        return driverRepository.save(driver);
    }

    private boolean checkDriverProfileCompleteness(Driver driver) {
        return driver.getFirstName() != null && !driver.getFirstName().isBlank() &&
                driver.getLastName() != null && !driver.getLastName().isBlank() &&
                driver.getProfilePictureUrl() != null && !driver.getProfilePictureUrl().isBlank() &&
                driver.getDriverLicenseNumber() != null && !driver.getDriverLicenseNumber().isBlank() &&
                driver.getDriverLicensePictureUrl() != null && !driver.getDriverLicensePictureUrl().isBlank() &&
                driver.getCarName() != null && !driver.getCarName().isBlank() &&
                driver.getCarNumber() != null && !driver.getCarNumber().isBlank() &&
                driver.getCarPictureUrl() != null && !driver.getCarPictureUrl().isBlank() &&
                driver.getPassportPictureUrl() != null && !driver.getPassportPictureUrl().isBlank() &&
                driver.getDistrict() != null;
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getAvailableOrders(Long authenticatedUserId, Long driverDistrictId, Long driverRegionId, LocalDateTime start, LocalDateTime end, int maxSeats) {
        boolean isApprovedAndComplete = driverRepository.isFullyCredentialed(authenticatedUserId, Driver.ApprovalStatus.ACCEPTED);

        if (!isApprovedAndComplete) {
            logger.warn("Driver (User ID: {}) is not approved or has an incomplete profile. Denying access to order feed.", authenticatedUserId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver must be approved with a complete profile to view orders.");
        }

        logger.info("Driver (User ID: {}) is approved and has a complete profile. Fetching available orders for district ID: {} (Region ID: {}).", authenticatedUserId, driverDistrictId, driverRegionId);
        return orderService.findPendingOrdersForDriverFeed(driverDistrictId, driverRegionId, start, end, maxSeats);
    }

    @Transactional
    public OrderEntity acceptOrder(Long authenticatedUserId, Long orderId) {
        Driver driver = getDriverByAuthenticatedUserId(authenticatedUserId);
        logger.info("Driver {} (User ID: {}) attempting to accept order {}.", driver.getId(), authenticatedUserId, orderId);
        return orderService.acceptOrder(orderId, driver.getId());
    }

    @Transactional
    public OrderEntity updateOrderStatus(Long authenticatedUserId, Long orderId, String newStatus) {
        Driver driver = getDriverByAuthenticatedUserId(authenticatedUserId);
        logger.info("Driver {} (User ID: {}) attempting to update status of order {} to {}.", authenticatedUserId, orderId, newStatus);
        return orderService.updateOrderStatus(orderId, OrderStatus.valueOf(newStatus), driver.getId());
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getDriverRideHistory(Long authenticatedUserId, Optional<OrderStatus> status) {
        Driver driver = getDriverByAuthenticatedUserId(authenticatedUserId);
        return orderService.getDriverOrderHistory(driver.getId(), status);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getDriverActiveOrders(Long authenticatedUserId) { // CRITICAL FIX: Changed return type to List
        Driver driver = getDriverByAuthenticatedUserId(authenticatedUserId);
        logger.info("Fetching active orders for driver (User ID: {}).", authenticatedUserId);
        return orderService.getDriverActiveOrder(driver.getId()); // orderService.getDriverActiveOrder now returns List
    }
}