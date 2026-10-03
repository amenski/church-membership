package io.github.membertracker.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory throttle for sign-in. It counts FAILED attempts in a sliding window, per client IP and per
 * email (case-insensitive). Successful sign-ins are never counted, and a success clears the email's
 * entries. State lives in this process only: a restart resets it.
 */
@Component
public class LoginAttemptLimiter {

    public static final Duration WINDOW = Duration.ofMinutes(10);
    public static final int IP_LIMIT = 30;
    public static final int EMAIL_LIMIT = 10;

    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();
    private final Map<String, Deque<Long>> byEmail = new HashMap<>();
    private long lastSweep;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
        this.lastSweep = clock.millis();
    }

    /** Throws when the IP or the email already has too many failures in the window. */
    public synchronized void check(String ip, String email) {
        long now = clock.millis();
        sweepIfDue(now);
        long retryAfter = Math.max(
                retryAfterMillis(byIp, ipKey(ip), IP_LIMIT, now),
                retryAfterMillis(byEmail, emailKey(email), EMAIL_LIMIT, now));
        if (retryAfter > 0) {
            throw new TooManyLoginAttemptsException(Math.max(1, (retryAfter + 999) / 1000));
        }
    }

    public synchronized void recordFailure(String ip, String email) {
        long now = clock.millis();
        sweepIfDue(now);
        byIp.computeIfAbsent(ipKey(ip), k -> new ArrayDeque<>()).addLast(now);
        byEmail.computeIfAbsent(emailKey(email), k -> new ArrayDeque<>()).addLast(now);
    }

    /** A successful sign-in forgets the failures recorded for that email. */
    public synchronized void recordSuccess(String ip, String email) {
        byEmail.remove(emailKey(email));
    }

    /** Milliseconds until the key is allowed again, or 0 when it is under the limit. */
    private long retryAfterMillis(Map<String, Deque<Long>> map, String key, int limit, long now) {
        Deque<Long> attempts = map.get(key);
        if (attempts == null) {
            return 0;
        }
        prune(attempts, now);
        if (attempts.isEmpty()) {
            map.remove(key);
            return 0;
        }
        if (attempts.size() < limit) {
            return 0;
        }
        // Allowed again when enough old failures have left the window to get under the limit.
        Iterator<Long> oldestFirst = attempts.iterator();
        long releasing = 0;
        for (int i = 0; i <= attempts.size() - limit; i++) {
            releasing = oldestFirst.next();
        }
        return releasing + WINDOW.toMillis() - now;
    }

    private void sweepIfDue(long now) {
        if (now - lastSweep < SWEEP_INTERVAL.toMillis()) {
            return;
        }
        lastSweep = now;
        sweep(byIp, now);
        sweep(byEmail, now);
    }

    private void sweep(Map<String, Deque<Long>> map, long now) {
        map.values().removeIf(attempts -> {
            prune(attempts, now);
            return attempts.isEmpty();
        });
    }

    private void prune(Deque<Long> attempts, long now) {
        long cutoff = now - WINDOW.toMillis();
        while (!attempts.isEmpty() && attempts.peekFirst() <= cutoff) {
            attempts.removeFirst();
        }
    }

    /** Number of keys currently held; for tests. */
    synchronized int trackedKeys() {
        return byIp.size() + byEmail.size();
    }

    private static String ipKey(String ip) {
        return ip == null ? "unknown" : ip;
    }

    private static String emailKey(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
