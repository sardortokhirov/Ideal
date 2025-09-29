package org.example.taxi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId; // Client ID
    private Long driverId;

    private int seats;

    @ElementCollection
    private List<String> selectedSeats;

    private String luggageContactInfo;
    private String extraInfo;

    private Long fromDistrictId;
    private Long toDistrictId;

    @Column(nullable = true)
    private String fromLocation;
    @Column(nullable = true)
    private String toLocation;

    private LocalDateTime pickupTime;

    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum OrderType {
        REGULAR,
        WOMEN_DRIVER,
        LUGGAGE,
        PREMIUM_REGULAR
    }

    public enum OrderStatus {
        PENDING,
        ACCEPTED,
        EN_ROUTE,
        COMPLETED,
        CANCELED
    }
}