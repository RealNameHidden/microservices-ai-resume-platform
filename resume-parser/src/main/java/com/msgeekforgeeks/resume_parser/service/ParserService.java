package com.msgeekforgeeks.resume_parser.service;

import com.msgeekforgeeks.resume_parser.kafka.ResumeUploadedEvent;
import com.msgeekforgeeks.resume_parser.model.CandidateDocument;
import com.msgeekforgeeks.resume_parser.model.ParsedCandidate;
import com.msgeekforgeeks.resume_parser.repository.CandidateDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParserService {

    private final PdfService pdfService;
    private final ClaudeParserService claudeParserService;
    private final CandidateDocumentRepository esRepository;

    /**
     * Full parse pipeline triggered by a resume.uploaded Kafka event:
     *
     *  1. Read PDF text from disk
     *  2. Call Claude to extract structured skills/location/experience
     *  3. Index the result in Elasticsearch
     *
     * TODO: Step 3b — also update the MySQL candidate row with parsed data
     */
    public void parse(ResumeUploadedEvent event) {
        try {
            // Step 1 — extract raw text from PDF
            String resumeText = pdfService.extractText(event.getFilePath());

            // Step 2 — call Claude to get structured data
            ParsedCandidate parsed = claudeParserService.extract(resumeText);

            // Step 3 — index in Elasticsearch
            CandidateDocument doc = toDocument(event, parsed);
            esRepository.save(doc);

            log.info("Indexed candidateId={} with skills={}", event.getCandidateId(), parsed.getSkills());

        } catch (Exception e) {
            log.error("Failed to parse resume for candidateId={}: {}", event.getCandidateId(), e.getMessage(), e);
        }
    }

    private CandidateDocument toDocument(ResumeUploadedEvent event, ParsedCandidate parsed) {
        CandidateDocument doc = new CandidateDocument();
        doc.setCandidateId(String.valueOf(event.getCandidateId()));
        doc.setName(parsed.getName());
        doc.setLocation(parsed.getLocation());
        doc.setSkills(parsed.getSkills());
        doc.setExperience(parsed.getYearsOfExperience());
        doc.setSummary(parsed.getSummary());
        doc.setUploadedAt(event.getUploadedAt());
        return doc;
    }
}
