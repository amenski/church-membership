package io.github.membertracker.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-for-hs256-signing";
    private static final String OTHER_SECRET = "another-test-secret-that-is-long-enough-for-hs256";

    private final UserDetails user = new User("user@example.com", "pw", List.of());

    private String untyped(long ttlMillis, String secret) {
        return Jwts.builder()
                .setSubject("user@example.com")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void accessTokenPassesAccessAndFailsRefresh() {
        String token = JwtUtils.generateAccessToken(user, SECRET, 60);

        assertThat(JwtUtils.validateToken(token, SECRET, JwtUtils.TOKEN_TYPE_ACCESS)).isTrue();
        assertThat(JwtUtils.validateToken(token, SECRET, JwtUtils.TOKEN_TYPE_REFRESH)).isFalse();
    }

    @Test
    void refreshTokenPassesRefreshAndFailsAccess() {
        String token = JwtUtils.generateRefreshToken(user, SECRET, 60);

        assertThat(JwtUtils.validateToken(token, SECRET, JwtUtils.TOKEN_TYPE_REFRESH)).isTrue();
        assertThat(JwtUtils.validateToken(token, SECRET, JwtUtils.TOKEN_TYPE_ACCESS)).isFalse();
    }

    @Test
    void tokenWithoutTypeFailsBothTypes() {
        String token = untyped(60_000, SECRET);

        assertThat(JwtUtils.validateToken(token, SECRET, JwtUtils.TOKEN_TYPE_ACCESS)).isFalse();
        assertThat(JwtUtils.validateToken(token, SECRET, JwtUtils.TOKEN_TYPE_REFRESH)).isFalse();
    }

    @Test
    void expiredTokenFails() {
        String token = JwtUtils.generateAccessToken(user, SECRET, -10);

        assertThat(JwtUtils.validateToken(token, SECRET, JwtUtils.TOKEN_TYPE_ACCESS)).isFalse();
    }

    @Test
    void tokenSignedWithAnotherSecretFails() {
        String token = JwtUtils.generateAccessToken(user, OTHER_SECRET, 60);

        assertThat(JwtUtils.validateToken(token, SECRET, JwtUtils.TOKEN_TYPE_ACCESS)).isFalse();
    }

    @Test
    void garbageFails() {
        assertThat(JwtUtils.validateToken("not-a-jwt", SECRET, JwtUtils.TOKEN_TYPE_ACCESS)).isFalse();
    }

    @Test
    void missingSecretThrowsInsteadOfFallingBackToAKnownKey() {
        assertThatThrownBy(() -> JwtUtils.generateAccessToken(user, null, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret is not configured");
        assertThatThrownBy(() -> JwtUtils.generateAccessToken(user, " ", 60))
                .isInstanceOf(IllegalStateException.class);
    }
}
