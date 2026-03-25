package com.msgeekforgeeks.search_service.model;

import lombok.Data;

@Data
public class SearchRequest {
    // Natural language query from the recruiter
    // e.g. "find me a Java dev in Toronto with Kubernetes experience"
    private String query;
}
