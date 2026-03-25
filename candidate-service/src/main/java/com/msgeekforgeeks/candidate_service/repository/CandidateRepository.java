package com.msgeekforgeeks.candidate_service.repository;

import com.msgeekforgeeks.candidate_service.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
}
