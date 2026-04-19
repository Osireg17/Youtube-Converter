package com.youtube.converter.jobservice.dto;

import com.youtube.converter.jobservice.domain.enums.OutputFormat;

import java.util.UUID;


public record ConversionMessage(UUID jobId, String youtubeUrl, OutputFormat outputFormat) {


}
