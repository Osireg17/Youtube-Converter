package com.youtube.converter.jobservice.service;

import com.youtube.converter.jobservice.domain.Job;
import com.youtube.converter.jobservice.domain.enums.JobStatus;
import com.youtube.converter.jobservice.dto.ConversionMessage;
import com.youtube.converter.jobservice.dto.CreateJobRequest;
import com.youtube.converter.jobservice.dto.CreateJobResponse;
import com.youtube.converter.jobservice.dto.JobStatusResponse;
import com.youtube.converter.jobservice.exception.JobNotFoundException;
import com.youtube.converter.jobservice.messaging.JobPublisher;
import com.youtube.converter.jobservice.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobPublisher jobPublisher;
    private final StorageService storageService;

    public JobService(JobRepository jobRepository, JobPublisher jobPublisher, StorageService storageService) {
        this.jobRepository  = jobRepository;
        this.jobPublisher   = jobPublisher;
        this.storageService = storageService;
    }

    @Transactional
    public CreateJobResponse createJob(CreateJobRequest request) {
        log.info("Creating job for url={} format={}", request.youtubeUrl(), request.outputFormat());

        Job job = new Job();
        job.setYoutubeUrl(request.youtubeUrl());
        job.setOutputFormat(request.outputFormat());

        Job savedJob = jobRepository.save(job);
        log.info("Job persisted id={}", savedJob.getId());

        ConversionMessage message = new ConversionMessage(savedJob.getId(), savedJob.getYoutubeUrl(), savedJob.getOutputFormat());
        jobPublisher.publish(message);
        log.info("Conversion message published for jobId={}", savedJob.getId());

        return new CreateJobResponse(savedJob.getId());
    }

    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        String downloadUrl = null;
        if (job.getStatus() == JobStatus.DONE) {
            downloadUrl = storageService.generatePresignedDownloadUrl(getRequiredStorageObjectKey(job));
        }

        return new JobStatusResponse(job.getId(), job.getStatus(), downloadUrl);
    }

    private String getRequiredStorageObjectKey(Job job) {
        String storageObjectKey = job.getStorageObjectKey();
        if (storageObjectKey == null || storageObjectKey.isBlank()) {
            throw new IllegalStateException(
                    "Job " + job.getId() + " is marked DONE but has no storageObjectKey");
        }
        return storageObjectKey;
    }
}
