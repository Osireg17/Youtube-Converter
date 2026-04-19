package com.youtube.converter.jobservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youtube.converter.jobservice.domain.enums.JobStatus;
import com.youtube.converter.jobservice.domain.enums.OutputFormat;
import com.youtube.converter.jobservice.dto.CreateJobRequest;
import com.youtube.converter.jobservice.dto.JobStatusResponse;
import com.youtube.converter.jobservice.exception.JobNotFoundException;
import com.youtube.converter.jobservice.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobService jobService;

    @ParameterizedTest
    @MethodSource("invalidCreateJobRequests")
    void createJob_invalidRequest_returns400(String youtubeUrl, OutputFormat outputFormat) throws Exception {
        CreateJobRequest request = new CreateJobRequest(youtubeUrl, outputFormat);
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private static Stream<Arguments> invalidCreateJobRequests() {
        return Stream.of(
                Arguments.of("not-a-youtube-url", OutputFormat.MP4),
                Arguments.of("", OutputFormat.MP4),
                Arguments.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ", null)
        );
    }

    @Test
    void getJobStatus_returns200WithStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJobStatus(id)).thenReturn(new JobStatusResponse(id, JobStatus.PROCESSING, null));

        mockMvc.perform(get("/api/jobs/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.downloadUrl").doesNotExist());
    }

    @Test
    void getJobStatus_whenDone_returns200WithDownloadUrl() throws Exception {
        UUID id = UUID.randomUUID();
        String presignedUrl = "https://presigned.example.com/abc";
        when(jobService.getJobStatus(id)).thenReturn(new JobStatusResponse(id, JobStatus.DONE, presignedUrl));

        mockMvc.perform(get("/api/jobs/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.downloadUrl").value(presignedUrl));
    }

    @Test
    void getJobStatus_whenNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJobStatus(any())).thenThrow(new JobNotFoundException(id));

        mockMvc.perform(get("/api/jobs/{id}/status", id))
                .andExpect(status().isNotFound());
    }
}
