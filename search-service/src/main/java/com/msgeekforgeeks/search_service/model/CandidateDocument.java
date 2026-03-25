package com.msgeekforgeeks.search_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

/**
 * Elasticsearch document representing a parsed candidate resume.
 *
 * Index mapping mirrors the design in projectRevamp.md:
 *   - skills / location stored as keyword for exact matching
 *   - summary stored as text for full-text search
 *   - experience as integer for range queries
 */
@Data
@Document(indexName = "candidates")
public class CandidateDocument {

    @Id
    private String candidateId;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String summary;

    // keyword subfield lets us do both full-text and exact matching
    @Field(type = FieldType.Text, fielddata = true)
    private String location;

    // keyword for exact skill matching — O(1) inverted index lookup
    @Field(type = FieldType.Keyword)
    private List<String> skills;

    @Field(type = FieldType.Integer)
    private Integer experience;

    @Field(type = FieldType.Date)
    private String uploadedAt;
}
