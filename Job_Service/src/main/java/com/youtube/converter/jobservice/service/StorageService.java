package com.youtube.converter.jobservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final Duration PRESIGN_DURATION = Duration.ofHours(1);

    private final S3Presigner s3Presigner;
    private final String bucketName;

    public StorageService(S3Presigner s3Presigner, String s3BucketName) {
        this.s3Presigner = s3Presigner;
        this.bucketName  = s3BucketName;
    }

    public String generatePresignedDownloadUrl(String objectKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(r ->
                r.signatureDuration(PRESIGN_DURATION)
                 .getObjectRequest(getRequest));

        String url = presigned.url().toString();
        log.debug("Generated presigned URL for key={} expiry=1h", objectKey);
        return url;
    }
}
