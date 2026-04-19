package com.youtube.converter.jobservice.service;

import com.youtube.converter.jobservice.domain.Job;
import com.youtube.converter.jobservice.dto.ConversionMessage;
import com.youtube.converter.jobservice.dto.CreateJobRequest;
import com.youtube.converter.jobservice.dto.CreateJobResponse;
import com.youtube.converter.jobservice.messaging.JobPublisher;
import com.youtube.converter.jobservice.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobPublisher jobPublisher;

    public JobService(JobRepository jobRepository, JobPublisher jobPublisher) {
        this.jobRepository = jobRepository;
        this.jobPublisher = jobPublisher;
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
}
