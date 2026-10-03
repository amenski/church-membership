package io.github.membertracker.utils;

import io.github.membertracker.infrastructure.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtils {

    private final AuthProperties authProperties;

    public CookieUtils(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public ResponseCookie buildAccessCookie(String value) {
        return buildCookie(
            authProperties.getCookies().getAccessName(),
            value,
            authProperties.getAccessTtlSeconds(),
            "/",
            authProperties.getCookies().isSecure(),
            authProperties.getCookies().getSameSite(),
            authProperties.getCookies().getDomain()
        );
    }

    public ResponseCookie buildRefreshCookie(String value) {
        return buildRefreshCookie(value, authProperties.getRefreshTtlSeconds());
    }

    /** Refresh cookie that lives {@code maxAgeSeconds}; a rotated token has less than the full TTL left. */
    public ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        return buildCookie(
            authProperties.getCookies().getRefreshName(),
            value,
            maxAgeSeconds,
            "/api/auth",
            authProperties.getCookies().isSecure(),
            authProperties.getCookies().getSameSite(),
            authProperties.getCookies().getDomain()
        );
    }

    public ResponseCookie buildClearAccessCookie() {
        return buildClearCookie(
            authProperties.getCookies().getAccessName(),
            "/"
        );
    }

    public ResponseCookie buildClearRefreshCookie() {
        return buildClearCookie(
            authProperties.getCookies().getRefreshName(),
            "/api/auth"
        );
    }

    private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds, String path,
                                      boolean secure, String sameSite, String domain) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .path(path)
            .maxAge(maxAgeSeconds)
            .sameSite(sameSite);

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        return builder.build();
    }

    private ResponseCookie buildClearCookie(String name, String path) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(authProperties.getCookies().isSecure())
            .path(path)
            .maxAge(0)
            .sameSite(authProperties.getCookies().getSameSite());

        String domain = authProperties.getCookies().getDomain();
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        return builder.build();
    }
}