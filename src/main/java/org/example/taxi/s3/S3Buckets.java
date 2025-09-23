package org.example.taxi.s3; // Changed package to your project structure

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data; // Added Lombok for cleaner code

@Configuration
@ConfigurationProperties(prefix = "aws.s3.buckets")
@Data
public class S3Buckets {
    private String driverUploads;
}