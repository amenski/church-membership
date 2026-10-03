package io.github.membertracker.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class JwtUtils {

    public static final String TOKEN_TYPE_CLAIM = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    /** Epoch seconds of the original sign-in; carried unchanged through every refresh. */
    public static final String AUTH_TIME_CLAIM = "auth_time";

    private JwtUtils() {
        // Utility class - prevent instantiation
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String extractUsername(String token, String jwtSecret) {
        return extractClaim(token, jwtSecret, Claims::getSubject);
    }

    public static Date extractExpiration(String token, String jwtSecret) {
        return extractClaim(token, jwtSecret, Claims::getExpiration);
    }

    public static <T> T extractClaim(String token, String jwtSecret, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token, jwtSecret);
        return claimsResolver.apply(claims);
    }

    private static Claims extractAllClaims(String token, String jwtSecret) {
        SecretKey secretKey = getSecretKey(jwtSecret);
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private static boolean isTokenExpired(String token, String jwtSecret) {
        return extractExpiration(token, jwtSecret).before(new Date());
    }

    private static boolean isTokenValid(String token, String jwtSecret) {
        return !isTokenExpired(token, jwtSecret);
    }

    /** Access token for a sign-in happening now. */
    public static String generateAccessToken(UserDetails userDetails, String jwtSecret, long accessTtlSeconds) {
        return generateAccessToken(userDetails, jwtSecret, accessTtlSeconds, nowSeconds());
    }

    /** Access token that expires {@code accessTtlSeconds} from now and records the original sign-in time. */
    public static String generateAccessToken(UserDetails userDetails, String jwtSecret, long accessTtlSeconds, long authTimeSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS);
        claims.put(AUTH_TIME_CLAIM, authTimeSeconds);
        return createToken(claims, userDetails.getUsername(), System.currentTimeMillis() + accessTtlSeconds * 1000, jwtSecret);
    }

    /** Refresh token for a sign-in happening now. */
    public static String generateRefreshToken(UserDetails userDetails, String jwtSecret, long refreshTtlSeconds) {
        return generateRefreshToken(userDetails, jwtSecret, refreshTtlSeconds, nowSeconds());
    }

    /**
     * Refresh token whose expiry is {@code authTimeSeconds + refreshTtlSeconds}: rotating it keeps the
     * original sign-in time, so a session never lasts longer than the refresh TTL from the sign-in.
     */
    public static String generateRefreshToken(UserDetails userDetails, String jwtSecret, long refreshTtlSeconds, long authTimeSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH);
        claims.put(AUTH_TIME_CLAIM, authTimeSeconds);
        return createToken(claims, userDetails.getUsername(), (authTimeSeconds + refreshTtlSeconds) * 1000, jwtSecret);
    }

    private static String createToken(Map<String, Object> claims, String subject, long expiresAtMillis, String jwtSecret) {
        SecretKey secretKey = getSecretKey(jwtSecret);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(expiresAtMillis))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private static long nowSeconds() {
        return Instant.now().getEpochSecond();
    }

    /** Issue time in epoch seconds. */
    public static long extractIssuedAt(String token, String jwtSecret) {
        return extractClaim(token, jwtSecret, Claims::getIssuedAt).toInstant().getEpochSecond();
    }

    /** Original sign-in time in epoch seconds, or null for a token that has no such claim (issued before it existed). */
    public static Long extractAuthTime(String token, String jwtSecret) {
        Number authTime = extractClaim(token, jwtSecret, claims -> claims.get(AUTH_TIME_CLAIM, Number.class));
        return authTime == null ? null : authTime.longValue();
    }

    /**
     * True when the token was issued in an earlier second than the last password change, i.e. it belongs to a
     * session that the change must end. A null change time means no check.
     */
    public static boolean issuedBeforePasswordChange(String token, String jwtSecret, LocalDateTime lastPasswordChange) {
        if (lastPasswordChange == null) {
            return false;
        }
        long changedAt = lastPasswordChange.atZone(ZoneId.systemDefault()).toEpochSecond();
        return extractIssuedAt(token, jwtSecret) < changedAt;
    }

    public static boolean validateToken(String token, UserDetails userDetails, String jwtSecret) {
        final String username = extractUsername(token, jwtSecret);
        return (username.equals(userDetails.getUsername()) && isTokenValid(token, jwtSecret));
    }

    public static boolean validateToken(String token, String jwtSecret) {
        try {
            SecretKey secretKey = getSecretKey(jwtSecret);
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
            return isTokenValid(token, jwtSecret);
        } catch (Exception e) {
            return false;
        }
    }

    /** True only for a well-formed, unexpired token signed with the secret whose {@code typ} claim equals expectedType. */
    public static boolean validateToken(String token, String jwtSecret, String expectedType) {
        try {
            Claims claims = extractAllClaims(token, jwtSecret);
            return expectedType != null
                    && expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                    && claims.getExpiration() != null
                    && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private static SecretKey getSecretKey(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT secret is not configured");
        }
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
}