package com.msgeekforgeeks.search_service.service;

import com.msgeekforgeeks.search_service.model.EsFilters;

/**
 * Contract for translating a recruiter's natural-language query into
 * structured Elasticsearch filters.
 *
 * Two implementations:
 *  - ClaudeServiceImpl  — real Claude API call (default / prod profile)
 *  - ClaudeServiceStub  — hardcoded filters, no API call (local profile)
 */
public interface ClaudeService {
    EsFilters translateQuery(String recruiterQuery);
}
