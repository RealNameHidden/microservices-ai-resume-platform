package com.msgeekforgeeks.search_service.service;

import com.msgeekforgeeks.search_service.model.EsFilters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local-dev stub — active only when spring.profiles.active=local.
 *
 * Parses the recruiter query with basic keyword matching instead of calling
 * Claude. Good enough to exercise the full Redis → ES flow without needing
 * an API key.
 *
 * Recognised skills: Java, Python, Go, Kubernetes, Docker, Spring, React,
 *   AWS, Kafka, Elasticsearch, Redis, MySQL, Node, TypeScript
 *
 * Recognised cities: Toronto, Vancouver, London, New York, Austin, Berlin
 *
 * Experience: looks for patterns like "5 years", "3+ years"
 */
@Slf4j
@Service
@Profile("local")
public class ClaudeServiceStub implements ClaudeService {

    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Python", "Go", "Kubernetes", "Docker", "Spring",
            "React", "AWS", "Kafka", "Elasticsearch", "Redis", "MySQL",
            "Node", "TypeScript"
    );

    private static final Map<String, String> CITY_ALIASES = Map.of(
            "toronto", "Toronto",
            "vancouver", "Vancouver",
            "london", "London",
            "new york", "New York",
            "austin", "Austin",
            "berlin", "Berlin"
    );

    // Matches "3 years", "5+ years", "at least 4 years"
    private static final Pattern EXPERIENCE_PATTERN =
            Pattern.compile("(\\d+)\\+?\\s+years?", Pattern.CASE_INSENSITIVE);

    @Override
    public EsFilters translateQuery(String recruiterQuery) {
        log.info("[STUB] Parsing query locally (no Claude API call): {}", recruiterQuery);

        String lower = recruiterQuery.toLowerCase();

        // Extract skills
        List<String> matchedSkills = new ArrayList<>();
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase())) {
                matchedSkills.add(skill);
            }
        }

        // Extract location
        String location = null;
        for (Map.Entry<String, String> entry : CITY_ALIASES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                location = entry.getValue();
                break;
            }
        }

        // Extract min experience
        Integer minExperience = 0;
        Matcher m = EXPERIENCE_PATTERN.matcher(recruiterQuery);
        if (m.find()) {
            minExperience = Integer.parseInt(m.group(1));
        }

        EsFilters filters = new EsFilters();
        filters.setSkills(matchedSkills);
        filters.setLocation(location);
        filters.setMinExperience(minExperience);

        log.info("[STUB] Derived filters: skills={}, location={}, minExperience={}",
                matchedSkills, location, minExperience);

        return filters;
    }
}
