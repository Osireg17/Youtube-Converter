package com.youtube.converter.jobservice.controller;

import com.youtube.converter.jobservice.dto.CreateJobRequest;
import com.youtube.converter.jobservice.dto.CreateJobResponse;
import com.youtube.converter.jobservice.dto.JobStatusResponse;
import com.youtube.converter.jobservice.service.JobService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateJobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        log.info("POST /api/jobs url={} format={}", request.youtubeUrl(), request.outputFormat());
        return jobService.createJob(request);
    }

    @GetMapping("/{id}/status")
    public JobStatusResponse getJobStatus(@PathVariable UUID id) {
        log.info("GET /api/jobs/{}/status", id);
        return jobService.getJobStatus(id);
    }
}
