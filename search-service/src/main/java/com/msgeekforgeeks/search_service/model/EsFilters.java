package com.msgeekforgeeks.search_service.model;

import lombok.Data;
import java.util.List;

/**
 * Structured Elasticsearch filters extracted by Claude from a recruiter's
 * natural-language query.
 *
 * Claude is asked to return ONLY valid JSON matching this shape:
 * {
 *   "skills":        ["Java", "Kubernetes"],
 *   "location":      "Toronto",
 *   "minExperience": 3
 * }
 */
@Data
public class EsFilters {
    private List<String> skills;
    private String location;
    private Integer minExperience;
}
