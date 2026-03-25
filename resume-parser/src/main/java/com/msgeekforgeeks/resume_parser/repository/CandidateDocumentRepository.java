package com.msgeekforgeeks.resume_parser.repository;

import com.msgeekforgeeks.resume_parser.model.CandidateDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CandidateDocumentRepository extends ElasticsearchRepository<CandidateDocument, String> {
}
