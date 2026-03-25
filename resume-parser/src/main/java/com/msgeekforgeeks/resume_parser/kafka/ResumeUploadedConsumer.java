package com.msgeekforgeeks.resume_parser.kafka;

import com.msgeekforgeeks.resume_parser.service.ParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeUploadedConsumer {

    private final ParserService parserService;

    /**
     * Listens on resume.uploaded topic.
     * Each message triggers the full parse pipeline:
     *   read PDF → call Claude → save to MySQL → index in ES
     */
    @KafkaListener(
            topics = "${kafka.topic.resume-uploaded:resume.uploaded}",
            groupId = "${spring.kafka.consumer.group-id:resume-parser-group}"
    )
    public void consume(ResumeUploadedEvent event) {
        log.info("Received resume.uploaded event for candidateId={}", event.getCandidateId());
        parserService.parse(event);
    }
}
