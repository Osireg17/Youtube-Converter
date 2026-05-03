package com.youtube.converter.jobservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youtube.converter.jobservice.domain.Job;
import com.youtube.converter.jobservice.domain.enums.JobStatus;
import com.youtube.converter.jobservice.domain.enums.OutputFormat;
import com.youtube.converter.jobservice.dto.CreateJobRequest;
import com.youtube.converter.jobservice.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JobServiceIntegrationTest.S3PresignerStubConfig.class})
@TestPropertySource(properties = {
        "aws.s3.access-key-id=test-key",
        "aws.s3.secret-access-key=test-secret",
        "aws.s3.region=us-east-1",
        "aws.s3.bucket-name=test-bucket",
        "cors.allowed-origins=http://localhost:5173",
        "spring.main.allow-bean-definition-overriding=true"
})
class JobServiceIntegrationTest {

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String PRESIGNED_URL = "https://test-bucket.s3.amazonaws.com/conversions/stub.mp4?X-Amz-Signature=stub";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobRepository jobRepository;
    @Autowired private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanUp() {
        // drain any leftover messages so tests don't bleed into each other
        while (rabbitTemplate.receive("conversion.queue") != null) {}
        jobRepository.deleteAll();
    }

    // ─── POST /api/jobs ───────────────────────────────────────────────────────

    @Test
    void createJob_persistsJobAndPublishesMessage() throws Exception {
        CreateJobRequest request = new CreateJobRequest(VALID_URL, OutputFormat.MP4);

        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(body).get("jobId").asText());

        // DB row exists with correct state
        Job saved = jobRepository.findById(jobId).orElseThrow();
        assertEquals(VALID_URL, saved.getYoutubeUrl());
        assertEquals(OutputFormat.MP4, saved.getOutputFormat());
        assertEquals(JobStatus.PENDING, saved.getStatus());
        assertNull(saved.getStorageObjectKey());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        // RabbitMQ message published with matching fields
        Message raw = rabbitTemplate.receive("conversion.queue", 3_000);
        assertNotNull(raw, "Expected a message on conversion.queue within 3s");
        var node = objectMapper.readTree(raw.getBody());
        assertEquals(jobId.toString(), node.get("jobId").asText());
        assertEquals(VALID_URL, node.get("youtubeUrl").asText());
        assertEquals("MP4", node.get("outputFormat").asText());
    }

    // ─── GET /api/jobs/{id}/status ────────────────────────────────────────────

    @Test
    void getJobStatus_whenPending_returnsCorrectResponse() throws Exception {
        Job job = saveJob(JobStatus.PENDING, null);

        mockMvc.perform(get("/api/jobs/{id}/status", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.downloadUrl").doesNotExist());
    }

    @Test
    void getJobStatus_whenDone_returnsPresignedUrl() throws Exception {
        Job job = saveJob(JobStatus.DONE, "conversions/" + UUID.randomUUID() + ".mp4");

        mockMvc.perform(get("/api/jobs/{id}/status", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.downloadUrl").value(PRESIGNED_URL));
    }

    @Test
    void getJobStatus_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}/status", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ─── POST /api/jobs — validation ──────────────────────────────────────────

    @Test
    void createJob_invalidUrl_returns400AndPublishesNothing() throws Exception {
        CreateJobRequest request = new CreateJobRequest("not-a-youtube-url", OutputFormat.MP4);

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());

        assertNull(rabbitTemplate.receive("conversion.queue", 1_000), "No message should be published for invalid request");
    }

    @Test
    void createJob_missingFormat_returns400AndPublishesNothing() throws Exception {
        String body = "{\"youtubeUrl\":\"" + VALID_URL + "\"}";

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertNull(rabbitTemplate.receive("conversion.queue", 1_000), "No message should be published for invalid request");
    }

    @Test
    void createJob_shortYoutuBeUrl_accepted() throws Exception {
        String shortUrl = "https://youtu.be/dQw4w9WgXcQ";
        CreateJobRequest request = new CreateJobRequest(shortUrl, OutputFormat.MP4);

        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(responseBody).get("jobId").asText());

        Job saved = jobRepository.findById(jobId).orElseThrow();
        assertEquals(shortUrl, saved.getYoutubeUrl());

        Message raw = rabbitTemplate.receive("conversion.queue", 3_000);
        assertNotNull(raw);
        var node = objectMapper.readTree(raw.getBody());
        assertEquals(shortUrl, node.get("youtubeUrl").asText());
    }

    @Test
    void createJob_mp3Format_accepted() throws Exception {
        CreateJobRequest request = new CreateJobRequest(VALID_URL, OutputFormat.MP3);

        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(body).get("jobId").asText());

        Job saved = jobRepository.findById(jobId).orElseThrow();
        assertEquals(OutputFormat.MP3, saved.getOutputFormat());

        Message raw = rabbitTemplate.receive("conversion.queue", 3_000);
        assertNotNull(raw);
        var node = objectMapper.readTree(raw.getBody());
        assertEquals("MP3", node.get("outputFormat").asText());
    }

    // ─── CORS ─────────────────────────────────────────────────────────────────

    @Test
    void createJob_actualRequest_returnsCorsHeaders() throws Exception {
        CreateJobRequest request = new CreateJobRequest(VALID_URL, OutputFormat.MP4);

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void getJobStatus_actualRequest_returnsCorsHeaders() throws Exception {
        Job job = saveJob(JobStatus.PENDING, null);

        mockMvc.perform(get("/api/jobs/{id}/status", job.getId())
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    // ─── GET /api/jobs/{id}/status — additional statuses ──────────────────────

    @Test
    void getJobStatus_whenProcessing_returnsCorrectResponse() throws Exception {
        Job job = saveJob(JobStatus.PROCESSING, null);

        mockMvc.perform(get("/api/jobs/{id}/status", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.downloadUrl").doesNotExist());
    }

    @Test
    void getJobStatus_whenFailed_returnsCorrectResponse() throws Exception {
        Job job = saveJob(JobStatus.FAILED, null);

        mockMvc.perform(get("/api/jobs/{id}/status", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.downloadUrl").doesNotExist());
    }

    // ─── GET /api/jobs/{id}/status — DONE with missing storage key ────────────

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   "})
    void getJobStatus_whenDoneButStorageKeyIsBlank_returns500(String storageKey) throws Exception {
        Job job = saveJob(JobStatus.DONE, storageKey);

        mockMvc.perform(get("/api/jobs/{id}/status", job.getId()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Job saveJob(JobStatus status, String storageObjectKey) {
        Job job = new Job();
        job.setYoutubeUrl(VALID_URL);
        job.setOutputFormat(OutputFormat.MP4);
        Job saved = jobRepository.save(job);
        saved.setStatus(status);
        saved.setStorageObjectKey(storageObjectKey);
        return jobRepository.save(saved);
    }

    // ─── S3 stub ──────────────────────────────────────────────────────────────

    @TestConfiguration
    static class S3PresignerStubConfig {

        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        S3Presigner s3Presigner() throws Exception {
            S3Presigner presigner = mock(S3Presigner.class);
            PresignedGetObjectRequest presignedResponse = mock(PresignedGetObjectRequest.class);
            when(presignedResponse.url()).thenReturn(URI.create(PRESIGNED_URL).toURL());
            when(presigner.presignGetObject(any(Consumer.class))).thenReturn(presignedResponse);
            return presigner;
        }
    }
}
