package org.example.taxi.service;

import org.example.taxi.s3.S3Buckets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client; // AWS SDK V2
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL; // For public read access (V2)

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
    private final S3Client s3Client; // Inject S3Client (V2)
    private final S3Buckets s3Buckets; // Inject your S3Buckets config

    @Autowired
    public FileStorageService(S3Client s3Client, S3Buckets s3Buckets) {
        this.s3Client = s3Client;
        this.s3Buckets = s3Buckets;
    }

    /**
     * Stores a file directly to AWS S3 and returns its publicly accessible URL.
     * @param file The MultipartFile to store.
     * @param fileType The type of file (e.g., "license", "car") used for structuring the S3 key.
     * @param phoneNumber The authenticated user's phone number, for organizing S3 objects.
     * @return The URL to access the stored file.
     * @throws ResponseStatusException if file storage fails.
     */
    public String storeFile(MultipartFile file, String fileType, String phoneNumber) {
        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExtension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileExtension = originalFileName.substring(dotIndex);
        }

        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        // S3 Key structure: drivers/<phoneNumber>/<fileType>/<uniqueId.ext>
        String s3Key = String.format("drivers/%s/%s/%s", phoneNumber, fileType.toLowerCase(), uniqueFileName);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Buckets.getDriverUploads()) // Use the specific driver files bucket
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .acl(ObjectCannedACL.PUBLIC_READ) // Make the object publicly readable
                    .build();

            s3Client.putObject(putObjectRequest, software.amazon.awssdk.core.sync.RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // Construct the public URL for the uploaded object
            // The format is typically: https://<bucket-name>.s3.<region>.amazonaws.com/<key>
            // We need the region to construct this.
            // Option 1: Get region from S3Client config (more robust)
            String bucketRegion = s3Client.serviceClientConfiguration().region().id();
            String fileUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", s3Buckets.getDriverUploads(), bucketRegion, s3Key);

            logger.info("File '{}' uploaded to S3 bucket '{}' with key '{}'. Public URL: {}", originalFileName, s3Buckets.getDriverUploads(), s3Key, fileUrl);
            return fileUrl;

        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file " + originalFileName + ". Please try again!", ex);
        } catch (software.amazon.awssdk.core.exception.SdkClientException | software.amazon.awssdk.services.s3.model.S3Exception ex) {
            logger.error("Error uploading file to S3: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error during S3 upload: " + ex.getMessage(), ex);
        }
    }

    // You can add a method here to get object if needed, similar to your S3Service example
    // public byte[] getObject(String bucketName, String key) { ... }
}