package org.example.taxi.service;

import org.example.taxi.controller.dto.*;
import org.example.taxi.controller.dto.DriverPerformanceResponse.DriverOverview;
import org.example.taxi.entity.*;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.example.taxi.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {
    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PriceRepository priceRepository;
    @Autowired private DistrictRepository districtRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OperatorService operatorService;
    @Autowired private OrderService orderService;

    private static final BigDecimal APP_FEE_PER_PASSENGER = BigDecimal.valueOf(20);
    private static final BigDecimal APP_FEE_LUGGAGE = BigDecimal.valueOf(10);
    private static final BigDecimal COMPANY_PASSENGER_SHARE_PERCENT = BigDecimal.valueOf(0.15); // 15%
    private static final BigDecimal COMPANY_LUGGAGE_SHARE_PERCENT = BigDecimal.valueOf(1.00); // 100%

    @Transactional
    public User createOperator(OperatorCreationRequest request) {
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with phone number " + request.getPhoneNumber() + " already exists.");
        }

        User operatorUser = new User();
        operatorUser.setPhoneNumber(request.getPhoneNumber());
        operatorUser.setPassword(passwordEncoder.encode(request.getPassword()));
        operatorUser.setFirstName(request.getFirstName());
        operatorUser.setLastName(request.getLastName());
        operatorUser.setUserType(User.UserType.OPERATOR);

        User savedOperator = userRepository.save(operatorUser);
        logger.info("New OPERATOR user created with ID: {} and phone number: {}", savedOperator.getId(), savedOperator.getPhoneNumber());

        return savedOperator;
    }

    @Transactional
    public Price createOrUpdatePriceConfig(PriceConfigUpdate request) {
        District fromDistrict = districtRepository.findById(request.getFromDistrictId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid From District ID: " + request.getFromDistrictId()));
        District toDistrict = districtRepository.findById(request.getToDistrictId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid To District ID: " + request.getToDistrictId()));

        if (fromDistrict.equals(toDistrict)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From and To districts cannot be the same for a price route.");
        }

        Optional<Price> existingPriceOpt = priceRepository.findByFromDistrictAndToDistrict(fromDistrict, toDistrict);
        Price price;

        if (existingPriceOpt.isPresent()) {
            price = existingPriceOpt.get();
            price.setBasePricePerSeat(request.getBasePricePerSeat());
            price.setWomenDriverPricePerSeat(request.getWomenDriverPricePerSeat());
            price.setPremiumPricePerSeat(request.getPremiumPricePerSeat());
            price.setFrontSeatExtraFee(request.getFrontSeatExtraFee());
            price.setOtherSeatExtraFee(request.getOtherSeatExtraFee());
            price.setLuggagePrice(request.getLuggagePrice());
            logger.info("Updated price config for route from {} to {}.", fromDistrict.getName(), toDistrict.getName());
        } else {
            price = new Price();
            price.setFromDistrict(fromDistrict);
            price.setToDistrict(toDistrict);
            price.setBasePricePerSeat(request.getBasePricePerSeat());
            price.setWomenDriverPricePerSeat(request.getWomenDriverPricePerSeat());
            price.setPremiumPricePerSeat(request.getPremiumPricePerSeat());
            price.setFrontSeatExtraFee(request.getFrontSeatExtraFee());
            price.setOtherSeatExtraFee(request.getOtherSeatExtraFee());
            price.setLuggagePrice(request.getLuggagePrice());
            logger.info("Created new price config for route from {} to {}.", fromDistrict.getName(), toDistrict.getName());
        }

        return priceRepository.save(price);
    }

    @Transactional(readOnly = true)
    public List<Price> getAllPriceConfigs() {
        return priceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Price> getPriceConfigById(Long priceId) {
        return priceRepository.findById(priceId);
    }

    @Transactional
    public void deletePriceConfig(Long priceId) {
        if (!priceRepository.existsById(priceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Price configuration not found with ID: " + priceId);
        }
        priceRepository.deleteById(priceId);
        logger.info("Deleted price config with ID: {}", priceId);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary() {
        long totalUsers = userRepository.count();
        long totalClients = userRepository.countByUserType(User.UserType.CLIENT);
        long totalDrivers = userRepository.countByUserType(User.UserType.DRIVER);
        long driversPendingApproval = driverRepository.countByApprovalStatus(Driver.ApprovalStatus.PENDING);

        long totalOrders = orderRepository.count();
        long completedOrders = orderRepository.countByStatus(OrderStatus.COMPLETED);
        long canceledOrders = orderRepository.countByStatus(OrderStatus.CANCELED);
        List<OrderStatus> activeOrderStatuses = List.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.EN_ROUTE);
        long activeOrders = orderRepository.countByStatusIn(activeOrderStatuses);

        List<OrderEntity> allCompletedOrders = orderRepository.findByStatus(OrderStatus.COMPLETED);
        BigDecimal totalAppFeesCollected = BigDecimal.ZERO;
        BigDecimal totalCompanyRevenue = BigDecimal.ZERO;
        BigDecimal totalDriverNetEarnings = BigDecimal.ZERO;
        BigDecimal totalClientSpending = BigDecimal.ZERO;

        for (OrderEntity order : allCompletedOrders) {
            BigDecimal appPassengerFee = APP_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(order.getSeats()));
            BigDecimal appLuggageFee = order.getOrderType() == OrderEntity.OrderType.LUGGAGE ? APP_FEE_LUGGAGE : BigDecimal.ZERO;

            BigDecimal orderAppFee = appPassengerFee.add(appLuggageFee);
            totalAppFeesCollected = totalAppFeesCollected.add(orderAppFee);

            BigDecimal companyPassengerRevenue = appPassengerFee.multiply(COMPANY_PASSENGER_SHARE_PERCENT);
            BigDecimal companyLuggageRevenue = appLuggageFee.multiply(COMPANY_LUGGAGE_SHARE_PERCENT);
            totalCompanyRevenue = totalCompanyRevenue.add(companyPassengerRevenue).add(companyLuggageRevenue);

            totalClientSpending = totalClientSpending.add(order.getTotalCost());
            totalDriverNetEarnings = totalDriverNetEarnings.add(order.getTotalCost().subtract(orderAppFee));
        }

        return DashboardSummaryResponse.builder()
                .totalUsers(totalUsers)
                .totalDrivers(totalDrivers)
                .totalClients(totalClients)
                .driversPendingApproval(driversPendingApproval)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .canceledOrders(canceledOrders)
                .activeOrders(activeOrders)
                .totalAppEarnings(totalAppFeesCollected)
                .totalCompanyRevenueFromAppEarnings(totalCompanyRevenue)
                .totalDriverNetEarnings(totalDriverNetEarnings)
                .totalClientSpending(totalClientSpending)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getDailyAppEarnings(Optional<Integer> days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days.orElse(30));
        List<OrderEntity> completedOrders = orderRepository.findByStatusAndCreatedAtAfter(OrderStatus.COMPLETED, cutoff);

        Map<LocalDate, BigDecimal> dailyEarnings = new HashMap<>();
        for (OrderEntity order : completedOrders) {
            BigDecimal appPassengerFee = APP_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(order.getSeats()));
            BigDecimal appLuggageFee = order.getOrderType() == OrderEntity.OrderType.LUGGAGE ? APP_FEE_LUGGAGE : BigDecimal.ZERO;
            BigDecimal orderAppFee = appPassengerFee.add(appLuggageFee);
            dailyEarnings.merge(order.getCreatedAt().toLocalDate(), orderAppFee, BigDecimal::add);
        }

        return dailyEarnings.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getDailyCompanyRevenue(Optional<Integer> days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days.orElse(30));
        List<OrderEntity> completedOrders = orderRepository.findByStatusAndCreatedAtAfter(OrderStatus.COMPLETED, cutoff);

        Map<LocalDate, BigDecimal> dailyRevenue = new HashMap<>();
        for (OrderEntity order : completedOrders) {
            BigDecimal appPassengerFee = APP_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(order.getSeats()));
            BigDecimal appLuggageFee = order.getOrderType() == OrderEntity.OrderType.LUGGAGE ? APP_FEE_LUGGAGE : BigDecimal.ZERO;

            BigDecimal companyPassengerRevenue = appPassengerFee.multiply(COMPANY_PASSENGER_SHARE_PERCENT);
            BigDecimal companyLuggageRevenue = appLuggageFee.multiply(COMPANY_LUGGAGE_SHARE_PERCENT);
            BigDecimal orderCompanyRevenue = companyPassengerRevenue.add(companyLuggageRevenue);

            dailyRevenue.merge(order.getCreatedAt().toLocalDate(), orderCompanyRevenue, BigDecimal::add);
        }

        return dailyRevenue.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getOrderStatusDistribution() {
        List<OrderEntity> allOrders = orderRepository.findAll();
        Map<OrderStatus, Long> countsByStatus = allOrders.stream()
                .collect(Collectors.groupingBy(OrderEntity::getStatus, Collectors.counting()));

        return countsByStatus.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().name(), entry.getValue()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getDailyNewUsers(Optional<Integer> days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days.orElse(30));
        List<User> newUsers = userRepository.findByCreatedAtAfter(cutoff);

        Map<LocalDate, Long> dailyUserCounts = new HashMap<>();
        for (User user : newUsers) {
            dailyUserCounts.merge(user.getCreatedAt().toLocalDate(), 1L, Long::sum);
        }
        return dailyUserCounts.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getDailyNewClients(Optional<Integer> days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days.orElse(30));
        List<User> newClients = userRepository.findByUserTypeAndCreatedAtAfter(User.UserType.CLIENT, cutoff);
        Map<LocalDate, Long> dailyClientCounts = new HashMap<>();
        for (User user : newClients) {
            dailyClientCounts.merge(user.getCreatedAt().toLocalDate(), 1L, Long::sum);
        }
        return dailyClientCounts.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getDailyNewDrivers(Optional<Integer> days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days.orElse(30));
        List<User> newDrivers = userRepository.findByUserTypeAndCreatedAtAfter(User.UserType.DRIVER, cutoff);
        Map<LocalDate, Long> dailyDriverCounts = new HashMap<>();
        for (User user : newDrivers) {
            dailyDriverCounts.merge(user.getCreatedAt().toLocalDate(), 1L, Long::sum);
        }
        return dailyDriverCounts.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getUsersByDistrictDistribution() {
        List<User> allUsers = userRepository.findAll();
        Map<Long, Long> usersByDistrictCount = allUsers.stream()
                .filter(user -> user.getUserType() == User.UserType.CLIENT || user.getUserType() == User.UserType.DRIVER)
                .map(user -> {
                    if (user.getUserType() == User.UserType.CLIENT) {
                        return clientRepository.findByUser_Id(user.getId()).map(Client::getDistrictId).orElse(null);
                    } else if (user.getUserType() == User.UserType.DRIVER) {
                        return driverRepository.findByUser_Id(user.getId()).map(driver -> driver.getDistrict() != null ? driver.getDistrict().getId() : null).orElse(null);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        districtId -> districtId,
                        Collectors.counting()
                ));

        return usersByDistrictCount.entrySet().stream()
                .map(entry -> {
                    String districtName = districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown District");
                    return new ChartDataPoint(districtName, entry.getValue());
                })
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getClientsByDistrictDistribution() {
        List<Client> allClients = clientRepository.findAll();
        Map<Long, Long> clientsByDistrictCount = allClients.stream()
                .map(Client::getDistrictId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        districtId -> districtId,
                        Collectors.counting()
                ));

        return clientsByDistrictCount.entrySet().stream()
                .map(entry -> {
                    String districtName = districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown District");
                    return new ChartDataPoint(districtName, entry.getValue());
                })
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChartDataPoint> getDriversByDistrictDistribution() {
        List<Driver> allDrivers = driverRepository.findAll();
        Map<Long, Long> driversByDistrictCount = allDrivers.stream()
                .filter(driver -> driver.getDistrict() != null)
                .map(driver -> driver.getDistrict().getId())
                .collect(Collectors.groupingBy(
                        districtId -> districtId,
                        Collectors.counting()
                ));

        return driversByDistrictCount.entrySet().stream()
                .map(entry -> {
                    String districtName = districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown District");
                    return new ChartDataPoint(districtName, entry.getValue());
                })
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RevenueReportResponse getRevenueReport() {
        List<OrderEntity> allCompletedOrders = orderRepository.findByStatus(OrderStatus.COMPLETED);

        BigDecimal totalAppFeesCollectedAllTime = BigDecimal.ZERO;
        BigDecimal totalCompanyRevenueAllTime = BigDecimal.ZERO;
        BigDecimal totalDriverNetEarningsAllTime = BigDecimal.ZERO;
        BigDecimal totalClientSpendingAllTime = BigDecimal.ZERO;

        Map<LocalDate, BigDecimal> dailyAppEarnings = new HashMap<>();
        Map<YearMonth, BigDecimal> monthlyAppEarnings = new HashMap<>();
        Map<LocalDate, BigDecimal> dailyCompanyRevenue = new HashMap<>();
        Map<YearMonth, BigDecimal> monthlyCompanyRevenue = new HashMap<>();
        Map<Long, BigDecimal> appEarningsByDistrict = new HashMap<>();
        Map<Long, BigDecimal> companyRevenueByDistrict = new HashMap<>();
        Map<Long, BigDecimal> appEarningsByRegion = new HashMap<>();
        Map<Long, BigDecimal> companyRevenueByRegion = new HashMap<>();
        Map<Long, Long> ordersByDistrictCount = new HashMap<>();
        Map<Long, Long> ordersByRegionCount = new HashMap<>();

        for (OrderEntity order : allCompletedOrders) {
            BigDecimal appPassengerFee = APP_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(order.getSeats()));
            BigDecimal appLuggageFee = order.getOrderType() == OrderEntity.OrderType.LUGGAGE ? APP_FEE_LUGGAGE : BigDecimal.ZERO;
            BigDecimal orderAppFee = appPassengerFee.add(appLuggageFee);

            BigDecimal companyPassengerRevenue = appPassengerFee.multiply(COMPANY_PASSENGER_SHARE_PERCENT);
            BigDecimal companyLuggageRevenue = appLuggageFee.multiply(COMPANY_LUGGAGE_SHARE_PERCENT);
            BigDecimal orderCompanyRevenue = companyPassengerRevenue.add(companyLuggageRevenue);

            totalAppFeesCollectedAllTime = totalAppFeesCollectedAllTime.add(orderAppFee);
            totalCompanyRevenueAllTime = totalCompanyRevenueAllTime.add(orderCompanyRevenue);
            totalClientSpendingAllTime = totalClientSpendingAllTime.add(order.getTotalCost());
            totalDriverNetEarningsAllTime = totalDriverNetEarningsAllTime.add(order.getTotalCost().subtract(orderAppFee));

            LocalDate orderDate = order.getCreatedAt().toLocalDate();
            dailyAppEarnings.merge(orderDate, orderAppFee, BigDecimal::add);
            dailyCompanyRevenue.merge(orderDate, orderCompanyRevenue, BigDecimal::add);

            YearMonth orderMonth = YearMonth.from(order.getCreatedAt());
            monthlyAppEarnings.merge(orderMonth, orderAppFee, BigDecimal::add);
            monthlyCompanyRevenue.merge(orderMonth, orderCompanyRevenue, BigDecimal::add);

            if (order.getToDistrictId() != null) {
                appEarningsByDistrict.merge(order.getToDistrictId(), orderAppFee, BigDecimal::add);
                companyRevenueByDistrict.merge(order.getToDistrictId(), orderCompanyRevenue, BigDecimal::add);
                ordersByDistrictCount.merge(order.getToDistrictId(), 1L, Long::sum);

                districtRepository.findById(order.getToDistrictId()).ifPresent(district -> {
                    if (district.getRegion() != null) {
                        appEarningsByRegion.merge(district.getRegion().getId(), orderAppFee, BigDecimal::add);
                        companyRevenueByRegion.merge(district.getRegion().getId(), orderCompanyRevenue, BigDecimal::add);
                        ordersByRegionCount.merge(district.getRegion().getId(), 1L, Long::sum);
                    }
                });
            }
        }

        List<ChartDataPoint> ordersByDistrictDistribution = ordersByDistrictCount.entrySet().stream()
                .map(entry -> new ChartDataPoint(districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown"), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
        List<ChartDataPoint> ordersByRegionDistribution = ordersByRegionCount.entrySet().stream()
                .map(entry -> new ChartDataPoint(districtRepository.findById(entry.getKey()).map(District::getRegion).map(Region::getName).orElse("Unknown"), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        return RevenueReportResponse.builder()
                .totalAppEarningsAllTime(totalAppFeesCollectedAllTime)
                .totalCompanyRevenueAllTime(totalCompanyRevenueAllTime)
                .totalDriverNetEarningsAllTime(totalDriverNetEarningsAllTime)
                .totalClientSpendingAllTime(totalClientSpendingAllTime)
                .dailyAppEarnings(dailyAppEarnings)
                .monthlyAppEarnings(monthlyAppEarnings)
                .dailyCompanyRevenue(dailyCompanyRevenue)
                .monthlyCompanyRevenue(monthlyCompanyRevenue)
                .appEarningsByDistrict(appEarningsByDistrict)
                .companyRevenueByDistrict(companyRevenueByDistrict)
                .appEarningsByRegion(appEarningsByRegion)
                .companyRevenueByRegion(companyRevenueByRegion)
                .ordersByDistrictDistribution(ordersByDistrictDistribution)
                .ordersByRegionDistribution(ordersByRegionDistribution)
                .build();
    }

    @Transactional(readOnly = true)
    public RideStatsResponse getRideStatistics() {
        List<OrderEntity> allOrders = orderRepository.findAll();

        long totalOrdersCount = allOrders.size();
        Map<OrderStatus, Long> countsByStatus = allOrders.stream()
                .collect(Collectors.groupingBy(OrderEntity::getStatus, Collectors.counting()));

        List<ChartDataPoint> ordersByStatusDistribution = countsByStatus.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().name(), entry.getValue()))
                .collect(Collectors.toList());

        long completedOrdersCount = countsByStatus.getOrDefault(OrderStatus.COMPLETED, 0L);
        long canceledOrdersCount = countsByStatus.getOrDefault(OrderStatus.CANCELED, 0L);
        long pendingOrdersCount = countsByStatus.getOrDefault(OrderStatus.PENDING, 0L);
        long acceptedOrdersCount = countsByStatus.getOrDefault(OrderStatus.ACCEPTED, 0L);
        long enRouteOrdersCount = countsByStatus.getOrDefault(OrderStatus.EN_ROUTE, 0L);

        Map<Long, Long> completedOrdersByDriverId = allOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED && order.getDriverId() != null)
                .collect(Collectors.groupingBy(OrderEntity::getDriverId, Collectors.counting()));

        Map<LocalDate, Long> dailyCompletedOrders = allOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .collect(Collectors.groupingBy(order -> order.getCreatedAt().toLocalDate(), Collectors.counting()));

        Map<Long, Long> completedOrdersByDistrict = allOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED && order.getToDistrictId() != null)
                .collect(Collectors.groupingBy(OrderEntity::getToDistrictId, Collectors.counting()));

        return RideStatsResponse.builder()
                .totalOrdersCount(totalOrdersCount)
                .completedOrdersCount(completedOrdersCount)
                .canceledOrdersCount(canceledOrdersCount)
                .pendingOrdersCount(pendingOrdersCount)
                .acceptedOrdersCount(acceptedOrdersCount)
                .enRouteOrdersCount(enRouteOrdersCount)
                .ordersByStatusDistribution(ordersByStatusDistribution)
                .completedOrdersByDriverId(completedOrdersByDriverId)
                .dailyCompletedOrders(dailyCompletedOrders)
                .completedOrdersByDistrict(completedOrdersByDistrict)
                .build();
    }

    @Transactional(readOnly = true)
    public UserStatsResponse getUserStatistics() {
        long totalUsersCount = userRepository.count();
        long totalClientsCount = userRepository.countByUserType(User.UserType.CLIENT);
        long totalDriversCount = userRepository.countByUserType(User.UserType.DRIVER);
        long totalOperatorsCount = userRepository.countByUserType(User.UserType.OPERATOR);
        long totalAdminsCount = userRepository.countByUserType(User.UserType.ADMIN);

        List<User> allUsers = userRepository.findAll();
        Map<LocalDate, Long> dailyNewUsersMap = allUsers.stream()
                .collect(Collectors.groupingBy(user -> user.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<ChartDataPoint> dailyNewUsers = dailyNewUsersMap.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        Map<LocalDate, Long> dailyNewDriversMap = allUsers.stream()
                .filter(user -> user.getUserType() == User.UserType.DRIVER)
                .collect(Collectors.groupingBy(user -> user.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<ChartDataPoint> dailyNewDrivers = dailyNewDriversMap.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        Map<LocalDate, Long> dailyNewClientsMap = allUsers.stream()
                .filter(user -> user.getUserType() == User.UserType.CLIENT)
                .collect(Collectors.groupingBy(user -> user.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<ChartDataPoint> dailyNewClients = dailyNewClientsMap.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        Map<Long, Long> usersByDistrictCount = new HashMap<>();
        for (User user : allUsers) {
            Long districtId = null;
            if (user.getUserType() == User.UserType.CLIENT) {
                districtId = clientRepository.findByUser_Id(user.getId()).map(Client::getDistrictId).orElse(null);
            } else if (user.getUserType() == User.UserType.DRIVER) {
                districtId = driverRepository.findByUser_Id(user.getId()).map(driver -> driver.getDistrict() != null ? driver.getDistrict().getId() : null).orElse(null);
            }
            if (districtId != null) {
                usersByDistrictCount.merge(districtId, 1L, Long::sum);
            }
        }
        List<ChartDataPoint> usersByDistrictDistribution = usersByDistrictCount.entrySet().stream()
                .map(entry -> new ChartDataPoint(districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown"), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        Map<Long, Long> clientsByDistrictCount = clientRepository.findAll().stream()
                .filter(client -> client.getDistrict() != null)
                .collect(Collectors.groupingBy(Client::getDistrictId, Collectors.counting()));
        List<ChartDataPoint> clientsByDistrictDistribution = clientsByDistrictCount.entrySet().stream()
                .map(entry -> new ChartDataPoint(districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown"), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        Map<Long, Long> driversByDistrictCount = driverRepository.findAll().stream()
                .filter(driver -> driver.getDistrict() != null)
                .map(driver -> driver.getDistrict().getId())
                .collect(Collectors.groupingBy(
                        districtId -> districtId,
                        Collectors.counting()
                ));
        List<ChartDataPoint> driversByDistrictDistribution = driversByDistrictCount.entrySet().stream()
                .map(entry -> new ChartDataPoint(districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown"), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        List<DriverProfileResponse> driversPendingApproval = operatorService.getPendingDriverApprovals().stream()
                .map(this::mapDriverToProfileResponse)
                .collect(Collectors.toList());

        List<ClientProfileResponse> latestClients = clientRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "user.createdAt"))).getContent()
                .stream().map(ClientProfileResponse::fromEntity).collect(Collectors.toList());

        return UserStatsResponse.builder()
                .totalUsersCount(totalUsersCount)
                .totalClientsCount(totalClientsCount)
                .totalDriversCount(totalDriversCount)
                .totalOperatorsCount(totalOperatorsCount)
                .totalAdminsCount(totalAdminsCount)
                .dailyNewUsers(dailyNewUsers)
                .dailyNewDrivers(dailyNewDrivers)
                .dailyNewClients(dailyNewClients)
                .usersByDistrictDistribution(usersByDistrictDistribution)
                .clientsByDistrictDistribution(clientsByDistrictDistribution)
                .driversByDistrictDistribution(driversByDistrictDistribution)
                .driversPendingApproval(driversPendingApproval)
                .latestClients(latestClients)
                .build();
    }

    @Transactional(readOnly = true)
    public DriverPerformanceResponse getDriverPerformance() {
        List<Driver> allDrivers = driverRepository.findAll();
        List<OrderEntity> completedOrders = orderRepository.findByStatus(OrderStatus.COMPLETED);

        long totalApprovedDrivers = allDrivers.stream().filter(d -> d.getApprovalStatus() == Driver.ApprovalStatus.ACCEPTED).count();
        long totalPendingDrivers = allDrivers.stream().filter(d -> d.getApprovalStatus() == Driver.ApprovalStatus.PENDING).count();
        long totalRejectedDrivers = allDrivers.stream().filter(d -> d.getApprovalStatus() == Driver.ApprovalStatus.REJECTED).count();

        Map<Long, BigDecimal> totalEarningsByDriverIdMap = new HashMap<>();
        Map<Long, Long> totalRidesByDistrictMap = new HashMap<>();

        for (OrderEntity order : completedOrders) {
            if (order.getDriverId() != null) {
                BigDecimal appPassengerFee = APP_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(order.getSeats()));
                BigDecimal appLuggageFee = order.getOrderType() == OrderEntity.OrderType.LUGGAGE ? APP_FEE_LUGGAGE : BigDecimal.ZERO;
                BigDecimal orderAppFee = appPassengerFee.add(appLuggageFee);

                BigDecimal driverNetEarningForOrder = order.getTotalCost().subtract(orderAppFee);
                totalEarningsByDriverIdMap.merge(order.getDriverId(), driverNetEarningForOrder, BigDecimal::add);

                if (order.getToDistrictId() != null) {
                    totalRidesByDistrictMap.merge(order.getToDistrictId(), 1L, Long::sum);
                }
            }
        }

        List<DriverOverview> driverOverviews = allDrivers.stream()
                .map(driver -> DriverOverview.builder()
                        .driverId(driver.getId())
                        .phoneNumber(driver.getUser() != null ? driver.getUser().getPhoneNumber() : "N/A")
                        .firstName(driver.getFirstName())
                        .lastName(driver.getLastName())
                        .ratings(driver.getRatings())
                        .rideCount(driver.getRideCount())
                        .walletBalance(driver.getWalletBalance())
                        .approvalStatus(driver.getApprovalStatus().name())
                        .districtName(driver.getDistrict() != null ? driver.getDistrict().getName() : "N/A")
                        .build())
                .collect(Collectors.toList());

        List<DriverOverview> topRatedDrivers = driverOverviews.stream()
                .filter(d -> d.getRideCount() > 0)
                .sorted(Comparator.comparingDouble(DriverOverview::getRatings).reversed())
                .limit(5)
                .collect(Collectors.toList());

        List<DriverOverview> mostRidesDrivers = driverOverviews.stream()
                .sorted(Comparator.comparingInt(DriverOverview::getRideCount).reversed())
                .limit(5)
                .collect(Collectors.toList());

        Map<Long, Double> calculatedAverageRatingByDistrictMap = allDrivers.stream()
                .filter(driver -> driver.getDistrict() != null && driver.getRideCount() > 0)
                .collect(Collectors.groupingBy(
                        driver -> driver.getDistrict().getId(),
                        Collectors.averagingDouble(Driver::getRatings)
                ));

        List<ChartDataPoint> averageRatingByDistrict = calculatedAverageRatingByDistrictMap.entrySet().stream()
                .map(entry -> new ChartDataPoint(districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown"), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        List<ChartDataPoint> totalRidesByDistrict = totalRidesByDistrictMap.entrySet().stream()
                .map(entry -> new ChartDataPoint(districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown"), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        List<ChartDataPoint> totalEarningsByDriver = totalEarningsByDriverIdMap.entrySet().stream()
                .map(entry -> {
                    String driverInfo = driverRepository.findById(entry.getKey())
                            .map(d -> d.getFirstName() + " " + d.getLastName() + " (" + d.getUser().getPhoneNumber() + ")")
                            .orElse("Driver ID: " + entry.getKey());
                    return new ChartDataPoint(driverInfo, entry.getValue());
                })
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());

        return DriverPerformanceResponse.builder()
                .totalApprovedDrivers(totalApprovedDrivers)
                .totalPendingDrivers(totalPendingDrivers)
                .totalRejectedDrivers(totalRejectedDrivers)
                .totalEarningsByDriver(totalEarningsByDriver)
                .topRatedDrivers(topRatedDrivers)
                .mostRidesDrivers(mostRidesDrivers)
                .averageRatingByDistrict(averageRatingByDistrict)
                .totalRidesByDistrict(totalRidesByDistrict)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<DetailedOrderResponse> getOrders(OrderFilterRequest filter, Pageable pageable) {
        Specification<OrderEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.getStatuses()));
            }

            if (filter.getClientId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), filter.getClientId()));
            }

            if (filter.getDriverId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("driverId"), filter.getDriverId()));
            }

            if (filter.getFromDistrictId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("fromDistrictId"), filter.getFromDistrictId()));
            }

            if (filter.getToDistrictId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("toDistrictId"), filter.getToDistrictId()));
            }

            if (filter.getPickupTimeStart() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("pickupTime"), filter.getPickupTimeStart()));
            }
            if (filter.getPickupTimeEnd() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("pickupTime"), filter.getPickupTimeEnd()));
            }

            if (filter.getClientPhoneNumber() != null && !filter.getClientPhoneNumber().isBlank()) {
                Optional<User> clientUserOpt = userRepository.findByPhoneNumber(filter.getClientPhoneNumber());
                clientUserOpt.ifPresent(user -> predicates.add(criteriaBuilder.equal(root.get("userId"), user.getId())));
                if (clientUserOpt.isEmpty()) {
                    predicates.add(criteriaBuilder.disjunction());
                }
            }

            if (filter.getDriverPhoneNumber() != null && !filter.getDriverPhoneNumber().isBlank()) {
                Optional<User> driverUserOpt = userRepository.findByPhoneNumber(filter.getDriverPhoneNumber());
                driverUserOpt.ifPresent(user -> predicates.add(criteriaBuilder.equal(root.get("driverId"), user.getId())));
                if (driverUserOpt.isEmpty()) {
                    predicates.add(criteriaBuilder.disjunction());
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<OrderEntity> ordersPage = orderRepository.findAll(spec, pageable);
        return ordersPage.map(this::mapToDetailedOrderResponse);
    }

    private DetailedOrderResponse mapToDetailedOrderResponse(OrderEntity order) {
        DetailedOrderResponse dto = new DetailedOrderResponse();
        dto.setId(order.getId());
        dto.setUserId(order.getUserId());
        dto.setDriverId(order.getDriverId());
        dto.setSeats(order.getSeats());
        dto.setOrderType(order.getOrderType());
        dto.setSelectedSeats(order.getSelectedSeats());
        dto.setLuggageContactInfo(order.getLuggageContactInfo());
        dto.setExtraInfo(order.getExtraInfo());
        dto.setFromDistrictId(order.getFromDistrictId());
        dto.setToDistrictId(order.getToDistrictId());
        dto.setFromLocation(order.getFromLocation());
        dto.setToLocation(order.getToLocation());
        dto.setPickupTime(order.getPickupTime());
        dto.setTotalCost(order.getTotalCost());
        dto.setStatus(order.getStatus());
        dto.setCreatedAt(order.getCreatedAt());

        if (order.getUserId() != null) {
            userRepository.findById(order.getUserId()).ifPresent(user -> {
                dto.setClientPhoneNumber(user.getPhoneNumber());
                dto.setClientFirstName(user.getFirstName());
                dto.setClientLastName(user.getLastName());
            });
        }

        if (order.getDriverId() != null) {
            userRepository.findById(order.getDriverId()).ifPresent(user -> {
                dto.setDriverPhoneNumber(user.getPhoneNumber());
                dto.setDriverFirstName(user.getFirstName());
                dto.setDriverLastName(user.getLastName());
            });
        }

        if (order.getFromDistrictId() != null) {
            districtRepository.findById(order.getFromDistrictId()).ifPresent(district -> {
                dto.setFromDistrictName(district.getName());
            });
        }
        if (order.getToDistrictId() != null) {
            districtRepository.findById(order.getToDistrictId()).ifPresent(district -> {
                dto.setToDistrictName(district.getName());
            });
        }

        return dto;
    }

    @Transactional
    public DetailedOrderResponse updateOrderStatusByAdmin(Long orderId, OrderEntity.OrderStatus newStatus) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update a completed or canceled order. Current status: " + currentStatus.name());
        }

        order.setStatus(newStatus);
        OrderEntity updatedOrder = orderRepository.save(order);

        if (newStatus == OrderStatus.COMPLETED) {
            orderService.deductAppFee(orderId);
        }
        logger.info("Admin updated order {} status to {}. Previously: {}.", orderId, newStatus.name(), currentStatus.name());
        return mapToDetailedOrderResponse(updatedOrder);
    }

    @Transactional(readOnly = true)
    public List<District> getAllDistricts() {
        return districtRepository.findAll(Sort.by("name"));
    }

    private DriverProfileResponse mapDriverToProfileResponse(Driver driver) {
        driver.getUser();
        return DriverProfileResponse.fromEntity(driver);
    }

    @Transactional(readOnly = true)
    public Page<OperatorResponse> getAllOperators(Pageable pageable) {
        return userRepository.findByUserType(User.UserType.OPERATOR, pageable)
                .map(user -> new OperatorResponse(
                        user.getId(),
                        user.getPhoneNumber(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getUserType().name(),
                        "Operator details."
                ));
    }
}