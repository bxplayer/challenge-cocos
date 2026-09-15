package com.cocos.broker.infrastructure.cache;

import com.cocos.broker.domain.port.IdempotencyStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotencia en Redis. La reserva usa SET NX (atómico) con un valor
 * centinela vacío; {@link #put} lo reemplaza por el resultado. Tolerante a
 * fallos: si Redis no está disponible, degrada a "sin idempotencia" (la
 * solicitud se procesa).
 */
@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);
    private static final String PREFIX = "idempotency:";
    private static final String PENDING = "";

    private final StringRedisTemplate redis;

    @Override
    public boolean reserve(String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + key, PENDING, ttl));
        } catch (Exception e) {
            log.warn("Redis no disponible para idempotencia (reserve): {}", e.getMessage());
            return true;
        }
    }

    @Override
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + key))
                    .filter(value -> !PENDING.equals(value));
        } catch (Exception e) {
            log.warn("Redis no disponible para idempotencia (get): {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis no disponible para idempotencia (put): {}", e.getMessage());
        }
    }

    @Override
    public void release(String key) {
        try {
            redis.delete(PREFIX + key);
        } catch (Exception e) {
            log.warn("Redis no disponible para idempotencia (release): {}", e.getMessage());
        }
    }
}
