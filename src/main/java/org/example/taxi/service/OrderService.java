package org.example.taxi.service;

import org.example.taxi.entity.District;
import org.example.taxi.entity.Driver;
import org.example.taxi.entity.OrderEntity;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.example.taxi.entity.Price;
import org.example.taxi.repository.DistrictRepository;
import org.example.taxi.repository.DriverRepository;
import org.example.taxi.repository.OrderRepository;
import org.example.taxi.repository.PriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Autowired private OrderRepository orderRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private PriceRepository priceRepository;
    @Autowired private DistrictRepository districtRepository;

    private static final BigDecimal APP_FEE_PER_PERSON = BigDecimal.valueOf(20);
    private static final BigDecimal APP_FEE_LUGGAGE = BigDecimal.valueOf(10);
    private static final Price DEFAULT_PRICE_CONFIG = new Price(
            0L, null, null,
            BigDecimal.valueOf(150000), BigDecimal.valueOf(150000), BigDecimal.valueOf(200000),
            BigDecimal.valueOf(20000), BigDecimal.valueOf(10000),
            BigDecimal.valueOf(10000)
    );

    @Transactional
    public OrderEntity createOrder(OrderEntity order, Long userId) {
        order.setUserId(userId);
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);

        // Validate and set fields based on order type
        if (order.getOrderType() == OrderEntity.OrderType.LUGGAGE) {
            if (order.getLuggageContactInfo() == null || order.getLuggageContactInfo().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LUGGAGE orders must include contact information.");
            }
            order.setSeats(0); // Ignore seats for LUGGAGE
            order.setSelectedSeats(null); // Ignore selected seats for LUGGAGE
        } else {
            // For non-LUGGAGE orders (REGULAR, WOMEN_DRIVER, PREMIUM_REGULAR)
            if (order.getSeats() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Non-LUGGAGE orders must specify a positive number of seats.");
            }
            order.setLuggageContactInfo(null); // Clear contact info for non-LUGGAGE orders
        }

        Price routePrice = getPriceForRoute(order.getFromDistrictId(), order.getToDistrictId());
        order.setTotalCost(calculateTotalCost(order, routePrice));

        OrderEntity savedOrder = orderRepository.save(order);
        logger.info("New order created (ID: {}) for client {} from District {} ({}) to District {} ({}). Status: PENDING, Cost: {}.",
                savedOrder.getId(), userId != null ? userId.toString() : "Guest",
                order.getFromDistrictId(), order.getFromLocation(),
                order.getToDistrictId(), order.getToLocation(),
                order.getTotalCost());
        return savedOrder;
    }

    private Price getPriceForRoute(Long fromDistrictId, Long toDistrictId) {
        District fromDistrict = districtRepository.findById(fromDistrictId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid From District ID: " + fromDistrictId));
        District toDistrict = districtRepository.findById(toDistrictId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid To District ID: " + toDistrictId));

        return priceRepository.findByFromDistrictAndToDistrict(fromDistrict, toDistrict)
                .orElseGet(() -> {
                    logger.warn("No specific price configured for route from {} to {}. Using default price.", fromDistrict.getName(), toDistrict.getName());
                    return DEFAULT_PRICE_CONFIG;
                });
    }

    private BigDecimal calculateTotalCost(OrderEntity order, Price routePrice) {
        BigDecimal base;
        if (order.getOrderType() == OrderEntity.OrderType.PREMIUM_REGULAR) {
            base = routePrice.getPremiumPricePerSeat();
        } else if (order.getOrderType() == OrderEntity.OrderType.WOMEN_DRIVER) {
            base = routePrice.getWomenDriverPricePerSeat();
        } else if (order.getOrderType() == OrderEntity.OrderType.LUGGAGE) {
            return routePrice.getLuggagePrice(); // LUGGAGE orders use luggagePrice only
        } else {
            base = routePrice.getBasePricePerSeat();
        }
        BigDecimal seatCost = base.multiply(BigDecimal.valueOf(order.getSeats()));
        BigDecimal selectionCost = BigDecimal.ZERO;

        if (order.getSelectedSeats() != null) {
            for (String seat : order.getSelectedSeats()) {
                selectionCost = selectionCost.add("front".equals(seat) ? routePrice.getFrontSeatExtraFee() : routePrice.getOtherSeatExtraFee());
            }
        }

        return seatCost.add(selectionCost);
    }

    @Transactional
    public void deductAppFee(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for fee deduction."));

        if (order.getDriverId() == null) {
            logger.error("Attempted to deduct fee for order {} with no assigned driver. This should not happen for a COMPLETED order.", orderId);
            return;
        }

        Driver driver = driverRepository.findById(order.getDriverId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found for fee deduction."));

        BigDecimal fee = BigDecimal.ZERO;

        fee = fee.add(APP_FEE_PER_PERSON.multiply(BigDecimal.valueOf(order.getSeats())));

        if (order.getOrderType() == OrderEntity.OrderType.LUGGAGE) {
            fee = fee.add(APP_FEE_LUGGAGE);
        }

        if (driver.getWalletBalance().compareTo(fee) < 0) {
            logger.warn("Driver {} (ID: {}) has insufficient balance ({}) to cover app fee ({}). Setting wallet balance to zero and continuing.",
                    driver.getUser().getPhoneNumber(), driver.getId(), driver.getWalletBalance(), fee);
            driver.setWalletBalance(BigDecimal.ZERO);
        } else {
            driver.setWalletBalance(driver.getWalletBalance().subtract(fee));
        }

        driverRepository.save(driver);
        logger.info("App fee of {} deducted from driver {}'s wallet for order {}. New balance: {}",
                fee, driver.getId(), orderId, driver.getWalletBalance());
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> findPendingOrdersForDriverFeed(Long driverDistrictId, Long driverRegionId, LocalDateTime start, LocalDateTime end, int maxSeats) {
        logger.debug("Searching for pending orders for driver feed: driverDistrictId={}, driverRegionId={}, start={}, end={}, maxSeats={}", driverDistrictId, driverRegionId, start, end, maxSeats);

        District driverDistrict = districtRepository.findById(driverDistrictId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Driver's district not found."));

        List<District> districtsInDriverRegion = districtRepository.findByRegion(driverDistrict.getRegion());
        List<Long> districtIdsInDriverRegion = districtsInDriverRegion.stream()
                .map(District::getId)
                .collect(Collectors.toList());

        return orderRepository.findPendingOrdersForDriverFeed(
                OrderStatus.PENDING, districtIdsInDriverRegion, driverDistrictId, start, end, maxSeats);
    }

    @Transactional
    public OrderEntity acceptOrder(Long orderId, Long driverId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order cannot be accepted as it's not in PENDING status. Current status: " + order.getStatus());
        }
        if (order.getDriverId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already assigned to another driver (ID: " + order.getDriverId() + ").");
        }

        order.setDriverId(driverId);
        order.setStatus(OrderStatus.ACCEPTED);
        logger.info("Driver {} accepted order {}. Order status changed to ACCEPTED.", driverId, orderId);
        return orderRepository.save(order);
    }

    @Transactional
    public OrderEntity updateOrderStatus(Long orderId, OrderStatus newStatus, Long driverId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if (order.getDriverId() == null || !order.getDriverId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver " + driverId + " is not authorized to update order " + orderId + ".");
        }

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update a completed or canceled order. Current status: " + currentStatus);
        }

        switch (newStatus) {
            case EN_ROUTE:
                if (currentStatus != OrderStatus.ACCEPTED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must be ACCEPTED before going EN_ROUTE. Current status: " + currentStatus);
                }
                break;
            case COMPLETED:
                if (currentStatus != OrderStatus.EN_ROUTE) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must be EN_ROUTE before being COMPLETED. Current status: " + currentStatus);
                }
                break;
            case CANCELED:
                if (currentStatus == OrderStatus.PENDING || currentStatus == OrderStatus.ACCEPTED || currentStatus == OrderStatus.EN_ROUTE) {
                    break;
                }
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or unsupported status transition to: " + newStatus + " from " + currentStatus);
        }

        order.setStatus(newStatus);
        OrderEntity updatedOrder = orderRepository.save(order);

        if (newStatus == OrderStatus.COMPLETED) {
            deductAppFee(orderId);
        }
        logger.info("Order {} status updated to {} by driver {}.", orderId, newStatus, driverId);
        return updatedOrder;
    }

    @Transactional(readOnly = true)
    public OrderEntity getOrderDetails(Long orderId, Long userId, boolean isDriver) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with ID: " + orderId));

        if (isDriver) {
            if (order.getDriverId() == null || !order.getDriverId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver " + userId + " not authorized for order " + orderId + ".");
            }
        } else {
            if (order.getUserId() == null || !order.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client " + userId + " not authorized for order " + orderId + ".");
            }
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getClientOrderHistory(Long clientId, Optional<OrderStatus> status) {
        if (status.isPresent()) {
            return orderRepository.findByUserIdAndStatusOrderByPickupTimeDesc(clientId, status.get());
        }
        return orderRepository.findByUserIdOrderByPickupTimeDesc(clientId);
    }

    @Transactional(readOnly = true)
    public Optional<OrderEntity> getClientActiveOrder(Long clientId) {
        List<OrderStatus> activeStatuses = List.of(OrderStatus.ACCEPTED, OrderStatus.EN_ROUTE);
        return orderRepository.findByUserIdAndStatusIn(clientId, activeStatuses);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getDriverOrderHistory(Long driverId, Optional<OrderStatus> status) {
        if (status.isPresent()) {
            return orderRepository.findByDriverIdAndStatusOrderByPickupTimeDesc(driverId, status.get());
        }
        return orderRepository.findByDriverIdOrderByPickupTimeDesc(driverId);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getDriverActiveOrder(Long driverId) {
        List<OrderStatus> activeStatuses = List.of(OrderStatus.ACCEPTED, OrderStatus.EN_ROUTE);
        return orderRepository.findByDriverIdAndStatusIn(driverId, activeStatuses);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getAllOrdersByStatus(List<OrderStatus> statuses) {
        logger.debug("Operator requesting all orders with statuses: {}", statuses);
        return orderRepository.findByStatusIn(statuses);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getIncompleteOrdersOlderThan(LocalDateTime timeThreshold) {
        return orderRepository.findIncompleteAfter7Hours(timeThreshold);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getUnassignedPendingOrders(Optional<Long> fromDistrictId, Optional<String> fromLocation, Optional<LocalDateTime> start, Optional<LocalDateTime> end) {
        String locationFilter = fromLocation.orElse("");
        if (fromDistrictId.isPresent() && start.isPresent() && end.isPresent()) {
            return orderRepository.findUnassignedOrdersInFromDistrictAndTime(OrderStatus.PENDING, fromDistrictId.get(), start.get(), end.get(), locationFilter);
        }
        return orderRepository.findByStatusAndDriverIdIsNull(OrderStatus.PENDING);
    }

    @Transactional
    public OrderEntity manualAssignOrder(Long orderId, Long driverId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with ID: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order cannot be manually assigned as it's not in PENDING status. Current status: " + order.getStatus());
        }
        if (order.getDriverId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + orderId + " is already assigned to driver " + order.getDriverId() + ".");
        }
        driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver with ID " + driverId + " not found."));

        order.setDriverId(driverId);
        order.setStatus(OrderStatus.ACCEPTED);
        logger.info("Operator manually assigned order {} to driver {}. Status changed to ACCEPTED.", orderId, driverId);
        return orderRepository.save(order);
    }
}