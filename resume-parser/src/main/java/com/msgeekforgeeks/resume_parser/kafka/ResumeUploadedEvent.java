package com.msgeekforgeeks.resume_parser.kafka;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mirror of the event fired by candidate-service.
 * Must match the JSON shape produced by the producer.
 *
 * Topic: resume.uploaded
 */
@Data
@NoArgsConstructor
public class ResumeUploadedEvent {
    private Long candidateId;
    private String filePath;
    private LocalDateTime uploadedAt;
}
