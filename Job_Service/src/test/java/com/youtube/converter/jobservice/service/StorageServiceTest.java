package com.youtube.converter.jobservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StorageServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedRequest;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(s3Presigner, "test-bucket");
    }

    @Test
    void generatePresignedDownloadUrl_returnsExpectedUrl() throws Exception {
        // given
        String objectKey = "test-video.mp4";
        URL expectedUrl = URI.create("https://example.com/presigned-url").toURL();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<GetObjectPresignRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(s3Presigner.presignGetObject(captor.capture())).thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(expectedUrl);

        // when
        String actualUrl = storageService.generatePresignedDownloadUrl(objectKey);

        // then
        assertEquals(expectedUrl.toString(), actualUrl);
        Consumer<GetObjectPresignRequest.Builder> capturedConsumer = captor.getValue();
        GetObjectPresignRequest.Builder builder = GetObjectPresignRequest.builder();
        capturedConsumer.accept(builder);
        GetObjectPresignRequest capturedRequest = builder.build();
        assertEquals("test-bucket", capturedRequest.getObjectRequest().bucket());
        assertEquals(objectKey, capturedRequest.getObjectRequest().key());
    }
}
