package com.youtube.converter.jobservice.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.youtube.converter.jobservice.domain.enums.JobStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobStatusResponse(UUID jobId, JobStatus status, String downloadUrl) {

}
