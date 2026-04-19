package com.youtube.converter.jobservice.dto;

import com.youtube.converter.jobservice.domain.enums.OutputFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;


public record CreateJobRequest(
        @NotBlank @Pattern(regexp = "^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/)[a-zA-Z0-9_-]{11}$",
                message = "Invalid YouTube URL format") String youtubeUrl,
        @NotNull OutputFormat outputFormat) {
}
