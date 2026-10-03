package io.github.membertracker.infrastructure.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/**
 * The one place that defines the API error body: Spring's RFC 7807
 * {@link ProblemDetail} plus the properties {@code path} and {@code timestamp}.
 * Optional properties ({@code code}, {@code errors}) are added by the caller.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    /** Builds a problem for the request currently being handled. */
    public static ProblemDetail of(HttpStatus status, String detail) {
        return of(status, detail, currentPath());
    }

    public static ProblemDetail of(HttpStatus status, String detail, String path) {
        return addCommonProperties(ProblemDetail.forStatusAndDetail(status, detail), path);
    }

    /** Adds {@code path} and {@code timestamp} to a problem built elsewhere (for example by Spring). */
    public static ProblemDetail addCommonProperties(ProblemDetail problem, String path) {
        problem.setProperty("path", path);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    private static String currentPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest().getRequestURI()
                : null;
    }
}
