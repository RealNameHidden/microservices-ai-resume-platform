package com.msgeekforgeeks.candidate_service.service;

import com.msgeekforgeeks.candidate_service.kafka.ResumeEventProducer;
import com.msgeekforgeeks.candidate_service.kafka.ResumeUploadedEvent;
import com.msgeekforgeeks.candidate_service.model.Candidate;
import com.msgeekforgeeks.candidate_service.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final ResumeEventProducer eventProducer;

    @Value("${resume.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 1. Save the PDF to disk
     * 2. Persist candidate row in MySQL
     * 3. Fire resume.uploaded Kafka event
     */
    public Candidate uploadResume(String name, String email, MultipartFile file) {
        log.info("Received resume upload for candidate: {}", email);

        String filePath = saveFile(file);

        Candidate candidate = new Candidate();
        candidate.setName(name);
        candidate.setEmail(email);
        candidate.setResumePath(filePath);
        candidate.setUploadedAt(LocalDateTime.now());

        Candidate saved = candidateRepository.save(candidate);

        eventProducer.send(new ResumeUploadedEvent(
                saved.getId(),
                saved.getResumePath(),
                saved.getUploadedAt()
        ));

        return saved;
    }

    private String saveFile(MultipartFile file) {
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            // UUID prefix avoids filename collisions
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());

            log.info("Saved resume to: {}", target.toAbsolutePath());
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save resume file", e);
        }
    }

    public Candidate getById(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + id));
    }
}
