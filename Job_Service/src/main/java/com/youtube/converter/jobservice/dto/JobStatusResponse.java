package com.youtube.converter.jobservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.youtube.converter.jobservice.domain.enums.JobStatus;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobStatusResponse(UUID jobId, JobStatus status, String downloadUrl) {
}
