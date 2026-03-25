package com.msgeekforgeeks.search_service.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.msgeekforgeeks.search_service.model.CandidateDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Seeds Elasticsearch with sample candidates on startup.
 * Active only when spring.profiles.active=local.
 *
 * Why ApplicationRunner instead of @PostConstruct?
 * ApplicationRunner fires after the full Spring context is ready — including
 * the ES client being fully initialised. @PostConstruct can fire too early.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final ElasticsearchClient esClient;

    private static final String INDEX = "candidates";

    @Override
    public void run(ApplicationArguments args) {
        log.info("[SEEDER] Seeding Elasticsearch with sample candidates...");

        List<CandidateDocument> candidates = List.of(
            candidate("c1", "Alice Chen",    "Toronto",     List.of("Java", "Spring", "Kubernetes", "MySQL"), 6, "Senior Java engineer with strong k8s experience."),
            candidate("c2", "Bob Patel",     "Toronto",     List.of("Java", "Kafka", "Docker", "AWS"),        4, "Backend engineer specialising in event-driven systems."),
            candidate("c3", "Clara Müller",  "Berlin",      List.of("Python", "Kafka", "Elasticsearch"),      3, "Data engineer with search and streaming expertise."),
            candidate("c4", "Dan Okafor",    "Vancouver",   List.of("Go", "Kubernetes", "Docker", "AWS"),     5, "Platform engineer focused on cloud-native infra."),
            candidate("c5", "Eva Martínez",  "Toronto",     List.of("React", "TypeScript", "Node"),           2, "Full-stack developer with modern frontend skills.")
        );

        for (CandidateDocument c : candidates) {
            try {
                esClient.index(IndexRequest.of(i -> i
                        .index(INDEX)
                        .id(c.getCandidateId())
                        .document(c)
                ));
                log.info("[SEEDER] Indexed candidate: {} ({})", c.getName(), c.getCandidateId());
            } catch (Exception e) {
                log.warn("[SEEDER] Failed to index {}: {}", c.getCandidateId(), e.getMessage());
            }
        }

        log.info("[SEEDER] Done. {} candidates available in ES.", candidates.size());
    }

    private CandidateDocument candidate(String id, String name, String location,
                                        List<String> skills, int experience, String summary) {
        CandidateDocument doc = new CandidateDocument();
        doc.setCandidateId(id);
        doc.setName(name);
        doc.setLocation(location);
        doc.setSkills(skills);
        doc.setExperience(experience);
        doc.setSummary(summary);
        doc.setUploadedAt(Instant.now().toString());
        return doc;
    }
}
