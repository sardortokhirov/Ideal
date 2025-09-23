package org.example.taxi.controller;

import org.example.taxi.s3.S3Service;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType; // IMPORTANT: Ensure this is imported
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads/driver")
public class FileStorageController {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageController.class);

    @Autowired
    private S3Service s3Service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DriverService driverService;

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByPhoneNumber(userDetails.getUsername())
                .map(org.example.taxi.entity.User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
    }

    private String getAuthenticatedUserPhoneNumber() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication.getPrincipal() instanceof String)) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated.");
    }

    private ResponseEntity<?> handleFileUploadAndProfileUpdate(MultipartFile file, String fileType, String s3Directory) {
        Long authenticatedUserId = getAuthenticatedUserId();
        String phoneNumber = getAuthenticatedUserPhoneNumber();
        String subDirectory = "drivers/" + phoneNumber + "/" + s3Directory;

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File cannot be empty."));
        }

        logger.info("Attempting to upload file '{}' of type '{}' for user '{}' to S3.", file.getOriginalFilename(), fileType, phoneNumber);
        try {
            String fileUrl = s3Service.uploadFile(file, subDirectory);
            driverService.updateDriverFileUrl(authenticatedUserId, fileType, fileUrl);

            return ResponseEntity.ok(Map.of("message", fileType + " uploaded and profile updated successfully", "url", fileUrl));
        } catch (IOException e) {
            logger.error("IO error during file upload for user {}: {}", phoneNumber, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file for upload.", e);
        } catch (Exception e) {
            logger.error("Error during file upload for user {}: {}", phoneNumber, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "Uploads driver's profile picture",
            description = "Uploads an image file to S3 and updates the driver's profilePictureUrl in the database.")
    @PostMapping(value = "/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) // Explicitly set consumes
    public ResponseEntity<?> uploadProfilePicture(
            @Parameter(description = "The profile picture file to upload (e.g., JPEG, PNG)", required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestParam("file") MultipartFile file) { // @RequestParam("file") ensures the part name is 'file'
        return handleFileUploadAndProfileUpdate(file, "profilePicture", "profile-pictures");
    }

    @Operation(summary = "Uploads driver's license picture",
            description = "Uploads an image file of the driver's license to S3 and updates driverLicensePictureUrl.")
    @PostMapping(value = "/license-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDriverLicensePicture(
            @Parameter(description = "The driver license file to upload", required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestParam("file") MultipartFile file) {
        return handleFileUploadAndProfileUpdate(file, "driverLicensePicture", "licenses");
    }

    @Operation(summary = "Uploads driver's car picture",
            description = "Uploads an image file of the driver's car to S3 and updates carPictureUrl.")
    @PostMapping(value = "/car-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCarPicture(
            @Parameter(description = "The car picture file to upload", required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestParam("file") MultipartFile file) {
        return handleFileUploadAndProfileUpdate(file, "carPicture", "cars");
    }

    @Operation(summary = "Uploads driver's passport picture",
            description = "Uploads an image file of the driver's passport to S3 and updates passportPictureUrl.")
    @PostMapping(value = "/passport-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPassportPicture(
            @Parameter(description = "The passport file to upload", required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestParam("file") MultipartFile file) {
        return handleFileUploadAndProfileUpdate(file, "passportPicture", "passports");
    }
}