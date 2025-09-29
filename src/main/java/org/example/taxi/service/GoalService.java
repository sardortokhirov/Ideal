package org.example.taxi.service;

import org.example.taxi.controller.dto.GoalProgressionResponse;
import org.example.taxi.controller.dto.GoalRequest;
import org.example.taxi.controller.dto.GoalResponse;
import org.example.taxi.entity.Goal;
import org.example.taxi.entity.OrderEntity;
import org.example.taxi.repository.GoalRepository;
import org.example.taxi.repository.OrderRepository;
import org.example.taxi.repository.ClientRepository;
import org.example.taxi.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private static final Logger logger = LoggerFactory.getLogger(GoalService.class);

    @Autowired private GoalRepository goalRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private AdminService adminService; // For revenue calculation helper

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // CRITICAL FIX: Define these constants here!
    public static final BigDecimal AVG_REVENUE_PER_CLIENT = BigDecimal.valueOf(1800); // Example average UZS per client/order (company's share)
    public static final long AVG_RIDES_PER_DRIVER_PER_MONTH = 30; // Example average (total rides per driver per month)


    /**
     * Creates a new monthly goal or updates an existing one for the specified month.
     * @param request GoalRequest DTO containing month and targets.
     * @return The created or updated GoalResponse.
     */
    @Transactional
    public GoalResponse createOrUpdateGoal(GoalRequest request) {
        Optional<Goal> existingGoal = goalRepository.findByMonth(request.getMonth());
        Goal goal;

        if (existingGoal.isPresent()) {
            goal = existingGoal.get();
            goal.setTargetNewClients(request.getTargetNewClients());
            goal.setTargetNewDrivers(request.getTargetNewDrivers());
            goal.setTargetCompanyRevenue(request.getTargetCompanyRevenue());
            goal.getUpdatedAt();
            logger.info("Updated goal for month {}.", request.getMonth());
        } else {
            goal = new Goal();
            goal.setMonth(request.getMonth());
            goal.setTargetNewClients(request.getTargetNewClients());
            goal.setTargetNewDrivers(request.getTargetNewDrivers());
            goal.setTargetCompanyRevenue(request.getTargetCompanyRevenue());
            logger.info("Created new goal for month {}.", request.getMonth());
        }
        return GoalResponse.fromEntity(goalRepository.save(goal));
    }

    /**
     * Retrieves a goal by its ID.
     * @param id The ID of the goal.
     * @return Optional GoalResponse.
     */
    @Transactional(readOnly = true)
    public Optional<GoalResponse> getGoalById(Long id) {
        return goalRepository.findById(id).map(GoalResponse::fromEntity);
    }

    /**
     * Retrieves all goals.
     * @return List of all GoalResponse objects.
     */
    @Transactional(readOnly = true)
    public List<GoalResponse> getAllGoals() {
        return goalRepository.findAllByOrderByMonthAsc().stream()
                .map(GoalResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a goal by its ID.
     * @param id The ID of the goal.
     */
    @Transactional
    public void deleteGoal(Long id) {
        if (!goalRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found with ID: " + id);
        }
        goalRepository.deleteById(id);
        logger.info("Deleted goal with ID: {}.", id);
    }

    /**
     * Calculates the progression of a specific goal (actual vs. target).
     * @param month The YearMonth for which to calculate progression.
     * @return GoalProgressionResponse.
     * @throws ResponseStatusException if no goal is set for the given month.
     */
    @Transactional(readOnly = true)
    public GoalProgressionResponse getGoalProgression(YearMonth month) {
        Goal goal = goalRepository.findByMonth(month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No goal set for " + month));

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDateTime periodStart = monthStart.atStartOfDay();
        LocalDateTime periodEnd = monthEnd.atTime(LocalTime.MAX);

        // Fetch actual data for the month
        long actualNewClients = clientRepository.findByCreatedAtAfter(periodStart).stream()
                .filter(c -> c.getCreatedAt().isBefore(periodEnd)) // Filter by periodEnd, not monthEnd.plusDays(1)
                .count();
        long actualNewDrivers = driverRepository.findByCreatedAtAfter(periodStart).stream()
                .filter(d -> d.getCreatedAt().isBefore(periodEnd)) // Filter by periodEnd
                .count();

        // Calculate actual company revenue for the month
        List<OrderEntity> completedOrdersInMonth = orderRepository.findByStatusAndCreatedAtAfter(OrderEntity.OrderStatus.COMPLETED, periodStart).stream()
                .filter(o -> o.getCreatedAt().isBefore(periodEnd)) // Filter by periodEnd
                .collect(Collectors.toList());

        BigDecimal actualCompanyRevenue = calculateCompanyRevenueFromOrders(completedOrdersInMonth);

        // Calculate percentages
        BigDecimal clientProgressPercent = calculateProgress(actualNewClients, goal.getTargetNewClients());
        BigDecimal driverProgressPercent = calculateProgress(actualNewDrivers, goal.getTargetNewDrivers());
        BigDecimal revenueProgressPercent = calculateProgress(actualCompanyRevenue, goal.getTargetCompanyRevenue());

        // Determine status
        String clientStatus = getProgressStatus(clientProgressPercent);
        String driverStatus = getProgressStatus(driverProgressPercent);
        String revenueStatus = getProgressStatus(revenueProgressPercent);

        // Calculate remaining needed
        Long remainingClientsNeeded = Math.max(0, goal.getTargetNewClients() - actualNewClients);
        Long remainingDriversNeeded = Math.max(0, goal.getTargetNewDrivers() - actualNewDrivers);
        BigDecimal remainingRevenueNeeded = goal.getTargetCompanyRevenue().subtract(actualCompanyRevenue).max(BigDecimal.ZERO);

        boolean isAchieved = actualNewClients >= goal.getTargetNewClients() &&
                actualNewDrivers >= goal.getTargetNewDrivers() &&
                actualCompanyRevenue.compareTo(goal.getTargetCompanyRevenue()) >= 0;

        return GoalProgressionResponse.builder()
                .goal(GoalResponse.fromEntity(goal))
                .actualNewClients(actualNewClients)
                .actualNewDrivers(actualNewDrivers)
                .actualCompanyRevenue(actualCompanyRevenue)
                .clientProgressPercent(clientProgressPercent)
                .driverProgressPercent(driverProgressPercent)
                .revenueProgressPercent(revenueProgressPercent)
                .clientStatus(clientStatus)
                .driverStatus(driverStatus)
                .revenueStatus(revenueStatus)
                .remainingClientsNeeded(remainingClientsNeeded)
                .remainingDriversNeeded(remainingDriversNeeded)
                .remainingRevenueNeeded(remainingRevenueNeeded)
                .isAchieved(isAchieved)
                .isActiveMonth(YearMonth.now().equals(month))
                .build();
    }

    private BigDecimal calculateCompanyRevenueFromOrders(List<OrderEntity> orders) {
        BigDecimal totalCompanyRevenue = BigDecimal.ZERO;
        for (OrderEntity order : orders) {
            BigDecimal appPassengerFee = BigDecimal.valueOf(20).multiply(BigDecimal.valueOf(order.getSeats())); // Re-use constant

            BigDecimal companyPassengerRevenue = appPassengerFee.multiply(BigDecimal.valueOf(0.15)); // Re-use constant
            totalCompanyRevenue = totalCompanyRevenue.add(companyPassengerRevenue);
        }
        return totalCompanyRevenue;
    }
    private BigDecimal calculateProgress(Long actual, Long target) {
        if (target == null || target == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(actual).divide(BigDecimal.valueOf(target), 4, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    private BigDecimal calculateProgress(BigDecimal actual, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return actual.divide(target, 4, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    private String getProgressStatus(BigDecimal percent) {
        if (percent.compareTo(HUNDRED) >= 0) return "Achieved";
        if (percent.compareTo(BigDecimal.valueOf(80)) >= 0) return "On Track";
        return "Behind";
    }
}