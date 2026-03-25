package com.msgeekforgeeks.search_service.controller;

import com.msgeekforgeeks.search_service.model.SearchRequest;
import com.msgeekforgeeks.search_service.model.SearchResponse;
import com.msgeekforgeeks.search_service.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * POST /search-service/search
     *
     * Body: { "query": "find me a Java dev in Toronto with Kubernetes experience" }
     *
     * Returns ranked candidates + metadata (cache hit, derived ES filters).
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {
        log.info("Received search request: {}", request.getQuery());

        SearchService.SearchResult result = searchService.search(request.getQuery());

        SearchResponse response = new SearchResponse(
                request.getQuery(),
                result.filters(),
                result.fromCache(),
                result.candidates()
        );

        return ResponseEntity.ok(response);
    }
}
