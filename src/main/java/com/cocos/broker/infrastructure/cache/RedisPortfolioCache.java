package com.cocos.broker.infrastructure.cache;

import com.cocos.broker.domain.model.Portfolio;
import com.cocos.broker.domain.port.PortfolioCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside del portfolio en Redis (JSON). Tolerante a fallos: si Redis no está
 * disponible o hay error de (de)serialización, degrada a cache-miss/no-op.
 */
@Component
public class RedisPortfolioCache implements PortfolioCache {

    private static final Logger log = LoggerFactory.getLogger(RedisPortfolioCache.class);
    private static final String PREFIX = "portfolio:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisPortfolioCache(StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               @Value("${app.cache.portfolio-ttl-seconds:60}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<Portfolio> get(Long userId) {
        try {
            String json = redis.opsForValue().get(PREFIX + userId);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, Portfolio.class));
        } catch (Exception e) {
            log.warn("Cache de portfolio no disponible (get) user={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long userId, Portfolio portfolio) {
        try {
            redis.opsForValue().set(PREFIX + userId, objectMapper.writeValueAsString(portfolio), ttl);
        } catch (Exception e) {
            log.warn("Cache de portfolio no disponible (put) user={}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void evict(Long userId) {
        try {
            redis.delete(PREFIX + userId);
        } catch (Exception e) {
            log.warn("Cache de portfolio no disponible (evict) user={}: {}", userId, e.getMessage());
        }
    }
}
