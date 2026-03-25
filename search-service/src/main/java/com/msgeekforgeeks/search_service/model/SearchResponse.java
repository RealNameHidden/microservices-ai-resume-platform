package com.msgeekforgeeks.search_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Returned to the recruiter after a search.
 * Includes meta-info so they (and interviewers) can see what happened
 * under the hood: whether Redis served the result, and what filters
 * Claude derived from their natural-language query.
 */
@Data
@AllArgsConstructor
public class SearchResponse {

    /** The natural-language query the recruiter submitted. */
    private String originalQuery;

    /** Structured filters Claude extracted from the query. */
    private EsFilters derivedFilters;

    /** Whether the result was served from Redis cache or hit Elasticsearch. */
    private boolean servedFromCache;

    /** Ranked list of matching candidates. */
    private List<CandidateDocument> candidates;
}
