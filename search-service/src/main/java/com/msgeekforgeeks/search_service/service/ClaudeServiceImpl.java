package com.msgeekforgeeks.search_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msgeekforgeeks.search_service.model.EsFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Real Claude API implementation — active in all profiles except "local".
 * Requires CLAUDE_API_KEY to be set.
 */
@Slf4j
@Service
@Profile("!local")
@RequiredArgsConstructor
public class ClaudeServiceImpl implements ClaudeService {

    private final WebClient claudeWebClient;
    private final ObjectMapper objectMapper;

    @Value("${claude.model}")
    private String claudeModel;

    private static final String SYSTEM_PROMPT =
            "You are a query translator for a resume search engine. " +
            "Convert the recruiter's natural language query into Elasticsearch filters. " +
            "Return ONLY valid JSON with no extra text, no markdown fences, no explanation.";

    private static final String USER_PROMPT_TEMPLATE =
            "Convert this recruiter query into Elasticsearch filters and return ONLY valid JSON:\n" +
            "{\n" +
            "  \"skills\": [],\n" +
            "  \"location\": \"\",\n" +
            "  \"minExperience\": 0\n" +
            "}\n\n" +
            "Recruiter query: %s";

    @Override
    public EsFilters translateQuery(String recruiterQuery) {
        log.info("Sending query to Claude for translation: {}", recruiterQuery);

        String userMessage = String.format(USER_PROMPT_TEMPLATE, recruiterQuery);

        Map<String, Object> requestBody = Map.of(
                "model", claudeModel,
                "max_tokens", 256,
                "system", SYSTEM_PROMPT,
                "messages", List.of(
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            String rawResponse = claudeWebClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("Raw Claude response: {}", rawResponse);

            JsonNode root = objectMapper.readTree(rawResponse);
            String jsonText = root.path("content").get(0).path("text").asText();

            log.info("Claude translated filters: {}", jsonText);
            return objectMapper.readValue(jsonText, EsFilters.class);

        } catch (Exception e) {
            log.error("Failed to translate query via Claude: {}", e.getMessage(), e);
            return new EsFilters();
        }
    }
}
