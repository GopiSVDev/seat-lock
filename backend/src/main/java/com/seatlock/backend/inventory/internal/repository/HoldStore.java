package com.seatlock.backend.inventory.internal.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Seat holds in Redis; Postgres never sees them. Keys (all expire with the hold, so nothing cleans up):
//   seat:{show}:{seat}        hold id   the lock on one seat
//   hold:{hold}               hash      customer, show, seats (csv), expiresAt (epoch ms)
//   held:{show}               zset      seat id -> expiry: every held seat of a show in one read
//   user-hold:{show}:{user}   hold id   the customer's current hold for the show
// Writes go through the Lua scripts in resources/redis, so each one is atomic.
@Repository
@RequiredArgsConstructor
public class HoldStore {

    private static final RedisScript<Long> HOLD = script("hold-seats", Long.class);
    private static final RedisScript<Long> RELEASE = script("release-hold", Long.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CLAIM = script("claim-hold", List.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> EXTEND = script("extend-hold", List.class);

    private final StringRedisTemplate redis;

    // Empty if a seat is held by someone else
    public Optional<Instant> place(String holdId, Long showId, String customerId, List<Long> seatIds, Duration ttl) {
        List<String> keys = new ArrayList<>(List.of(heldKey(showId), userHoldKey(showId, customerId), holdKey(holdId)));

        List<String> args = new ArrayList<>(
                List.of(holdId, String.valueOf(ttl.toMillis()), customerId, showId.toString(), csv(seatIds)));

        for (Long seatId : seatIds) {
            keys.add("seat:" + showId + ":" + seatId);
            args.add(seatId.toString());
        }

        long expiresAt = redis.execute(HOLD, keys, args.toArray());
        return expiresAt == 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(expiresAt));
    }

    public Optional<HoldRecord> find(String holdId) {
        List<Object> fields = redis.opsForHash().multiGet(holdKey(holdId),
                List.<Object>of("customer", "show", "seats", "expiresAt"));

        if (fields.getFirst() == null) {
            return Optional.empty();
        }

        return Optional.of(new HoldRecord(holdId, (String) fields.get(0), Long.valueOf((String) fields.get(1)),
                seatIds((String) fields.get(2)), Instant.ofEpochMilli(Long.parseLong((String) fields.get(3)))));
    }

    // False if the hold is gone or someone else's
    public boolean release(String holdId, String customerId) {
        return redis.execute(RELEASE, List.of(holdKey(holdId)), holdId, customerId) == 1;
    }

    public Optional<ClaimedHold> claim(String holdId, String customerId, Duration grace) {
        List<?> result = redis.execute(CLAIM, List.of(holdKey(holdId)), holdId, customerId,
                String.valueOf(grace.toMillis()));

        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ClaimedHold(Long.valueOf((String) result.get(0)), seatIds((String) result.get(1))));
    }

    // Never shortens a hold; empty if it is gone or someone else's
    public Optional<HoldRecord> extend(String holdId, String customerId, Duration window) {
        List<?> result = redis.execute(EXTEND, List.of(holdKey(holdId)), holdId, customerId,
                String.valueOf(window.toMillis()));

        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new HoldRecord(holdId, customerId, Long.valueOf((String) result.get(0)),
                seatIds((String) result.get(1)), Instant.ofEpochMilli(Long.parseLong((String) result.get(2)))));
    }

    // Expired entries stay in the set until the next hold on the show removes them,
    // so filter by expiry
    public Set<Long> heldSeats(Long showId) {
        Set<String> seats = redis.opsForZSet().rangeByScore(heldKey(showId), System.currentTimeMillis(),
                Double.POSITIVE_INFINITY);

        return seats == null ? Set.of() : seats.stream().map(Long::valueOf).collect(Collectors.toSet());
    }

    private static String holdKey(String holdId) {
        return "hold:" + holdId;
    }

    private static String heldKey(Long showId) {
        return "held:" + showId;
    }

    private static String userHoldKey(Long showId, String customerId) {
        return "user-hold:" + showId + ":" + customerId;
    }

    private static String csv(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Long> seatIds(String csv) {
        return Arrays.stream(csv.split(",")).map(Long::valueOf).toList();
    }

    private static <T> RedisScript<T> script(String name, Class<T> resultType) {
        return RedisScript.of(new ClassPathResource("redis/" + name + ".lua"), resultType);
    }
}
