package org.example.taxi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.taxi.entity.Driver;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverProfileResponse {
    private Long id;
    private String phoneNumber;

    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private String driverLicenseNumber;
    private String driverLicensePictureUrl;
    private String carName;
    private String carNumber;
    private String carPictureUrl;
    private String passportPictureUrl;
    // --- REMOVED: insuranceDocumentUrl
    // --- REMOVED: backgroundCheckDocumentUrl
    private Long districtId;
    private String districtName;
    private String regionName;

    private double ratings;
    private int rideCount;
    private BigDecimal walletBalance;
    private Driver.ApprovalStatus approvalStatus;

    public static DriverProfileResponse fromEntity(Driver driver) {
        if (driver == null) return null;
        String districtName = driver.getDistrict() != null ? driver.getDistrict().getName() : null;
        String regionName = (driver.getDistrict() != null && driver.getDistrict().getRegion() != null)
                ? driver.getDistrict().getRegion().getName() : null;

        return new DriverProfileResponse(
                driver.getId(),
                driver.getUser() != null ? driver.getUser().getPhoneNumber() : null,

                driver.getFirstName(),
                driver.getLastName(),
                driver.getProfilePictureUrl(),
                driver.getDriverLicenseNumber(),
                driver.getDriverLicensePictureUrl(),
                driver.getCarName(),
                driver.getCarNumber(),
                driver.getCarPictureUrl(),
                driver.getPassportPictureUrl(),
                // --- REMOVED: driver.getInsuranceDocumentUrl(),
                // --- REMOVED: driver.getBackgroundCheckDocumentUrl(),
                driver.getDistrict() != null ? driver.getDistrict().getId() : null,
                districtName,
                regionName,

                driver.getRatings(),
                driver.getRideCount(),
                driver.getWalletBalance(),
                driver.getApprovalStatus()
        );
    }
}