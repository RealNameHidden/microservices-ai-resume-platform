package com.msgeekforgeeks.candidate_service.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka event fired after a resume is saved.
 * resume-parser consumes this to kick off parsing.
 *
 * Topic: resume.uploaded
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUploadedEvent {
    private Long candidateId;
    private String filePath;
    private LocalDateTime uploadedAt;
}
