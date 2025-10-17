package io.github.membertracker.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class JwtUtils {

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

    public static String generateAccessToken(UserDetails userDetails, String jwtSecret, long accessTtlSeconds) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername(), accessTtlSeconds, jwtSecret);
    }

    public static String generateRefreshToken(UserDetails userDetails, String jwtSecret, long refreshTtlSeconds) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername(), refreshTtlSeconds, jwtSecret);
    }

    private static String createToken(Map<String, Object> claims, String subject, long ttlSeconds, String jwtSecret) {
        SecretKey secretKey = getSecretKey(jwtSecret);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + ttlSeconds * 1000))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
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

    private static SecretKey getSecretKey(String jwtSecret) {
        return Keys.hmacShaKeyFor(
            (jwtSecret != null ? jwtSecret : "defaultSecretKeyForDevelopmentOnlyChangeInProduction")
                .getBytes()
        );
    }
}