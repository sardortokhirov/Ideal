package org.example.taxi.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class DriverCreationRequest {
    @NotBlank(message = "Phone number is required.")
    @Pattern(regexp = "^\\+?\\d{10,15}$", message = "Invalid phone number format. Must be 10-15 digits, optionally starting with '+'")
    private String phoneNumber;

    @NotBlank(message = "Password is required.")
    @Size(min = 6, message = "Password must be at least 6 characters long.")
    private String password;

    @NotBlank(message = "First name is required.")
    private String firstName;

    @NotBlank(message = "Last name is required.")
    private String lastName;

    @NotNull(message = "Profile picture file is required.")
    private MultipartFile profilePictureFile;

    @NotBlank(message = "Driver license number is required.")
    private String driverLicenseNumber;

    @NotNull(message = "Driver license picture file is required.")
    private MultipartFile driverLicensePictureFile;

    @NotBlank(message = "Car name is required.")
    private String carName;

    @NotBlank(message = "Car number is required.")
    private String carNumber;

    @NotNull(message = "Car picture file is required.")
    private MultipartFile carPictureFile;

    @NotNull(message = "Passport picture file is required.")
    private MultipartFile passportPictureFile;

    @NotNull(message = "District ID is required.")
    private Long districtId;
}