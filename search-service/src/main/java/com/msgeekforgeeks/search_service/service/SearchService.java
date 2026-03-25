package com.msgeekforgeeks.search_service.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.msgeekforgeeks.search_service.model.CandidateDocument;
import com.msgeekforgeeks.search_service.model.EsFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the full search flow:
 *
 *  1. Translate recruiter query → EsFilters via Claude
 *  2. Check Redis cache — return immediately on HIT
 *  3. On MISS: build + execute Elasticsearch bool query
 *  4. Store results in Redis
 *  5. Return ranked candidates
 *
 * The ES query is a bool query combining:
 *  - terms filter on `skills` (keyword field — exact match)
 *  - match query on `location`  (text field — fuzzy friendly)
 *  - range filter on `experience` (>= minExperience)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ClaudeService claudeService;
    private final CacheService cacheService;
    private final ElasticsearchClient esClient;

    private static final String CANDIDATES_INDEX = "candidates";

    public record SearchResult(EsFilters filters, boolean fromCache, List<CandidateDocument> candidates) {}

    /**
     * Main entry point. Returns filters (for the response metadata),
     * a cache-hit flag, and the ranked candidates.
     */
    public SearchResult search(String recruiterQuery) {

        // Step 1 — translate query via Claude
        EsFilters filters = claudeService.translateQuery(recruiterQuery);

        // Step 2 — Redis cache check (keyed on filters, not raw query)
        List<CandidateDocument> cached = cacheService.get(filters);
        if (cached != null) {
            return new SearchResult(filters, true, cached);
        }

        // Step 3 — Elasticsearch query
        List<CandidateDocument> results = queryElasticsearch(filters);

        // Step 4 — cache results
        cacheService.put(filters, results);

        return new SearchResult(filters, false, results);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<CandidateDocument> queryElasticsearch(EsFilters filters) {
        List<Query> mustClauses = new ArrayList<>();
        List<Query> filterClauses = new ArrayList<>();

        // --- skills: terms filter (keyword exact match) ---
        if (filters.getSkills() != null && !filters.getSkills().isEmpty()) {
            filterClauses.add(Query.of(q -> q
                    .terms(t -> t
                            .field("skills")
                            .terms(tv -> tv.value(
                                    filters.getSkills().stream()
                                            .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                            .collect(Collectors.toList())
                            ))
                    )
            ));
        }

        // --- location: match query (text, handles partial / fuzzy) ---
        if (filters.getLocation() != null && !filters.getLocation().isBlank()) {
            mustClauses.add(Query.of(q -> q
                    .match(m -> m
                            .field("location")
                            .query(filters.getLocation())
                    )
            ));
        }

        // --- experience: range >= minExperience ---
        if (filters.getMinExperience() != null && filters.getMinExperience() > 0) {
            filterClauses.add(Query.of(q -> q
                    .range(r -> r
                            .field("experience")
                            .gte(JsonData.of(filters.getMinExperience()))
                    )
            ));
        }

        // Build the bool query
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        mustClauses.forEach(boolBuilder::must);
        filterClauses.forEach(boolBuilder::filter);

        // If no clauses at all, match_all
        Query finalQuery = (!mustClauses.isEmpty() || !filterClauses.isEmpty())
                ? Query.of(q -> q.bool(boolBuilder.build()))
                : Query.of(q -> q.matchAll(m -> m));

        try {
            SearchResponse<CandidateDocument> response = esClient.search(
                    SearchRequest.of(s -> s
                            .index(CANDIDATES_INDEX)
                            .query(finalQuery)
                            .size(20) // return top 20 matches
                    ),
                    CandidateDocument.class
            );

            List<CandidateDocument> results = response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .collect(Collectors.toList());

            log.info("ES returned {} candidates for filters: {}", results.size(), filters);
            return results;

        } catch (IOException e) {
            log.error("Elasticsearch query failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
