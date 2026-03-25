package com.msgeekforgeeks.resume_parser.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch document — indexed after parsing.
 * This is what search-service queries against.
 */
@Data
@Document(indexName = "candidates")
public class CandidateDocument {

    @Id
    private String candidateId;

    @Field(type = FieldType.Text)
    private String name;

    // text for full-text + keyword subfield for exact filter
    @Field(type = FieldType.Text)
    private String location;

    // keyword for exact skill matching (terms filter)
    @Field(type = FieldType.Keyword)
    private List<String> skills;

    @Field(type = FieldType.Integer)
    private Integer experience;

    @Field(type = FieldType.Text)
    private String summary;

    @Field(type = FieldType.Date)
    private LocalDateTime uploadedAt;
}
