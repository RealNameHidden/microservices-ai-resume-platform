package com.msgeekforgeeks.candidate_service.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MySQL entity — source of truth for candidate data.
 * ES is a derived index built by resume-parser after processing.
 */
@Data
@Entity
@Table(name = "candidates")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    // Path to the uploaded PDF on disk (or object storage key later)
    private String resumePath;

    private LocalDateTime uploadedAt;
}
