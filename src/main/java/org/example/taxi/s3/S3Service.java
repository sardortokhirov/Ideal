package org.example.taxi.s3;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3;
    private final S3Buckets s3Buckets;

    public S3Service(S3Client s3, S3Buckets s3Buckets) {
        this.s3 = s3;
        this.s3Buckets = s3Buckets;
    }

    public String uploadFile(MultipartFile file, String subDirectory) throws IOException, S3Exception {
        String originalFileName = file.getOriginalFilename();
        String fileExtension = "";
        int dotIndex = originalFileName != null ? originalFileName.lastIndexOf('.') : -1;
        if (dotIndex > 0) {
            fileExtension = originalFileName.substring(dotIndex);
        }
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

        String s3Key = subDirectory + "/" + uniqueFileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Buckets.getDriverUploads())
                .key(s3Key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String region = s3.serviceClientConfiguration().region().id();
        String encodedKeyForUrl = s3Key.replace("+", "%2B");
        String fileUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", s3Buckets.getDriverUploads(), region, encodedKeyForUrl);

        return fileUrl;
    }

    public byte[] getObject(String bucketName, String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        try {
            ResponseInputStream<GetObjectResponse> res = s3.getObject(getObjectRequest);
            return res.readAllBytes();
        } catch (IOException e) {
            return null;
        } catch (S3Exception e) {
            return null;
        }
    }
}