package com.youtube.converter.jobservice.repository;

import com.youtube.converter.jobservice.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
}
