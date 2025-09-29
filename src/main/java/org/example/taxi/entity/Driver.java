package org.example.taxi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private String driverLicenseNumber;
    private String driverLicensePictureUrl;

    private String carName;
    @Column(unique = true)
    private String carNumber;
    private String carPictureUrl;

    private String passportPictureUrl;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    private double ratings = 0.0;
    private int rideCount = 0;
    private BigDecimal walletBalance = BigDecimal.ZERO;
    private LocalDateTime createdAt = LocalDateTime.now(); // Ensure createdAt is present

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.NONE;

    public enum ApprovalStatus { PENDING, ACCEPTED, REJECTED, NONE }
}