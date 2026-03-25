package com.msgeekforgeeks.candidate_service.controller;

import com.msgeekforgeeks.candidate_service.model.Candidate;
import com.msgeekforgeeks.candidate_service.service.CandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    /**
     * POST /candidates/upload
     * Accepts a PDF resume plus name + email form fields.
     * Saves the file, persists candidate, fires Kafka event.
     */
    @PostMapping("/upload")
    public ResponseEntity<Candidate> upload(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam MultipartFile file) {
        return ResponseEntity.ok(candidateService.uploadResume(name, email, file));
    }

    /**
     * GET /candidates/{id}
     * Fetch a candidate profile by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Candidate> getById(@PathVariable Long id) {
        return ResponseEntity.ok(candidateService.getById(id));
    }
}
