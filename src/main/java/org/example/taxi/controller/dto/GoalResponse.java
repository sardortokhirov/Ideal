package org.example.taxi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.taxi.entity.Goal;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoalResponse {
    private Long id;
    private YearMonth month;
    private Long targetNewClients;
    private Long targetNewDrivers;
    private BigDecimal targetCompanyRevenue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GoalResponse fromEntity(Goal goal) {
        if (goal == null) return null;
        return new GoalResponse(
                goal.getId(),
                goal.getMonth(),
                goal.getTargetNewClients(),
                goal.getTargetNewDrivers(),
                goal.getTargetCompanyRevenue(),
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }
}