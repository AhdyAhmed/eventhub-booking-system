package com.ahdyahmed.eventhub.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Cache-aside wiring for Day 8. Three cache names back the read-heavy
 * endpoints called out in the roadmap:
 *
 * <ul>
 *   <li>{@code events} — a single {@code EventResponse} by id
 *       ({@code GET /api/v1/events/{id}}).</li>
 *   <li>{@code event-search} — a page of {@code EventResponse} for one
 *       combination of filter criteria + {@code Pageable}
 *       ({@code GET /api/v1/events}). Spring's default key generator builds
 *       the cache key from all method arguments, which works here because
 *       both {@code EventSearchCriteria} (a record) and Spring Data's
 *       {@code PageRequest} implement {@code equals}/{@code hashCode}.</li>
 *   <li>{@code seat-availability} — the seat list for one event + status
 *       filter combination ({@code GET /api/v1/events/{eventId}/seats}).</li>
 * </ul>
 *
 * <p>Each gets its own TTL rather than sharing the blanket default from
 * {@code application.yml}: seat availability is the most volatile of the
 * three (every booking touches it) so it's given the shortest TTL, while a
 * single event's details change rarely. None of this is a substitute for
 * eviction on write — that's what makes it "cache-aside" rather than
 * "eventually correct in under N minutes" — see {@code EventServiceImpl}'s
 * and {@code SeatServiceImpl}'s {@code @CacheEvict} annotations. The one gap
 * deliberately left open here is booking's effect on {@code
 * seat-availability}: {@code BookingServiceImpl} changes a seat's status but
 * doesn't currently evict this cache, so a just-booked seat can still show as
 * available for up to 30 seconds. That's Day 9's fix, not an oversight.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(redisObjectMapper()));

        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                "events", cacheConfig(valueSerializer, Duration.ofMinutes(5)),
                "event-search", cacheConfig(valueSerializer, Duration.ofMinutes(1)),
                "seat-availability", cacheConfig(valueSerializer, Duration.ofSeconds(30))
        );

        return builder -> builder.withInitialCacheConfigurations(perCacheConfig);
    }

    /**
     * {@code GenericJackson2JsonRedisSerializer}'s no-arg constructor builds
     * its own internal {@code ObjectMapper} that does <em>not</em> pick up
     * {@code jackson-datatype-jsr310} automatically — every {@code Instant}
     * field in this app (every entity's {@code createdAt}/{@code updatedAt},
     * {@code Event.eventDate}) failed to serialize under it with
     * {@code "Java 8 date/time type `java.time.Instant` not supported by
     * default"}. Building the mapper explicitly and registering {@link
     * JavaTimeModule} on it fixes that.
     *
     * <p>{@code DefaultTyping.EVERYTHING}, not {@code NON_FINAL}, is
     * required here — every DTO in this app ({@code EventResponse}, {@code
     * SeatResponse}, ...) is a Java {@code record}, and records are
     * implicitly {@code final}. {@code NON_FINAL} skips writing the {@code
     * "@class"} type-id property for final classes on the assumption that
     * the declared type is unambiguous, which is true at the call site but
     * not for a Redis cache: {@code RedisCache} always reads values back as
     * plain {@code Object}, so without the type id Jackson has no way to
     * know which concrete class to reconstruct — the exact {@code "missing
     * type id property '@class'"} failure this caused on read.</p>
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    private RedisCacheConfiguration cacheConfig(
            RedisSerializationContext.SerializationPair<Object> valueSerializer, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeValuesWith(valueSerializer)
                .disableCachingNullValues();
    }

    /**
     * Without this, a Redis outage doesn't just mean "no caching" — it means
     * every write that happens to hit a {@code @CacheEvict} (or read behind
     * a {@code @Cacheable}) throws and the request fails, even though the
     * actual Postgres write already succeeded. That's a worse failure mode
     * than the cache simply not working: caching is meant to be a
     * performance layer, not a new single point of failure for the write
     * path underneath it. This logs the failure and lets the request
     * continue as if the cache operation had been a (temporary) miss.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache,
                                             Object key) {
                log.warn("Cache GET failed on '{}' for key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache,
                                             Object key, Object value) {
                log.warn("Cache PUT failed on '{}' for key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache,
                                               Object key) {
                log.warn("Cache EVICT failed on '{}' for key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.warn("Cache CLEAR failed on '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }

}
