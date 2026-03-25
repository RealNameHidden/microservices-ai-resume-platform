package com.msgeekforgeeks.resume_parser.model;

import lombok.Data;

import java.util.List;

/**
 * Structured data extracted by Claude from the resume PDF.
 * Claude is asked to return JSON matching this shape exactly.
 */
@Data
public class ParsedCandidate {
    private String name;
    private String location;
    private List<String> skills;
    private Integer yearsOfExperience;
    private String summary;
}
