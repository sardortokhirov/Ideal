package org.example.taxi.service;

import org.example.taxi.controller.dto.ChartDataPoint;
import org.example.taxi.controller.dto.GoalCalculatorResponse; // Import GoalCalculatorResponse
import org.example.taxi.entity.Client;
import org.example.taxi.entity.Client.ClientOrderSource;
import org.example.taxi.entity.District;
import org.example.taxi.entity.Driver;
import org.example.taxi.entity.OrderEntity;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.example.taxi.entity.User;
import org.example.taxi.repository.ClientRepository;
import org.example.taxi.repository.DistrictRepository;
import org.example.taxi.repository.DriverRepository;
import org.example.taxi.repository.OrderRepository;
import org.example.taxi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode; // For RoundingMode
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MarketControlService {

    private static final Logger logger = LoggerFactory.getLogger(MarketControlService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DistrictRepository districtRepository;

    // Constants for average calculations (These would ideally be dynamic or configurable by Admin)
    // These are *company's share* revenue averages, not total client spend.
    public static final BigDecimal AVG_REVENUE_PER_CLIENT = BigDecimal.valueOf(180000); // Example average UZS per client/order (company's share)
    public static final long AVG_RIDES_PER_DRIVER_PER_MONTH = 30; // Example average (total rides per driver per month)

    // Constants for app fees (must match AdminService and OrderService)
    private static final BigDecimal APP_FEE_PER_PASSENGER = BigDecimal.valueOf(20);
    private static final BigDecimal APP_FEE_LONE_LUGGAGE = BigDecimal.valueOf(10);
    private static final BigDecimal COMPANY_PASSENGER_SHARE_PERCENT = BigDecimal.valueOf(0.15); // 15%
    private static final BigDecimal COMPANY_LUGGAGE_SHARE_PERCENT = BigDecimal.valueOf(1.00); // 100%


    /**
     * Calculates active drivers per day within a given time range.
     * Active drivers are those who completed at least one order.
     * @param start DateTime Start of range.
     * @param end DateTime End of range.
     * @return List of ChartDataPoint (date, count of active drivers).
     */
    @Transactional(readOnly = true)
    public List<ChartDataPoint> getDailyActiveDrivers(LocalDateTime start, LocalDateTime end) {
        List<OrderEntity> completedOrders = orderRepository.findByStatusAndPickupTimeBetween(OrderStatus.COMPLETED, start, end);

        Map<LocalDate, Set<Long>> dailyActiveDriversMap = new HashMap<>();
        for (OrderEntity order : completedOrders) {
            if (order.getDriverId() != null) {
                dailyActiveDriversMap
                        .computeIfAbsent(order.getPickupTime().toLocalDate(), k -> new HashSet<>())
                        .add(order.getDriverId());
            }
        }

        return dailyActiveDriversMap.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), (long) entry.getValue().size()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    /**
     * Calculates new clients per day, differentiating between mobile app and operator creation.
     * @param start DateTime Start of range.
     * @param end DateTime End of range.
     * @return List of ChartDataPoint for each day and source (e.g., [date, mobile_count, operator_count]).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDailyNewClientsBySource(LocalDateTime start, LocalDateTime end) {
        List<Client> newClients = clientRepository.findByCreatedAtAfter(start).stream()
                .filter(c -> c.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());

        Map<LocalDate, Map<Client.ClientOrderSource, Long>> dailyNewClientsMap = new HashMap<>();
        for (Client client : newClients) {
            LocalDate creationDate = client.getCreatedAt().toLocalDate();
            dailyNewClientsMap
                    .computeIfAbsent(creationDate, k -> new HashMap<>())
                    .merge(client.getOrderSource(), 1L, Long::sum);
        }

        return dailyNewClientsMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("date", entry.getKey().toString());
                    dataPoint.put("mobileApp", entry.getValue().getOrDefault(Client.ClientOrderSource.MOBILE_APP, 0L));
                    dataPoint.put("operator", entry.getValue().getOrDefault(Client.ClientOrderSource.OPERATOR, 0L));
                    return dataPoint;
                })
                .sorted(Comparator.comparing(dp -> (String) dp.get("date")))
                .collect(Collectors.toList());
    }

    /**
     * Calculates client distribution by district (using client's linked district).
     * @return List of ChartDataPoint (district name, count of clients).
     */
    @Transactional(readOnly = true)
    public List<ChartDataPoint> getClientsByDistrictDistribution() {
        List<Client> allClients = clientRepository.findAll();
        Map<Long, Long> clientsByDistrictCount = new HashMap<>();

        for(Client client : allClients) {
            Long districtId = client.getDistrict() != null ? client.getDistrict().getId() : null;
            if (districtId != null) {
                clientsByDistrictCount.merge(districtId, 1L, Long::sum);
            }
        }

        return clientsByDistrictCount.entrySet().stream()
                .map(entry -> {
                    String districtName = districtRepository.findById(entry.getKey()).map(District::getName).orElse("Unknown District");
                    return new ChartDataPoint(districtName, entry.getValue());
                })
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    /**
     * Calculates the distribution of order statuses.
     * @return List of ChartDataPoint (status name, count of orders).
     */
    @Transactional(readOnly = true)
    public List<ChartDataPoint> getOrderStatusDistribution() {
        List<OrderEntity> allOrders = orderRepository.findAll();
        Map<OrderStatus, Long> countsByStatus = allOrders.stream()
                .collect(Collectors.groupingBy(OrderEntity::getStatus, Collectors.counting()));

        return countsByStatus.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().name(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Calculates new clients per day.
     * @param start DateTime Start of range.
     * @param end DateTime End of range.
     * @return List of ChartDataPoint (date, count of new clients).
     */
    @Transactional(readOnly = true)
    public List<ChartDataPoint> getDailyNewClients(LocalDateTime start, LocalDateTime end) {
        List<Client> newClients = clientRepository.findByCreatedAtAfter(start).stream()
                .filter(c -> c.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());

        Map<LocalDate, Long> dailyNewClientsMap = new HashMap<>();
        for (Client client : newClients) {
            dailyNewClientsMap.merge(client.getCreatedAt().toLocalDate(), 1L, Long::sum);
        }

        return dailyNewClientsMap.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().toString(), entry.getValue()))
                .sorted(Comparator.comparing(ChartDataPoint::getName))
                .collect(Collectors.toList());
    }

    /**
     * Calculates the client breakdown by creation source (Mobile App vs Operator).
     * @param start DateTime Start of range.
     * @param end DateTime End of range.
     * @return List of ChartDataPoint (source name, count of clients).
     */
    @Transactional(readOnly = true)
    public List<ChartDataPoint> getClientsByOrderSource(LocalDateTime start, LocalDateTime end) {
        List<Client> clients = clientRepository.findByCreatedAtAfter(start).stream()
                .filter(c -> c.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());

        Map<Client.ClientOrderSource, Long> countsBySource = clients.stream()
                .collect(Collectors.groupingBy(Client::getOrderSource, Collectors.counting()));

        return countsBySource.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().name(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Calculates the client breakdown by creation source (Mobile App vs Operator).
     * This is an aggregation across all time.
     * @return List of ChartDataPoint (source name, count of clients).
     */
    @Transactional(readOnly = true)
    public List<ChartDataPoint> getClientsByOrderSourceAllTime() {
        List<Client> allClients = clientRepository.findAll();

        Map<Client.ClientOrderSource, Long> countsBySource = allClients.stream()
                .collect(Collectors.groupingBy(Client::getOrderSource, Collectors.counting()));

        return countsBySource.entrySet().stream()
                .map(entry -> new ChartDataPoint(entry.getKey().name(), entry.getValue()))
                .collect(Collectors.toList());
    }


    /**
     * Goal Calculator: Calculates clients and drivers needed for a target revenue.
     * @param targetRevenue The desired company revenue for a month.
     * @param averageClientRevenue The average revenue per client (from company's perspective).
     * @param averageRidesPerDriverPerMonth The average number of rides a driver completes per month.
     * @return GoalCalculatorResponse with calculated numbers.
     */
    @Transactional(readOnly = true)
    public GoalCalculatorResponse calculateGoalAttainment(BigDecimal targetRevenue, BigDecimal averageClientRevenue, long averageRidesPerDriverPerMonth) {
        if (averageClientRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            return GoalCalculatorResponse.builder()
                    .targetRevenue(targetRevenue)
                    .clientsNeededForRevenue(0L)
                    .estimatedDriversNeeded(0L)
                    .explanation("Cannot calculate clients needed if average revenue per client is zero or negative.")
                    .build();
        }

        Long clientsNeeded = targetRevenue.divide(averageClientRevenue, 0, RoundingMode.UP).longValue() ;

        long estimatedDriversNeeded = 0;
        if (clientsNeeded > 0 && averageRidesPerDriverPerMonth > 0) {
            estimatedDriversNeeded = clientsNeeded / averageRidesPerDriverPerMonth;
            if (clientsNeeded % averageRidesPerDriverPerMonth != 0) {
                estimatedDriversNeeded++;
            }
        }

        return GoalCalculatorResponse.builder()
                .targetRevenue(targetRevenue)
                .clientsNeededForRevenue(clientsNeeded)
                .estimatedDriversNeeded(estimatedDriversNeeded)
                .explanation(String.format("To reach %s UZS company revenue, you need to acquire approximately %d new clients (orders), which would require about %d active drivers, assuming an average revenue of %s UZS per client and %d rides per driver per month.",
                        targetRevenue.toPlainString(), clientsNeeded, estimatedDriversNeeded, averageClientRevenue.toPlainString(), averageRidesPerDriverPerMonth))
                .build();
    }
}