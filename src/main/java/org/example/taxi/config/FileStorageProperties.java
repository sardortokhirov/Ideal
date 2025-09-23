package org.example.taxi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cloud.aws.s3") // Changed prefix to match AWS S3 properties
public class FileStorageProperties {
    private String bucketName;
    // Base URL will be constructed by the S3 client, no longer a static property here.
    // If you need a custom CDN URL, you'd add it here and the service would use it.
}