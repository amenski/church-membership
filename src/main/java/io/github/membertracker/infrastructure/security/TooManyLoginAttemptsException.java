package io.github.membertracker.infrastructure.security;

/** Thrown when a client or an email has used up its failed sign-in attempts for the current window. */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Too many sign-in attempts. Try again in a few minutes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
