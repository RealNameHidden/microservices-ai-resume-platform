package com.msgeekforgeeks.resume_parser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msgeekforgeeks.resume_parser.model.ParsedCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeParserService {

    private final WebClient claudeWebClient;
    private final ObjectMapper objectMapper;

    @Value("${claude.model}")
    private String claudeModel;

    @Value("${claude.api.key}")
    private String claudeApiKey;

    private static final String SYSTEM_PROMPT =
            "You are a resume parser. Extract structured data from the resume text provided. " +
            "Return ONLY valid JSON with no extra text, no markdown fences, no explanation.";

    private static final String USER_PROMPT_TEMPLATE =
            "Extract the following from this resume and return ONLY valid JSON:\n" +
            "{\n" +
            "  \"name\": \"\",\n" +
            "  \"location\": \"\",\n" +
            "  \"skills\": [],\n" +
            "  \"yearsOfExperience\": 0,\n" +
            "  \"summary\": \"\"\n" +
            "}\n\n" +
            "Resume text:\n%s";

    public ParsedCandidate extract(String resumeText) {
        log.info("Sending resume text to Claude for parsing ({} chars)", resumeText.length());

        if ("not-set".equals(claudeApiKey) || "your-api-key-here".equals(claudeApiKey)) {
            log.warn("No Claude API key set — returning mock parsed candidate");
            ParsedCandidate mock = new ParsedCandidate();
            mock.setName("Mock Candidate");
            mock.setLocation("Remote");
            mock.setSkills(List.of("Java", "Go", "Spring Boot", "Kafka", "Elasticsearch"));
            mock.setYearsOfExperience(5);
            mock.setSummary("Mock summary — set CLAUDE_API_KEY for real parsing");
            return mock;
        }

        String userMessage = String.format(USER_PROMPT_TEMPLATE, resumeText);

        Map<String, Object> requestBody = Map.of(
                "model", claudeModel,
                "max_tokens", 512,
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

            // Strip markdown code fences if Claude wraps the JSON anyway
            jsonText = jsonText.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)```\\s*$", "").trim();

            log.info("Claude parsed resume: {}", jsonText);
            return objectMapper.readValue(jsonText, ParsedCandidate.class);

        } catch (Exception e) {
            log.error("Failed to parse resume via Claude: {}", e.getMessage(), e);
            return new ParsedCandidate();
        }
    }
}
