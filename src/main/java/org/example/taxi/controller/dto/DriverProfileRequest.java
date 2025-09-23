package org.example.taxi.controller.dto;

import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class DriverProfileRequest {
    @Nullable private String firstName;
    @Nullable private String lastName;
    @Nullable private String driverLicenseNumber;
    @Nullable private String carName;
    @Nullable private String carNumber;
    @Nullable private Long districtId; // Direct District ID
}