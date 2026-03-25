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

            log.info("Claude parsed resume: {}", jsonText);
            return objectMapper.readValue(jsonText, ParsedCandidate.class);

        } catch (Exception e) {
            log.error("Failed to parse resume via Claude: {}", e.getMessage(), e);
            return new ParsedCandidate();
        }
    }
}
