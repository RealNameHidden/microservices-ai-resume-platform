package com.msgeekforgeeks.candidate_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeEventProducer {

    private final KafkaTemplate<String, ResumeUploadedEvent> kafkaTemplate;

    @Value("${kafka.topic.resume-uploaded:resume.uploaded}")
    private String topic;

    public void send(ResumeUploadedEvent event) {
        log.info("Firing resume.uploaded event for candidateId={}", event.getCandidateId());
        kafkaTemplate.send(topic, String.valueOf(event.getCandidateId()), event);
    }
}
