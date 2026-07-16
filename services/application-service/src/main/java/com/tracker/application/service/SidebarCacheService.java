package com.tracker.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.application.dto.ApplicationDtos.ApplicationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Caches the sorted sidebar per (userId, sort, order) in Redis.
 * All of a user's cache keys are tracked in a companion Set so invalidation on any
 * tile write is a single, precise operation (no KEYS scan).
 */
@Service
public class SidebarCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public SidebarCacheService(StringRedisTemplate redis, ObjectMapper mapper,
                               @Value("${sidebar.cache-ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    private String valueKey(String userId, String sort, String order) {
        return "sidebar:" + userId + ":" + sort + ":" + order;
    }

    private String indexKey(String userId) {
        return "sidebar:keys:" + userId;
    }

    public Optional<List<ApplicationResponse>> get(String userId, String sort, String order) {
        String json = redis.opsForValue().get(valueKey(userId, sort, order));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, new TypeReference<List<ApplicationResponse>>() {}));
        } catch (Exception e) {
            return Optional.empty();   // treat corrupt cache as a miss
        }
    }

    public void put(String userId, String sort, String order, List<ApplicationResponse> value) {
        try {
            String key = valueKey(userId, sort, order);
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
            redis.opsForSet().add(indexKey(userId), key);
            redis.expire(indexKey(userId), ttl.plusMinutes(1));
        } catch (Exception ignored) {
            // cache write failures must never break the request
        }
    }

    /** Invalidate every sidebar variant for a user. Called on any tile write. */
    public void invalidate(String userId) {
        String index = indexKey(userId);
        Set<String> keys = redis.opsForSet().members(index);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        redis.delete(index);
    }
}
