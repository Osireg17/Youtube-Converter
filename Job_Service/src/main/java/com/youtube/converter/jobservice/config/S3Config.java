package com.youtube.converter.jobservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    private final String accessKeyId;
    private final String secretAccessKey;
    private final String region;
    private final String endpoint;
    private final String bucketName;

    public S3Config(
            @Value("${aws.s3.access-key-id}")    String accessKeyId,
            @Value("${aws.s3.secret-access-key}") String secretAccessKey,
            @Value("${aws.s3.region}")            String region,
            @Value("${aws.s3.endpoint:}")         String endpoint,
            @Value("${aws.s3.bucket-name}")       String bucketName) {
        this.accessKeyId     = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.region          = region;
        this.endpoint        = endpoint;
        this.bucketName      = bucketName;
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public String s3BucketName() {
        return bucketName;
    }
}
