package org.example.taxi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;
    private Long driverId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
}