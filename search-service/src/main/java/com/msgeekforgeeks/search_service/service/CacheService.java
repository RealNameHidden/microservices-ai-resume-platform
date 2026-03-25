package com.msgeekforgeeks.search_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msgeekforgeeks.search_service.model.CandidateDocument;
import com.msgeekforgeeks.search_service.model.EsFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-through Redis cache for recruiter search results.
 *
 * Cache key  = the exact recruiter query string (lowercased + trimmed)
 * Cache value = JSON-serialised List<CandidateDocument>
 * TTL         = configurable via search.cache.ttl-seconds (default 10 min)
 *
 * Why cache here?
 * "find Java devs in Toronto" is asked dozens of times a day.
 * Caching saves repeated Claude API calls AND repeated ES queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${search.cache.ttl-seconds:600}")
    private long cacheTtlSeconds;

    private static final String CACHE_PREFIX = "search:";

    /**
     * Returns cached results for the given filters, or null on a miss.
     */
    public List<CandidateDocument> get(EsFilters filters) {
        String key = buildKey(filters);
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                log.debug("Cache MISS for key: {}", key);
                return null;
            }
            log.info("Cache HIT for key: {}", key);
            return objectMapper.convertValue(raw, new TypeReference<List<CandidateDocument>>() {});
        } catch (Exception e) {
            log.warn("Redis read failed — treating as cache miss: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Stores search results in Redis with the configured TTL.
     */
    public void put(EsFilters filters, List<CandidateDocument> candidates) {
        String key = buildKey(filters);
        try {
            redisTemplate.opsForValue().set(key, candidates, Duration.ofSeconds(cacheTtlSeconds));
            log.info("Cached {} candidates for key: {} (TTL={}s)", candidates.size(), key, cacheTtlSeconds);
        } catch (Exception e) {
            log.warn("Redis write failed — search will continue without caching: {}", e.getMessage());
        }
    }

    /**
     * Builds a deterministic key from the structured filters so that differently-worded
     * queries that resolve to the same filters share a single cache entry.
     *
     * Skills are sorted alphabetically so ["Java","Spring"] and ["Spring","Java"] produce
     * the same key. Location is lowercased. minExperience defaults to 0 when absent.
     */
    private String buildKey(EsFilters filters) {
        List<String> skills = new ArrayList<>(
                filters.getSkills() != null ? filters.getSkills() : Collections.emptyList());
        Collections.sort(skills);

        String location = filters.getLocation() != null ? filters.getLocation().trim().toLowerCase() : "";
        int minExp = filters.getMinExperience() != null ? filters.getMinExperience() : 0;

        return CACHE_PREFIX + String.join(",", skills) + ":" + location + ":" + minExp;
    }
}