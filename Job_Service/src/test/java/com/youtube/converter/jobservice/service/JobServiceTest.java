package com.youtube.converter.jobservice.service;

import com.youtube.converter.jobservice.domain.Job;
import com.youtube.converter.jobservice.domain.enums.OutputFormat;
import com.youtube.converter.jobservice.dto.ConversionMessage;
import com.youtube.converter.jobservice.dto.CreateJobRequest;
import com.youtube.converter.jobservice.dto.CreateJobResponse;
import com.youtube.converter.jobservice.messaging.JobPublisher;
import com.youtube.converter.jobservice.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobPublisher jobPublisher;

    @InjectMocks
    private JobService jobService;

    @Test
    void createJob_happyPath_savesJobAndPublishesMessage() {

        CreateJobRequest request = new CreateJobRequest("https://www.youtube.com/watch?v=dQw4w9WgXcQ", OutputFormat.MP4);
        UUID fixedUuid = UUID.randomUUID();
        Job savedJob = mock(Job.class);
        when(savedJob.getId()).thenReturn(fixedUuid);
        when(savedJob.getYoutubeUrl()).thenReturn(request.youtubeUrl());
        when(savedJob.getOutputFormat()).thenReturn(request.outputFormat());
        when(jobRepository.save(any())).thenReturn(savedJob);

        CreateJobResponse expectedResponse = new CreateJobResponse(savedJob.getId());
        CreateJobResponse actualResponse = jobService.createJob(request);

        assertEquals(expectedResponse.jobId(), actualResponse.jobId());

        ArgumentCaptor<ConversionMessage> captor = ArgumentCaptor.forClass(ConversionMessage.class);
        verify(jobPublisher).publish(captor.capture());
        ConversionMessage capturedMessage = captor.getValue();
        assertEquals(savedJob.getId(), capturedMessage.jobId());
        assertEquals(request.youtubeUrl(), capturedMessage.youtubeUrl());
        assertEquals(request.outputFormat(), capturedMessage.outputFormat());
    }

    @Test
    void createJob_whenRepositoryThrows_publisherIsNeverCalled() {


        CreateJobRequest request = new CreateJobRequest("https://www.youtube.com/watch?v=dQw4w9WgXcQ", OutputFormat.MP3);
        when(jobRepository.save(any())).thenThrow(new DataAccessException("Database error") {});

        assertThrows(DataAccessException.class, () -> jobService.createJob(request));

        verify(jobPublisher, never()).publish(any());

    }
}
