package org.example.taxi.repository;

import org.example.taxi.entity.Driver;
import org.example.taxi.entity.Driver.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime; // Added for findByCreatedAtAfter
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByUser_Id(Long userId);
    Optional<Driver> findByCarNumber(String carNumber);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN TRUE ELSE FALSE END FROM Driver d WHERE d.user.id = ?1 AND d.approvalStatus = ?2 " +
            "AND d.firstName IS NOT NULL AND d.firstName <> '' AND d.lastName IS NOT NULL AND d.lastName <> '' " +
            "AND d.profilePictureUrl IS NOT NULL AND d.profilePictureUrl <> '' " +
            "AND d.driverLicenseNumber IS NOT NULL AND d.driverLicenseNumber <> '' " +
            "AND d.driverLicensePictureUrl IS NOT NULL AND d.driverLicensePictureUrl <> '' " +
            "AND d.carName IS NOT NULL AND d.carName <> '' AND d.carNumber IS NOT NULL AND d.carNumber <> '' " +
            "AND d.carPictureUrl IS NOT NULL AND d.carPictureUrl <> '' " +
            "AND d.passportPictureUrl IS NOT NULL AND d.passportPictureUrl <> '' " +
            "AND d.district IS NOT NULL")
    boolean isFullyCredentialed(Long userId, ApprovalStatus status);

    long countByApprovalStatus(ApprovalStatus approvalStatus);
    long count();

    List<Driver> findByApprovalStatus(ApprovalStatus approvalStatus);

    // NEW: Find drivers created after a specific time (for GoalService)
    List<Driver> findByCreatedAtAfter(LocalDateTime createdAt);
}