package org.example.taxi.controller;

import org.example.taxi.controller.dto.GoalCalculatorRequest;
import org.example.taxi.controller.dto.GoalCalculatorResponse;
import org.example.taxi.controller.dto.GoalProgressionResponse;
import org.example.taxi.controller.dto.GoalRequest;
import org.example.taxi.controller.dto.GoalResponse;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.GoalService;
import org.example.taxi.service.MarketControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/etamin/goal-control") // Base path for ETAMIN goal control
public class GoalController {

    private static final Logger logger = LoggerFactory.getLogger(GoalController.class);

    @Autowired private GoalService goalService;
    @Autowired private UserRepository userRepository;
    @Autowired private MarketControlService marketControlService;

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByPhoneNumber(userDetails.getUsername())
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
    }

    @PostMapping("/") // Corrected path
    public ResponseEntity<GoalResponse> createOrUpdateGoal(@Valid @RequestBody GoalRequest request) {
        logger.info("ETAMIN (User ID: {}) creating/updating goal for month {}.", getAuthenticatedUserId(), request.getMonth());
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.createOrUpdateGoal(request));
    }

    @GetMapping("/") // Corrected path
    public ResponseEntity<List<GoalResponse>> getAllGoals() {
        logger.info("ETAMIN (User ID: {}) requesting all goals.", getAuthenticatedUserId());
        List<GoalResponse> goals = goalService.getAllGoals();
        if (goals.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(goals);
    }

    @GetMapping("/{id}") // Corrected path
    public ResponseEntity<GoalResponse> getGoalById(@PathVariable Long id) {
        logger.info("ETAMIN (User ID: {}) requesting goal by ID: {}.", getAuthenticatedUserId(), id);
        return goalService.getGoalById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found with ID: " + id));
    }

    @DeleteMapping("/{id}") // Corrected path
    public ResponseEntity<Void> deleteGoal(@PathVariable Long id) {
        logger.info("ETAMIN (User ID: {}) deleting goal with ID: {}.", getAuthenticatedUserId(), id);
        goalService.deleteGoal(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/progression") // Corrected path
    public ResponseEntity<GoalProgressionResponse> getGoalProgression(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        logger.info("ETAMIN (User ID: {}) requesting goal progression for month {}.", getAuthenticatedUserId(), month);
        return ResponseEntity.ok(goalService.getGoalProgression(month));
    }

    @GetMapping("/calculator") // Corrected path
    public ResponseEntity<GoalCalculatorResponse> calculateGoal(
            @RequestParam BigDecimal targetRevenue,
            @RequestParam Optional<BigDecimal> avgClientRevenue,
            @RequestParam Optional<Long> avgRidesPerDriverPerMonth) {

        logger.info("ETAMIN (User ID: {}) requesting goal calculation for target revenue {}.", getAuthenticatedUserId(), targetRevenue);

        BigDecimal actualAvgClientRevenue = avgClientRevenue.orElse(MarketControlService.AVG_REVENUE_PER_CLIENT);
        Long actualAvgRidesPerDriverPerMonth = avgRidesPerDriverPerMonth.orElse(MarketControlService.AVG_RIDES_PER_DRIVER_PER_MONTH);

        return ResponseEntity.ok(marketControlService.calculateGoalAttainment(targetRevenue, actualAvgClientRevenue, actualAvgRidesPerDriverPerMonth));
    }
}