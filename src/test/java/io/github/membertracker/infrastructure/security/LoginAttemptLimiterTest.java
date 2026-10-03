package io.github.membertracker.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-04T10:00:00Z"));
        limiter = new LoginAttemptLimiter(clock);
    }

    private void fail(String ip, String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(ip, email);
        }
    }

    @Test
    void tenFailuresForOneEmailBlockTheEleventhAttempt() {
        fail("1.1.1.1", "a@example.com", 10);

        assertThatThrownBy(() -> limiter.check("1.1.1.1", "a@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void nineFailuresStillAllowAnAttempt() {
        fail("1.1.1.1", "a@example.com", 9);

        assertThatCode(() -> limiter.check("1.1.1.1", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void emailMatchingIsCaseInsensitiveAndIgnoresOuterSpaces() {
        fail("1.1.1.1", "Mixed@Example.com", 5);
        fail("2.2.2.2", " mixed@example.COM ", 5);

        assertThatThrownBy(() -> limiter.check("3.3.3.3", "MIXED@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void otherEmailsFromOtherIpsAreNotAffected() {
        fail("1.1.1.1", "a@example.com", 10);

        assertThatCode(() -> limiter.check("2.2.2.2", "b@example.com")).doesNotThrowAnyException();
    }

    @Test
    void thirtyFailuresFromOneIpBlockEveryEmailFromThatIp() {
        for (int i = 0; i < 30; i++) {
            limiter.recordFailure("9.9.9.9", "user" + i + "@example.com");
        }

        assertThatThrownBy(() -> limiter.check("9.9.9.9", "fresh@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.check("8.8.8.8", "fresh@example.com")).doesNotThrowAnyException();
    }

    @Test
    void twentyNineFailuresFromOneIpStillAllowAnAttempt() {
        for (int i = 0; i < 29; i++) {
            limiter.recordFailure("9.9.9.9", "user" + i + "@example.com");
        }

        assertThatCode(() -> limiter.check("9.9.9.9", "fresh@example.com")).doesNotThrowAnyException();
    }

    @Test
    void failuresLeaveTheWindowAfterTenMinutes() {
        fail("1.1.1.1", "a@example.com", 10);
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        assertThatThrownBy(() -> limiter.check("1.1.1.1", "a@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        clock.advance(Duration.ofSeconds(1));

        assertThatCode(() -> limiter.check("1.1.1.1", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void windowSlidesOneFailureAtATime() {
        fail("1.1.1.1", "a@example.com", 5);
        clock.advance(Duration.ofMinutes(6));
        fail("1.1.1.1", "a@example.com", 5);
        assertThatThrownBy(() -> limiter.check("1.1.1.1", "a@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        clock.advance(Duration.ofMinutes(4)); // the first five are now 10 minutes old

        assertThatCode(() -> limiter.check("1.1.1.1", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void retryAfterIsTheTimeUntilTheOldestFailureLeavesTheWindow() {
        fail("1.1.1.1", "a@example.com", 10);
        clock.advance(Duration.ofMinutes(4));

        assertThatThrownBy(() -> limiter.check("1.1.1.1", "a@example.com"))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(360));
    }

    @Test
    void successClearsTheEmailsFailures() {
        fail("1.1.1.1", "a@example.com", 9);

        limiter.recordSuccess("1.1.1.1", "A@example.com");
        fail("1.1.1.1", "a@example.com", 9);

        assertThatCode(() -> limiter.check("1.1.1.1", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void checkAloneNeverCountsAnAttempt() {
        for (int i = 0; i < 100; i++) {
            limiter.check("1.1.1.1", "a@example.com");
        }

        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void expiredEntriesAreRemovedSoMemoryDoesNotGrow() {
        for (int i = 0; i < 500; i++) {
            limiter.recordFailure("10.0.0." + i, "user" + i + "@example.com");
        }
        assertThat(limiter.trackedKeys()).isEqualTo(1000);

        clock.advance(Duration.ofMinutes(11));
        limiter.recordFailure("1.1.1.1", "new@example.com"); // any access sweeps

        assertThat(limiter.trackedKeys()).isEqualTo(2);
    }

    @Test
    void aSingleExpiredKeyIsRemovedWhenItIsChecked() {
        fail("1.1.1.1", "a@example.com", 3);
        clock.advance(Duration.ofMinutes(11));

        limiter.check("1.1.1.1", "a@example.com");

        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(2);
    }

    @Test
    void nullIpAndEmailAreHandled() {
        fail(null, null, 10);

        assertThatThrownBy(() -> limiter.check(null, null)).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
