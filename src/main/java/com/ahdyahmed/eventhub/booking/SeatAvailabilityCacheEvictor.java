package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.seat.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Evicts every {@code seat-availability} cache entry one event's seats
 * could be cached under.
 *
 * <p>Extracted out of {@code BookingServiceImpl} on Day 14, where it
 * started life as a private method on Day 9: {@link PaymentProcessedListener}
 * needs the exact same eviction after the payment step flips seats between
 * {@code RESERVED}, {@code BOOKED}, and (on a decline) back to {@code
 * AVAILABLE}, and duplicating the key-enumeration logic in two places would
 * mean the two copies silently drifting out of sync the next time {@link
 * SeatStatus} gains a value. One class, one definition of "every key this
 * event's seat listing could live under" — see {@code
 * SeatServiceImpl.getByEvent}'s {@code @Cacheable} key expression for the
 * convention this mirrors.</p>
 */
@Component
@RequiredArgsConstructor
public class SeatAvailabilityCacheEvictor {

    private final CacheManager cacheManager;

    public void evict(Long eventId) {
        Cache cache = cacheManager.getCache("seat-availability");
        if (cache == null) {
            return;
        }
        cache.evict(eventId + "-null");
        for (SeatStatus status : SeatStatus.values()) {
            cache.evict(eventId + "-" + status);
        }
    }
}
